package com.madtitan94.transactionsparser.core.domain.backup

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.madtitan94.transactionsparser.core.domain.model.TransactionKey
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * What a restore decides before the database sees any of it.
 *
 * The decisions are all about overlap: a backup restored onto an account that already holds some of
 * the same statements must not double the totals, and the user's own "count this anyway" overrides
 * have to survive the trip.
 */
class RestoreBackupUseCaseTest {

    private fun useCase(
        backups: RecordingBackupDataSource = RecordingBackupDataSource(),
        transactions: FakeDuplicateKeySource = FakeDuplicateKeySource()
    ) = RestoreBackupUseCase(backups = backups, transactions = transactions)

    private fun localKey(
        id: Long = 500L,
        ref: String? = "REF1",
        utr: String? = "UTR1",
        payee: String = "CORNER CAFE",
        amountPaise: Long = 25_800L,
        atMillis: Long = 1_750_000_123_000L
    ) = TransactionKey(
        id = id,
        transactionRef = ref,
        utr = utr,
        normalizedPayee = payee,
        amountPaise = amountPaise,
        dateTimeUtcMillis = atMillis
    )

    @Test
    fun `hands every table to the writer`() = runTest {
        val backups = RecordingBackupDataSource()

        useCase(backups = backups)(backupFile())

        val payload = backups.restored!!
        assertThat(payload.categories).hasSize(2)
        assertThat(payload.payees).hasSize(1)
        assertThat(payload.payeeIdentifiers).hasSize(2)
        assertThat(payload.sessions).hasSize(1)
        assertThat(payload.transactions).hasSize(2)
        assertThat(payload.uploadLogs).hasSize(1)
    }

    @Test
    fun `keeps the file's ids so the writer can rebuild the references`() = runTest {
        val backups = RecordingBackupDataSource()

        useCase(backups = backups)(backupFile())

        // The ids mean nothing in this database, but every link between the tables is expressed in
        // them — throwing them away here would leave the writer nothing to remap.
        assertThat(backups.restored!!.transactions.first().source.id).isEqualTo(10_000L)
        assertThat(backups.restored!!.transactions.last().source.duplicateOfTransactionId)
            .isEqualTo(10_000L)
    }

    @Test
    fun `restoring into an empty account flags nothing`() = runTest {
        val backups = RecordingBackupDataSource()

        useCase(backups = backups)(backupFile())

        val fresh = backups.restored!!.transactions.first()
        assertThat(fresh.isDuplicate).isFalse()
        assertThat(fresh.isExcluded).isFalse()
        assertThat(fresh.duplicateOfLocalId).isNull()
    }

    @Test
    fun `flags a transaction this account already has and points it at the local row`() = runTest {
        val backups = RecordingBackupDataSource()
        val transactions = FakeDuplicateKeySource(Result.Success(listOf(localKey(id = 500L))))

        useCase(backups = backups, transactions = transactions)(backupFile())

        val repeat = backups.restored!!.transactions.first()
        assertThat(repeat.isDuplicate).isTrue()
        // Excluded as well, so the account's totals are right the moment the restore finishes.
        assertThat(repeat.isExcluded).isTrue()
        assertThat(repeat.duplicateOfLocalId).isEqualTo(500L)
    }

    @Test
    fun `compares on payment identity, never on the ids in the file`() = runTest {
        val transactions = FakeDuplicateKeySource()

        useCase(transactions = transactions)(backupFile())

        // A candidate carrying the file's id could match a local row that merely shares the number.
        assertThat(transactions.lastCandidates!!.map { it.id }).isEqualTo(listOf(0L, 0L))
        assertThat(transactions.lastCandidates!!.first().transactionRef).isEqualTo("REF1")
    }

    @Test
    fun `keeps a duplicate the file already recorded`() = runTest {
        val backups = RecordingBackupDataSource()

        useCase(backups = backups)(backupFile())

        // Detection here only sees what this database holds; the file's own flag records a repeat
        // found when the row was first imported, which is still true.
        val fromFile = backups.restored!!.transactions.last()
        assertThat(fromFile.isDuplicate).isTrue()
        assertThat(fromFile.isExcluded).isTrue()
    }

    @Test
    fun `keeps a row the user chose to count`() = runTest {
        val backups = RecordingBackupDataSource()
        val included = backupFile(
            tables = sampleTables(
                transactions = sampleTables().transactions.map {
                    it.copy(isDuplicate = true, isExcluded = false)
                }
            )
        )

        useCase(backups = backups)(included)

        // isExcluded is the user's decision, not a system fact, so a restore into an account that
        // does not have these rows must not quietly re-exclude them.
        assertThat(backups.restored!!.transactions.all { !it.isExcluded }).isTrue()
    }

    @Test
    fun `an exclusion the user removed comes back when this account already has the row`() = runTest {
        val backups = RecordingBackupDataSource()
        val transactions = FakeDuplicateKeySource(Result.Success(listOf(localKey())))
        val included = backupFile(
            tables = sampleTables(
                transactions = listOf(sampleTables().transactions.first().copy(isExcluded = false))
            )
        )

        useCase(backups = backups, transactions = transactions)(included)

        // Detection can add an exclusion but never remove one: counting a row twice is the failure
        // that matters here, and the user can put it back.
        assertThat(backups.restored!!.transactions.single().isExcluded).isTrue()
    }

    @Test
    fun `refuses to write when the duplicate check fails`() = runTest {
        val backups = RecordingBackupDataSource()
        val transactions = FakeDuplicateKeySource(Result.Error(DataError.Local.UNKNOWN))

        val result = useCase(backups = backups, transactions = transactions)(backupFile())

        // The upload path shrugs this off and imports unflagged, because a statement the user can
        // see beats none. A restore cannot: it usually overlaps what is already here, and going
        // ahead unflagged would double every total with nothing on screen to say why.
        assertThat((result as Result.Error).error).isEqualTo(DataError.Local.UNKNOWN)
        assertThat(backups.restores).isEqualTo(0)
    }

    @Test
    fun `passes the writer's failure back rather than reporting a restore`() = runTest {
        val backups = RecordingBackupDataSource(Result.Error(DataError.Local.DISK_FULL))

        val result = useCase(backups = backups)(backupFile())

        assertThat((result as Result.Error).error).isEqualTo(DataError.Local.DISK_FULL)
    }

    @Test
    fun `restores an empty backup without a write failing`() = runTest {
        val backups = RecordingBackupDataSource()

        val result = useCase(backups = backups)(backupFile(tables = emptyTables()))

        assertThat(result).isEqualTo(Result.Success(emptyRestoreReport()))
        assertThat(backups.restored!!.transactions).hasSize(0)
    }
}
