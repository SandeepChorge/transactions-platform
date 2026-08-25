package com.madtitan94.transactionsparser.core.domain.backup

import com.madtitan94.transactionsparser.core.domain.datasource.BackupLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.TransactionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.duplicate.DuplicateDetector
import com.madtitan94.transactionsparser.core.domain.model.Transaction
import com.madtitan94.transactionsparser.core.domain.model.TransactionType
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.Result

/**
 * Writes a validated backup into the signed-in account.
 *
 * Takes a file that [ReadBackupUseCase] has already accepted, so nothing here decides whether the
 * restore *should* happen — by this point the user has seen what is in the file and said yes.
 *
 * Duplicate detection runs here rather than in the data layer for the same reason it does on the
 * upload path: the rules are pure and stay testable without a database, and running the same
 * [DuplicateDetector] means a statement that arrives inside a backup is deduplicated exactly as it
 * would have been had the user uploaded its PDF.
 */
class RestoreBackupUseCase(
    private val backups: BackupLocalDataSource,
    private val transactions: TransactionLocalDataSource
) {

    suspend operator fun invoke(file: BackupFile): Result<RestoreReport, DataError.Local> {
        val candidates = file.transactions.map { it.toCandidate() }

        // The upload path shrugs off a failed lookup and imports everything unflagged, because a
        // statement the user can see beats no statement at all. A restore cannot afford that: the
        // file usually overlaps what is already here, and importing it unflagged would double every
        // total with nothing on screen to say so.
        val existingKeys = when (val found = transactions.findDuplicateKeys(candidates)) {
            is Result.Error -> return Result.Error(found.error)
            is Result.Success -> found.data
        }

        val flagged = DuplicateDetector.flag(candidates, existingKeys)
        val rows = file.transactions.zip(flagged) { source, detected ->
            RestoreTransaction(
                source = source,
                // The file's own flag is kept as well: it records a repeat found when this row was
                // first imported, which is still true, and detection here only sees what this
                // database already holds.
                isDuplicate = source.isDuplicate || detected.isDuplicate,
                // isExcluded is the user's decision and travels with the row. Detection can add an
                // exclusion but never remove one — a row the user chose to count still ends up
                // excluded if this account already has it, which is what keeps totals right.
                isExcluded = source.isExcluded || detected.isExcluded,
                duplicateOfLocalId = detected.duplicateOfTransactionId
            )
        }

        return backups.restore(
            RestorePayload(
                categories = file.categories,
                payees = file.payees,
                payeeIdentifiers = file.payeeIdentifiers,
                sessions = file.sessions,
                transactions = rows,
                uploadLogs = file.uploadLogs
            )
        )
    }

    /**
     * The file's row as something [DuplicateDetector] can compare. Ids are left at zero on purpose:
     * they belong to the database the file came from, and letting one leak into a match would point
     * a back-link at whatever local row happened to share the number.
     */
    private fun BackupTransaction.toCandidate() = Transaction(
        sessionId = 0L,
        dateTimeUtcMillis = dateTimeUtcMillis,
        rawPayee = rawPayee,
        normalizedPayee = normalizedPayee,
        amountPaise = amountPaise,
        // Safe because validation refused any value that is not one of these.
        type = TransactionType.valueOf(type),
        transactionRef = transactionRef,
        utr = utr,
        payeeId = null
    )
}
