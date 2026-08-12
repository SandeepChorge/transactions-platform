package com.madtitan94.transactionsparser.core.database.account

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.madtitan94.transactionsparser.core.database.TransactionsDatabase
import com.madtitan94.transactionsparser.core.database.datasource.RoomCategoryDataSource
import com.madtitan94.transactionsparser.core.database.migration.LEGACY_OWNER_ID
import com.madtitan94.transactionsparser.core.domain.datasource.SessionStorage
import com.madtitan94.transactionsparser.core.domain.model.UserSession
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountIsolationTest {

    private lateinit var database: TransactionsDatabase
    private lateinit var sessionStorage: FakeSessionStorage
    private lateinit var categories: RoomCategoryDataSource

    private class FakeSessionStorage : SessionStorage {
        val session = MutableStateFlow<UserSession?>(null)

        fun signIn(googleId: String) {
            session.value = UserSession(googleId, "Test", "test@example.com", null)
        }

        override fun observeSession(): Flow<UserSession?> = session

        override suspend fun save(session: UserSession): EmptyResult<DataError.Local> {
            this.session.value = session
            return Result.Success(Unit)
        }

        override suspend fun clear(): EmptyResult<DataError.Local> {
            session.value = null
            return Result.Success(Unit)
        }
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            TransactionsDatabase::class.java
        ).build()
        sessionStorage = FakeSessionStorage()
        categories = RoomCategoryDataSource(
            dao = database.categoryDao(),
            activeAccount = ActiveAccountProvider(sessionStorage),
            nowMillis = { FIXED_NOW }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun oneAccountNeverSeesAnotherAccountsCategories() = runTest {
        sessionStorage.signIn("google-sub-a")
        categories.insert("Grocery")

        sessionStorage.signIn("google-sub-b")
        assertThat(categories.observeAll().first()).isEmpty()

        categories.insert("Rent")
        assertThat(categories.observeAll().first().map { it.name }).containsExactly("Rent")

        sessionStorage.signIn("google-sub-a")
        assertThat(categories.observeAll().first().map { it.name }).containsExactly("Grocery")
    }

    @Test
    fun signedOutReadsSeeNothingRatherThanFallingBackToAnotherAccount() = runTest {
        sessionStorage.signIn("google-sub-a")
        categories.insert("Grocery")

        sessionStorage.clear()

        assertThat(categories.observeAll().first()).isEmpty()
    }

    @Test
    fun deletedCategoryDisappearsFromReadsButStaysRecoverable() = runTest {
        sessionStorage.signIn("google-sub-a")
        val id = (categories.insert("Grocery") as Result.Success).data

        categories.delete(id)

        assertThat(categories.observeAll().first()).isEmpty()
        assertThat(categories.observeDeleted().first().map { it.name }).containsExactly("Grocery")

        categories.restore(id)

        assertThat(categories.observeAll().first().map { it.name }).containsExactly("Grocery")
        assertThat(categories.observeDeleted().first()).isEmpty()
    }

    @Test
    fun claimingLegacyDataHandsItToTheFirstAccountAndIsIdempotent() = runTest {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO categories (ownerId, name, isDeleted) VALUES('$LEGACY_OWNER_ID','Grocery',0)"
        )

        sessionStorage.signIn("google-sub-a")
        assertThat(categories.observeAll().first()).isEmpty()

        val claimer = LegacyDataClaimer(database, database.legacyOwnershipDao())
        claimer.claimFor("google-sub-a")

        assertThat(categories.observeAll().first().map { it.name }).containsExactly("Grocery")

        // A second account signing in later must not inherit data already claimed.
        claimer.claimFor("google-sub-b")
        sessionStorage.signIn("google-sub-b")
        assertThat(categories.observeAll().first()).isEmpty()
    }

    @Test
    fun claimIsARejectedNoOpWhileSignedOut() = runTest {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO categories (ownerId, name, isDeleted) VALUES('$LEGACY_OWNER_ID','Grocery',0)"
        )

        LegacyDataClaimer(database, database.legacyOwnershipDao()).claimFor(NO_OWNER_ID)

        database.openHelper.readableDatabase
            .query("SELECT ownerId FROM categories")
            .use { cursor ->
                cursor.moveToFirst()
                assertThat(cursor.getString(0)).isEqualTo(LEGACY_OWNER_ID)
            }
    }

    private companion object {
        const val FIXED_NOW = 1_700_000_000_000L
    }
}
