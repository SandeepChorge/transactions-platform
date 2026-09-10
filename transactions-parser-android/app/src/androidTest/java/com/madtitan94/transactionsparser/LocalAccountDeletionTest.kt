package com.madtitan94.transactionsparser

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import java.util.UUID
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.*
import com.madtitan94.transactionsparser.core.database.TransactionsDatabase
import com.madtitan94.transactionsparser.core.database.datasource.RoomLocalDataCleaner
import com.madtitan94.transactionsparser.core.database.entity.*
import com.madtitan94.transactionsparser.core.domain.model.UserSession
import com.madtitan94.transactionsparser.core.domain.util.Result
import com.madtitan94.transactionsparser.feature.auth.data.DataStoreSessionStorage
import com.madtitan94.transactionsparser.feature.dashboard.data.DataStoreDashboardPreferences
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardRange
import com.madtitan94.transactionsparser.feature.profile.data.DataStoreProfileStorage
import com.madtitan94.transactionsparser.feature.profile.domain.Gender
import com.madtitan94.transactionsparser.feature.profile.domain.UserProfile
import com.madtitan94.transactionsparser.feature.settings.domain.ThemePreference
import com.madtitan94.transactionsparser.feature.settings.domain.DeleteAccountService
import com.madtitan94.transactionsparser.feature.settings.data.CacheLocalDataCleaner
import com.madtitan94.transactionsparser.feature.settings.data.DataStoreThemeStorage
import com.madtitan94.transactionsparser.feature.settings.data.LocalDeleteAccountService
import org.koin.core.context.GlobalContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalAccountDeletionTest {
    @Test
    fun deletionServiceResolvesFromTheProductionGraph() {
        // Resolve only: never run deletion against the installed app's data or Google account.
        assertThat(GlobalContext.get().get<DeleteAccountService>()).isInstanceOf(LocalDeleteAccountService::class)
    }

    @Test
    fun clearsAllAccountsAndLiveStoresBeforeSigningOutAndAllowsFreshUse() = runBlocking<Unit> {
        // Instrumentation runs under the target UID. Restrict every path to a unique test
        // directory; the cache cleaner must not touch the user's app caches either.
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val testDirectory = File(target.cacheDir, "deletion-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = File(testDirectory, "files").apply { mkdirs() }
            override fun getCacheDir(): File = File(testDirectory, "cache").apply { mkdirs() }
            override fun getExternalCacheDirs(): Array<File> = emptyArray()
        }
        val database = Room.inMemoryDatabaseBuilder(context, TransactionsDatabase::class.java).build()
        // Each store is explicitly injected: preferencesDataStore delegates cache the first
        // instance process-wide, so changing Context alone would reuse the production store.
        val directory = File(context.filesDir, "deletion-test-${UUID.randomUUID()}").apply { mkdirs() }
        val storageJob = SupervisorJob()
        val storageScope = CoroutineScope(storageJob + Dispatchers.IO)
        fun store(name: String) = PreferenceDataStoreFactory.create(scope = storageScope) {
            File(directory, "$name.preferences_pb")
        }
        val sessions = DataStoreSessionStorage(context, store("session"))
        val profile = DataStoreProfileStorage(context, store("profile"))
        val dashboards = DataStoreDashboardPreferences(context, sessions, store("dashboards"))
        val theme = DataStoreThemeStorage(context, store("theme"))
        try {
            for (owner in listOf("owner-a", "owner-b")) {
                sessions.save(UserSession(owner, "Test", "$owner@example.com", null))
                dashboards.setRange(DashboardRange.Today)
                val category = database.categoryDao().insert(CategoryEntity(ownerId = owner, name = "Food", isDeleted = true))
                val payee = database.payeeDao().insert(PayeeEntity(ownerId = owner, alias = "Shop", categoryId = category))
                database.payeeIdentifierDao().insert(PayeeIdentifierEntity(ownerId = owner, payeeId = payee, rawName = "SHOP", normalizedName = "SHOP"))
                val session = database.sessionDao().insert(SessionEntity(ownerId = owner, fileName = "test.pdf", source = "PHONEPE", uploadedAtMillis = 0, periodStartMillis = null, periodEndMillis = null, status = "COMPLETED"))
                database.transactionDao().insertAll(listOf(TransactionEntity(ownerId = owner, sessionId = session, dateTimeUtcMillis = 0, rawPayee = "SHOP", normalizedPayee = "SHOP", amountPaise = 100, type = "DEBIT", transactionRef = null, utr = null, payeeId = payee, isDeleted = true)))
                database.uploadLogDao().insert(UploadLogEntity(ownerId = owner, fileName = "test.pdf", uploadedAtMillis = 0, success = true, source = "PHONEPE", failureReason = null, sessionId = session))
            }
            profile.save(UserProfile("Private profile", "9876543210", Gender.OTHER))
            theme.setTheme(ThemePreference.DARK)
            val cache = File(context.cacheDir, "nested/statement.pdf").apply { parentFile!!.mkdirs(); writeText("private statement") }
            val service = LocalDeleteAccountService(listOf(RoomLocalDataCleaner(database), profile, dashboards, theme, CacheLocalDataCleaner(context)), sessions)

            assertThat(service.deleteAccount()).isInstanceOf(Result.Success::class)
            assertThat(sessions.observeSession().first()).isNull()
            assertThat(profile.observeProfile().first()).isNull()
            assertThat(theme.observeTheme().first()).isEqualTo(ThemePreference.SYSTEM)
            assertThat(cache.exists()).isFalse()
            withContext(Dispatchers.IO) {
                for (table in listOf("categories", "payees", "payee_identifiers", "sessions", "transactions", "upload_logs")) {
                    database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
                        it.moveToFirst()
                        assertThat(it.getInt(0)).isEqualTo(0)
                    }
                }
            }
            // Existing singleton DataStores must remain usable and not resurrect either account.
            for (owner in listOf("owner-a", "owner-b")) {
                sessions.save(UserSession(owner, "Fresh", "$owner@example.com", null))
                assertThat(dashboards.observeRange().first()).isEqualTo(DashboardRange.Default)
                assertThat(profile.observeProfile().first()).isNull()
            }
            profile.save(UserProfile("New profile", "", null))
            theme.setTheme(ThemePreference.LIGHT)
            assertThat(profile.observeProfile().first()?.name).isEqualTo("New profile")
            assertThat(theme.observeTheme().first()).isEqualTo(ThemePreference.LIGHT)
            assertThat(service.deleteAccount()).isInstanceOf(Result.Success::class)
        } finally {
            database.close()
            sessions.clear()
            profile.clearLocalData()
            dashboards.clearLocalData()
            theme.clearLocalData()
            storageJob.cancelAndJoin()
            testDirectory.deleteRecursively()
        }
    }
}
