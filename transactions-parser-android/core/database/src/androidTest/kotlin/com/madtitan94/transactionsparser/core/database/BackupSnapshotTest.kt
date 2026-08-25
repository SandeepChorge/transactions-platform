package com.madtitan94.transactionsparser.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isNull
import com.madtitan94.transactionsparser.core.database.account.ActiveAccountProvider
import com.madtitan94.transactionsparser.core.database.datasource.RoomBackupDataSource
import com.madtitan94.transactionsparser.core.database.entity.CategoryEntity
import com.madtitan94.transactionsparser.core.database.entity.PayeeEntity
import com.madtitan94.transactionsparser.core.database.entity.PayeeIdentifierEntity
import com.madtitan94.transactionsparser.core.database.entity.SessionEntity
import com.madtitan94.transactionsparser.core.database.entity.TransactionEntity
import com.madtitan94.transactionsparser.core.database.entity.UploadLogEntity
import com.madtitan94.transactionsparser.core.domain.backup.BackupSnapshot
import com.madtitan94.transactionsparser.core.domain.datasource.SessionStorage
import com.madtitan94.transactionsparser.core.domain.model.UserSession
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The backup reads deliberately break the rule every other query in this file follows: they do
 * *not* filter out soft-deleted rows. That inversion is easy to "fix" by accident, and the cost of
 * doing so is silent — the backup still succeeds, and Settings › Recently deleted simply comes back
 * empty on the far side of a restore, which nobody notices until they go looking for something.
 *
 * Account scoping, by contrast, must behave exactly like everywhere else: a backup that leaked
 * another account's rows would carry them into whichever account restored the file.
 */
@RunWith(AndroidJUnit4::class)
class BackupSnapshotTest {

    private lateinit var database: TransactionsDatabase
    private lateinit var sessionStorage: FakeSessionStorage
    private lateinit var backups: RoomBackupDataSource

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
        sessionStorage.signIn(OWNER)
        backups = RoomBackupDataSource(
            database = database,
            dao = database.backupDao(),
            categories = database.categoryDao(),
            activeAccount = ActiveAccountProvider(sessionStorage)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun snapshot(): BackupSnapshot =
        (backups.snapshot() as Result.Success).data

    private suspend fun seedAccount(
        ownerId: String = OWNER,
        sessionId: Long,
        payee: String = "SWIGGY",
        categoryName: String = "Food"
    ): Long {
        val categoryId = database.categoryDao()
            .insert(CategoryEntity(ownerId = ownerId, name = categoryName))
        val payeeId = database.payeeDao()
            .insert(PayeeEntity(ownerId = ownerId, alias = payee, categoryId = categoryId))
        database.payeeIdentifierDao().insert(
            PayeeIdentifierEntity(
                ownerId = ownerId,
                payeeId = payeeId,
                rawName = payee,
                normalizedName = payee
            )
        )
        database.sessionDao().insert(
            SessionEntity(
                id = sessionId,
                ownerId = ownerId,
                fileName = "june.pdf",
                source = "PHONEPE",
                uploadedAtMillis = 0L,
                periodStartMillis = null,
                periodEndMillis = null,
                status = "COMPLETED"
            )
        )
        database.transactionDao().insertAll(
            listOf(transaction(ownerId = ownerId, sessionId = sessionId, payee = payee, payeeId = payeeId))
        )
        database.uploadLogDao().insert(
            UploadLogEntity(
                ownerId = ownerId,
                fileName = "june.pdf",
                uploadedAtMillis = 0L,
                success = true,
                source = "PHONEPE",
                failureReason = null,
                sessionId = sessionId
            )
        )
        return payeeId
    }

    private fun transaction(
        ownerId: String = OWNER,
        sessionId: Long,
        payee: String = "SWIGGY",
        payeeId: Long? = null,
        isDeleted: Boolean = false,
        isDuplicate: Boolean = false,
        duplicateOf: Long? = null
    ) = TransactionEntity(
        ownerId = ownerId,
        sessionId = sessionId,
        dateTimeUtcMillis = 1_750_000_123_000L,
        rawPayee = payee,
        normalizedPayee = payee,
        amountPaise = 25_800L,
        type = "DEBIT",
        transactionRef = "REF-$payee",
        utr = "UTR-$payee",
        payeeId = payeeId,
        isDuplicate = isDuplicate,
        duplicateOfTransactionId = duplicateOf,
        isExcluded = isDuplicate,
        isDeleted = isDeleted
    )

    @Test
    fun everyTableIsReadIntoTheSnapshot() = runTest {
        seedAccount(sessionId = SESSION_ID)

        val tables = snapshot().tables

        assertThat(tables.categories.size).isEqualTo(1)
        assertThat(tables.payees.size).isEqualTo(1)
        assertThat(tables.payeeIdentifiers.size).isEqualTo(1)
        assertThat(tables.sessions.size).isEqualTo(1)
        assertThat(tables.transactions.size).isEqualTo(1)
        assertThat(tables.uploadLogs.size).isEqualTo(1)
    }

    @Test
    fun aSoftDeletedCategoryIsBackedUpWithItsDeletionTimestamp() = runTest {
        seedAccount(sessionId = SESSION_ID)
        val doomed = database.categoryDao().insert(CategoryEntity(ownerId = OWNER, name = "Travel"))
        database.categoryDao().softDelete(OWNER, doomed, DELETED_AT)

        val categories = snapshot().tables.categories

        // Recently deleted has to survive a restore, so the deleted row and the moment it was
        // deleted both belong in the file.
        assertThat(categories.map { it.name }).containsExactly("Food", "Travel")
        val deleted = categories.single { it.isDeleted }
        assertThat(deleted.deletedAtMillis).isEqualTo(DELETED_AT)
    }

    @Test
    fun aSoftDeletedTransactionIsBackedUpEvenThoughTheExportDropsIt() = runTest {
        seedAccount(sessionId = SESSION_ID)
        database.transactionDao().insertAll(
            listOf(transaction(sessionId = SESSION_ID, payee = "REMOVED", isDeleted = true))
        )

        val backed = snapshot().tables.transactions.map { it.rawPayee }
        val exported = database.transactionDao().exportRows(OWNER).map { it.rawPayee }

        // The two reads disagreeing is the point: an export is a report, a backup is a copy.
        assertThat(backed).containsExactly("SWIGGY", "REMOVED")
        assertThat(exported).containsExactly("SWIGGY")
    }

    @Test
    fun anotherAccountsRowsNeverReachTheBackup() = runTest {
        seedAccount(sessionId = SESSION_ID, payee = "MINE", categoryName = "Food")
        seedAccount(
            ownerId = OTHER_OWNER,
            sessionId = OTHER_SESSION_ID,
            payee = "THEIRS",
            categoryName = "Theirs"
        )

        val tables = snapshot().tables

        assertThat(tables.transactions.map { it.rawPayee }).containsExactly("MINE")
        assertThat(tables.categories.map { it.name }).containsExactly("Food")
        assertThat(tables.payees.map { it.alias }).containsExactly("MINE")
        assertThat(tables.payeeIdentifiers.map { it.normalizedName }).containsExactly("MINE")
        assertThat(tables.sessions.map { it.id }).containsExactly(SESSION_ID)
        assertThat(tables.uploadLogs.size).isEqualTo(1)
    }

    @Test
    fun theDuplicateBackLinkSurvivesTheRead() = runTest {
        seedAccount(sessionId = SESSION_ID)
        val original = snapshot().tables.transactions.single().id
        database.transactionDao().insertAll(
            listOf(
                transaction(
                    sessionId = SESSION_ID,
                    payee = "REPEAT",
                    isDuplicate = true,
                    duplicateOf = original
                )
            )
        )

        val repeat = snapshot().tables.transactions.single { it.isDuplicate }

        assertThat(repeat.duplicateOfTransactionId).isEqualTo(original)
        assertThat(repeat.isExcluded).isEqualTo(true)
    }

    @Test
    fun anAccountWithNoRowsBacksUpEmptyRatherThanFailing() = runTest {
        val tables = snapshot().tables

        assertThat(tables.categories).isEmpty()
        assertThat(tables.transactions).isEmpty()
    }

    @Test
    fun theSnapshotReportsTheSchemaVersionTheRowsCameFrom() = runTest {
        // Compared against the open database rather than a literal, so this keeps passing across
        // schema bumps; the second assertion is what stops it passing on a default of zero.
        assertThat(snapshot().schemaVersion)
            .isEqualTo(database.openHelper.readableDatabase.version)
        assertThat(snapshot().schemaVersion).isGreaterThanOrEqualTo(4)
    }

    @Test
    fun rowsComeBackInIdOrderSoTwoBackupsOfTheSameDataMatch() = runTest {
        seedAccount(sessionId = SESSION_ID)
        database.transactionDao().insertAll(
            listOf(
                transaction(sessionId = SESSION_ID, payee = "SECOND"),
                transaction(sessionId = SESSION_ID, payee = "THIRD")
            )
        )

        val ids = snapshot().tables.transactions.map { it.id }

        assertThat(ids).isEqualTo(ids.sorted())
    }

    @Test
    fun signingOutBacksUpNothingRatherThanEveryAccountsRows() = runTest {
        seedAccount(sessionId = SESSION_ID)
        sessionStorage.clear()

        val tables = snapshot().tables

        // NO_OWNER_ID owns no rows, so the scoping that protects a signed-in account also stops a
        // signed-out one from reading the whole database.
        assertThat(tables.transactions).isEmpty()
        assertThat(tables.categories).isEmpty()
    }

    @Test
    fun anUnmappedTransactionKeepsItsNullPayee() = runTest {
        database.sessionDao().insert(
            SessionEntity(
                id = SESSION_ID,
                ownerId = OWNER,
                fileName = "june.pdf",
                source = "PHONEPE",
                uploadedAtMillis = 0L,
                periodStartMillis = null,
                periodEndMillis = null,
                status = "PENDING"
            )
        )
        database.transactionDao().insertAll(
            listOf(transaction(sessionId = SESSION_ID, payee = "UNKNOWN SHOP", payeeId = null))
        )

        val row = snapshot().tables.transactions.single()

        assertThat(row.payeeId).isNull()
        assertThat(row.rawPayee).isEqualTo("UNKNOWN SHOP")
    }

    private companion object {
        const val OWNER = "google-sub-a"
        const val OTHER_OWNER = "google-sub-b"
        const val SESSION_ID = 1L
        const val OTHER_SESSION_ID = 2L
        const val DELETED_AT = 1_700_000_000_000L
    }
}
