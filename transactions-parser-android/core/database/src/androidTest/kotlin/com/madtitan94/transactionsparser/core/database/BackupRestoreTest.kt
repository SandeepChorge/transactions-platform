package com.madtitan94.transactionsparser.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.madtitan94.transactionsparser.core.database.account.ActiveAccountProvider
import com.madtitan94.transactionsparser.core.database.datasource.RoomBackupDataSource
import com.madtitan94.transactionsparser.core.database.datasource.RoomTransactionDataSource
import com.madtitan94.transactionsparser.core.database.entity.CategoryEntity
import com.madtitan94.transactionsparser.core.database.entity.PayeeEntity
import com.madtitan94.transactionsparser.core.database.entity.PayeeIdentifierEntity
import com.madtitan94.transactionsparser.core.database.entity.SessionEntity
import com.madtitan94.transactionsparser.core.database.entity.TransactionEntity
import com.madtitan94.transactionsparser.core.domain.backup.BACKUP_FORMAT_VERSION
import com.madtitan94.transactionsparser.core.domain.backup.BackupAccount
import com.madtitan94.transactionsparser.core.domain.backup.BackupApp
import com.madtitan94.transactionsparser.core.domain.backup.BackupCategory
import com.madtitan94.transactionsparser.core.domain.backup.BackupFile
import com.madtitan94.transactionsparser.core.domain.backup.BackupPayee
import com.madtitan94.transactionsparser.core.domain.backup.BackupPayeeIdentifier
import com.madtitan94.transactionsparser.core.domain.backup.BackupSession
import com.madtitan94.transactionsparser.core.domain.backup.BackupTransaction
import com.madtitan94.transactionsparser.core.domain.backup.BackupUploadLog
import com.madtitan94.transactionsparser.core.domain.backup.RestoreBackupUseCase
import com.madtitan94.transactionsparser.core.domain.backup.RestoreReport
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
 * Restoring a backup into a database that already has data in it.
 *
 * The unit tests cover what a restore *decides*; this covers what actually lands — the id remapping
 * every reference depends on, the unique indexes that would otherwise fail the insert, and the
 * account scoping that stops a restore from writing rows nobody can reach. All of it against real
 * Room, because none of these are things a fake would get wrong in the same way.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {

    private lateinit var database: TransactionsDatabase
    private lateinit var sessionStorage: FakeSessionStorage
    private lateinit var backups: RoomBackupDataSource
    private lateinit var restore: RestoreBackupUseCase

    private class FakeSessionStorage : SessionStorage {
        val session = MutableStateFlow<UserSession?>(null)

        fun signIn(googleId: String) {
            session.value = UserSession(googleId, "Test", "$googleId@example.com", null)
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
        val activeAccount = ActiveAccountProvider(sessionStorage)
        backups = RoomBackupDataSource(
            database = database,
            dao = database.backupDao(),
            categories = database.categoryDao(),
            activeAccount = activeAccount
        )
        restore = RestoreBackupUseCase(
            backups = backups,
            transactions = RoomTransactionDataSource(database.transactionDao(), activeAccount)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun restored(file: BackupFile = backupFile()): RestoreReport =
        (restore(file) as Result.Success).data

    // --- the file being restored -------------------------------------------------------------
    //
    // Ids are deliberately large and unrelated to anything the database would assign, so a row that
    // kept one of them stands out rather than accidentally landing on a plausible value.

    private fun backupFile(
        categories: List<BackupCategory> = listOf(
            BackupCategory(id = 700L, name = "Food", isDeleted = false, deletedAtMillis = null)
        ),
        payees: List<BackupPayee> = listOf(
            BackupPayee(id = 800L, alias = "Corner Cafe", categoryId = 700L, isDeleted = false, deletedAtMillis = null)
        ),
        payeeIdentifiers: List<BackupPayeeIdentifier> = listOf(
            BackupPayeeIdentifier(id = 900L, payeeId = 800L, rawName = "CORNER CAFE", normalizedName = "CORNER CAFE")
        ),
        sessions: List<BackupSession> = listOf(fileSession()),
        transactions: List<BackupTransaction> = listOf(
            fileTransaction(id = 1_000L, ref = "REF-A"),
            fileTransaction(id = 1_001L, ref = "REF-B")
        ),
        uploadLogs: List<BackupUploadLog> = listOf(
            BackupUploadLog(
                id = 1_100L,
                fileName = "june.pdf",
                uploadedAtMillis = 1_750_000_000_000L,
                success = true,
                source = "PHONEPE",
                failureReason = null,
                sessionId = 850L,
                isDeleted = false,
                deletedAtMillis = null
            )
        ),
        account: BackupAccount? = BackupAccount("elsewhere@example.com", OTHER_OWNER)
    ) = BackupFile(
        formatVersion = BACKUP_FORMAT_VERSION,
        schemaVersion = 4,
        exportedAtMillis = 1_756_000_000_000L,
        app = BackupApp("1.0.33", 33),
        account = account,
        categories = categories,
        payees = payees,
        payeeIdentifiers = payeeIdentifiers,
        sessions = sessions,
        transactions = transactions,
        uploadLogs = uploadLogs
    )

    private fun fileSession(id: Long = 850L) = BackupSession(
        id = id,
        fileName = "june.pdf",
        source = "PHONEPE",
        uploadedAtMillis = 1_750_000_000_000L,
        periodStartMillis = null,
        periodEndMillis = null,
        status = "COMPLETED",
        isDeleted = false,
        deletedAtMillis = null
    )

    private fun fileTransaction(
        id: Long,
        ref: String?,
        payee: String = "CORNER CAFE",
        payeeId: Long? = 800L,
        amountPaise: Long = 25_800L,
        atMillis: Long = 1_750_000_123_000L,
        isDuplicate: Boolean = false,
        duplicateOf: Long? = null,
        isExcluded: Boolean = false,
        isDeleted: Boolean = false
    ) = BackupTransaction(
        id = id,
        sessionId = 850L,
        dateTimeUtcMillis = atMillis,
        rawPayee = payee,
        normalizedPayee = payee,
        amountPaise = amountPaise,
        type = "DEBIT",
        transactionRef = ref,
        utr = null,
        payeeId = payeeId,
        isDuplicate = isDuplicate,
        duplicateOfTransactionId = duplicateOf,
        isExcluded = isExcluded,
        isDeleted = isDeleted,
        deletedAtMillis = null
    )

    // --- what is already on the device -------------------------------------------------------

    private suspend fun seedLocal(
        ownerId: String = OWNER,
        categoryName: String = "Travel",
        payeeAlias: String = "Metro",
        normalizedName: String = "METRO",
        ref: String? = "REF-LOCAL"
    ): Long {
        val categoryId = database.categoryDao()
            .insert(CategoryEntity(ownerId = ownerId, name = categoryName))
        val payeeId = database.payeeDao()
            .insert(PayeeEntity(ownerId = ownerId, alias = payeeAlias, categoryId = categoryId))
        database.payeeIdentifierDao().insert(
            PayeeIdentifierEntity(
                ownerId = ownerId,
                payeeId = payeeId,
                rawName = normalizedName,
                normalizedName = normalizedName
            )
        )
        val sessionId = database.sessionDao().insert(
            SessionEntity(
                ownerId = ownerId,
                fileName = "may.pdf",
                source = "PHONEPE",
                uploadedAtMillis = 0L,
                periodStartMillis = null,
                periodEndMillis = null,
                status = "COMPLETED"
            )
        )
        database.transactionDao().insertAll(
            listOf(
                TransactionEntity(
                    ownerId = ownerId,
                    sessionId = sessionId,
                    dateTimeUtcMillis = 1_750_000_123_000L,
                    rawPayee = normalizedName,
                    normalizedPayee = normalizedName,
                    amountPaise = 25_800L,
                    type = "DEBIT",
                    transactionRef = ref,
                    utr = null,
                    payeeId = payeeId
                )
            )
        )
        return payeeId
    }

    private suspend fun localTransactions() = database.backupDao().transactions(OWNER)

    // --- tests ---------------------------------------------------------------------------------

    @Test
    fun everyTableIsWrittenIntoAnEmptyAccount() = runTest {
        val report = restored()

        assertThat(database.backupDao().categories(OWNER).size).isEqualTo(1)
        assertThat(database.backupDao().payees(OWNER).size).isEqualTo(1)
        assertThat(database.backupDao().payeeIdentifiers(OWNER).size).isEqualTo(1)
        assertThat(database.backupDao().sessions(OWNER).size).isEqualTo(1)
        assertThat(localTransactions().size).isEqualTo(2)
        assertThat(database.backupDao().uploadLogs(OWNER).size).isEqualTo(1)
        assertThat(report.transactions).isEqualTo(2)
        assertThat(report.duplicatesFlagged).isEqualTo(0)
    }

    @Test
    fun idsAreRemappedRatherThanTakenFromTheFile() = runTest {
        restored()

        val category = database.backupDao().categories(OWNER).single()
        val payee = database.backupDao().payees(OWNER).single()
        val session = database.backupDao().sessions(OWNER).single()

        // The file's ids belong to another database. What matters is not which ids these got, but
        // that every reference still resolves to the right row after they changed.
        assertThat(payee.categoryId).isEqualTo(category.id)
        assertThat(database.backupDao().payeeIdentifiers(OWNER).single().payeeId).isEqualTo(payee.id)
        assertThat(localTransactions().map { it.sessionId }).containsExactly(session.id, session.id)
        assertThat(localTransactions().map { it.payeeId }).containsExactly(payee.id, payee.id)
        assertThat(database.backupDao().uploadLogs(OWNER).single().sessionId).isEqualTo(session.id)
    }

    @Test
    fun aDuplicateBackLinkIsRebuiltAgainstTheRowsNewId() = runTest {
        restored(
            backupFile(
                transactions = listOf(
                    fileTransaction(id = 1_000L, ref = "REF-A"),
                    fileTransaction(
                        id = 1_001L,
                        ref = null,
                        isDuplicate = true,
                        duplicateOf = 1_000L,
                        isExcluded = true
                    )
                )
            )
        )

        val rows = localTransactions()
        val original = rows.single { !it.isDuplicate }
        val repeat = rows.single { it.isDuplicate }

        // Written in a second pass, because at insert time the row it names has no id yet.
        assertThat(repeat.duplicateOfTransactionId).isEqualTo(original.id)
        assertThat(repeat.isExcluded).isTrue()
    }

    @Test
    fun aCategoryTheAccountAlreadyHasIsReusedRatherThanDuplicated() = runTest {
        seedLocal(categoryName = "Food")

        val report = restored()

        // The unique index on (ownerId, name) would have failed the insert; reusing is also what
        // the user means by restoring a backup onto an account that already has categories.
        assertThat(database.backupDao().categories(OWNER).map { it.name }).containsExactly("Food")
        assertThat(report.categoriesReused).isEqualTo(1)
        assertThat(report.categoriesInserted).isEqualTo(0)
        val category = database.backupDao().categories(OWNER).single()
        assertThat(database.backupDao().payees(OWNER).map { it.categoryId })
            .containsExactlyInAnyOrder(category.id, category.id)
    }

    @Test
    fun categoriesAreMatchedWithoutRegardToCase() = runTest {
        seedLocal(categoryName = "food")

        val report = restored()

        // The app orders and shows names case-insensitively, so "Food" beside "food" would read as
        // a bug rather than as two categories.
        assertThat(database.backupDao().categories(OWNER).size).isEqualTo(1)
        assertThat(report.categoriesReused).isEqualTo(1)
    }

    @Test
    fun aCategoryInRecentlyDeletedComesBackWhenTheBackupHasItInUse() = runTest {
        val id = database.categoryDao().insert(CategoryEntity(ownerId = OWNER, name = "Food"))
        database.categoryDao().softDelete(OWNER, id, DELETED_AT)

        restored()

        // Otherwise the payees restored under it would hang off a row the user already threw away.
        val category = database.backupDao().categories(OWNER).single()
        assertThat(category.isDeleted).isFalse()
        assertThat(category.deletedAtMillis).isNull()
    }

    @Test
    fun aStatementNameAlreadyPointingAtAnotherPayeeIsLeftAloneAndReported() = runTest {
        seedLocal(payeeAlias = "Cafe Nearby", normalizedName = "CORNER CAFE")

        val report = restored()

        // One account can only read a statement name one way. The local mapping is the one the
        // user has been using, so it stands — and the disagreement is reported, not applied.
        val identifiers = database.backupDao().payeeIdentifiers(OWNER)
        assertThat(identifiers.size).isEqualTo(1)
        val localPayee = database.backupDao().payees(OWNER).single { it.alias == "Cafe Nearby" }
        assertThat(identifiers.single().payeeId).isEqualTo(localPayee.id)

        assertThat(report.identifierConflicts.size).isEqualTo(1)
        val conflict = report.identifierConflicts.single()
        assertThat(conflict.normalizedName).isEqualTo("CORNER CAFE")
        assertThat(conflict.keptPayeeAlias).isEqualTo("Cafe Nearby")
        assertThat(conflict.filePayeeAlias).isEqualTo("Corner Cafe")
    }

    @Test
    fun restoredRowsBelongToTheSignedInAccountAndNotToTheOneThatExportedThem() = runTest {
        restored()

        // The file names another account in its envelope, and the format carries no owner field at
        // all — so this is the only answer the writer can give.
        assertThat(localTransactions().size).isEqualTo(2)
        assertThat(database.backupDao().transactions(OTHER_OWNER)).isEmpty()
        assertThat(database.backupDao().categories(OTHER_OWNER)).isEmpty()
        assertThat(database.backupDao().payees(OTHER_OWNER)).isEmpty()
    }

    @Test
    fun anotherAccountsRowsAreNeitherReusedNorDisturbed() = runTest {
        seedLocal(ownerId = OTHER_OWNER, categoryName = "Food", normalizedName = "CORNER CAFE")

        val report = restored()

        // Reusing them would put this account's payees under a category it cannot see.
        assertThat(report.categoriesReused).isEqualTo(0)
        assertThat(report.identifierConflicts).isEmpty()
        assertThat(database.backupDao().categories(OWNER).size).isEqualTo(1)
        assertThat(database.backupDao().categories(OTHER_OWNER).size).isEqualTo(1)
        assertThat(database.backupDao().transactions(OTHER_OWNER).size).isEqualTo(1)
    }

    @Test
    fun aSoftDeletedRowSurvivesTheRestoreStillDeleted() = runTest {
        restored(
            backupFile(
                categories = listOf(
                    BackupCategory(id = 700L, name = "Food", isDeleted = false, deletedAtMillis = null),
                    BackupCategory(id = 701L, name = "Travel", isDeleted = true, deletedAtMillis = DELETED_AT)
                )
            )
        )

        // Settings › Recently deleted has to look the same on the far side of a restore.
        val deleted = database.backupDao().categories(OWNER).single { it.isDeleted }
        assertThat(deleted.name).isEqualTo("Travel")
        assertThat(deleted.deletedAtMillis).isEqualTo(DELETED_AT)
    }

    @Test
    fun restoringTheSameBackupTwiceFlagsTheSecondCopyRatherThanDoublingTheTotals() = runTest {
        restored()

        val report = restored()

        assertThat(report.duplicatesFlagged).isEqualTo(2)
        val rows = localTransactions()
        assertThat(rows.size).isEqualTo(4)
        // Kept and flagged rather than dropped, and excluded so the totals are right immediately —
        // exactly what re-uploading an overlapping statement does.
        assertThat(rows.count { it.isDuplicate }).isEqualTo(2)
        assertThat(rows.count { it.isExcluded }).isEqualTo(2)
        rows.filter { it.isDuplicate }.forEach {
            assertThat(it.duplicateOfTransactionId).isNotNull()
        }
    }

    @Test
    fun aTransactionTheAccountAlreadyHasIsPointedAtTheRowItRepeats() = runTest {
        seedLocal(normalizedName = "CORNER CAFE", ref = "REF-A")
        val existing = localTransactions().single()

        restored()

        val repeat = localTransactions().single { it.isDuplicate }
        assertThat(repeat.duplicateOfTransactionId).isEqualTo(existing.id)
        assertThat(repeat.transactionRef).isEqualTo("REF-A")
    }

    @Test
    fun aFailureLeavesTheAccountExactlyAsItWas() = runTest {
        seedLocal()
        val before = localTransactions().size

        // A payee the file's transactions point at but that the file does not contain. Validation
        // refuses this long before it reaches the writer; the point here is what the writer does if
        // one ever gets through — everything or nothing, never half.
        val result = restore(
            backupFile(
                payees = emptyList(),
                payeeIdentifiers = emptyList(),
                transactions = listOf(fileTransaction(id = 1_000L, ref = "REF-A", payeeId = 800L))
            )
        )

        assertThat(result).isInstanceOf(Result.Error::class)
        assertThat(localTransactions().size).isEqualTo(before)
        assertThat(database.backupDao().categories(OWNER).map { it.name }).containsExactly("Travel")
        assertThat(database.backupDao().sessions(OWNER).size).isEqualTo(1)
    }

    @Test
    fun anEmptyBackupRestoresWithoutFailing() = runTest {
        val report = restored(
            backupFile(
                categories = emptyList(),
                payees = emptyList(),
                payeeIdentifiers = emptyList(),
                sessions = emptyList(),
                transactions = emptyList(),
                uploadLogs = emptyList()
            )
        )

        assertThat(report.transactions).isEqualTo(0)
        assertThat(localTransactions()).isEmpty()
    }

    @Test
    fun restoringWhileSignedOutWritesNothingReachable() = runTest {
        sessionStorage.clear()

        restored()

        // NO_OWNER_ID owns no rows, so nothing written here is visible to any account — which is
        // why the rows the signed-in owner can see stay at zero.
        assertThat(database.backupDao().transactions(OWNER)).isEmpty()
        assertThat(database.backupDao().categories(OWNER)).isEmpty()
    }

    private companion object {
        const val OWNER = "google-sub-a"
        const val OTHER_OWNER = "google-sub-b"
        const val DELETED_AT = 1_700_000_000_000L
    }
}
