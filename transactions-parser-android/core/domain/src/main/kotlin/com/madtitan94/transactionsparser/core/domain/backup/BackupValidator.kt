package com.madtitan94.transactionsparser.core.domain.backup

import com.madtitan94.transactionsparser.core.domain.model.SessionStatus
import com.madtitan94.transactionsparser.core.domain.model.StatementSource
import com.madtitan94.transactionsparser.core.domain.model.TransactionType
import com.madtitan94.transactionsparser.core.domain.model.normalizePayee
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.serialization.SerializationException

/**
 * Decides whether a file can be restored, before a single row is written.
 *
 * Every check here is one a half-finished restore would have made expensive: a broken reference
 * discovered after two tables are already written leaves the user worse off than refusing outright.
 * Pure, so all of it is testable without a database.
 *
 * It returns a *corrected* file rather than the parsed one. The correction is deliberate and
 * narrow — normalized names are recomputed under this build's rules rather than trusted — because
 * a file written when normalization worked differently would otherwise import names that never
 * match anything again, silently breaking auto-mapping and duplicate detection.
 */
object BackupValidator {

    fun validate(text: String, supportedSchemaVersion: Int): Result<BackupFile, BackupError> {
        val parsed = try {
            BackupCodec.decode(text)
        } catch (e: SerializationException) {
            // Covers both "this is not JSON" and "this is JSON but a table is missing" — a table
            // key absent from the file is a malformed backup, not an empty table.
            return Result.Error(BackupError.NotABackup)
        } catch (e: IllegalArgumentException) {
            return Result.Error(BackupError.NotABackup)
        }

        envelopeError(parsed, supportedSchemaVersion)?.let { return Result.Error(it) }
        uniqueIdError(parsed)?.let { return Result.Error(it) }
        referenceError(parsed)?.let { return Result.Error(it) }
        valueError(parsed)?.let { return Result.Error(it) }

        val corrected = parsed.withRecomputedNames()
        // Recomputing can in principle collapse two names into one; whether it did or the file
        // already carried the collision, the result is the same and one account cannot hold it.
        collidingNameError(corrected)?.let { return Result.Error(it) }

        return Result.Success(corrected)
    }

    private fun envelopeError(file: BackupFile, supportedSchemaVersion: Int): BackupError? = when {
        file.formatVersion != BACKUP_FORMAT_VERSION ->
            BackupError.UnsupportedFormat(file.formatVersion)
        // Older is fine and is read as the format it is. Newer means the file may carry columns
        // this build has nowhere to put, so it is refused rather than partially understood.
        file.schemaVersion > supportedSchemaVersion ->
            BackupError.NewerSchema(file.schemaVersion, supportedSchemaVersion)
        else -> null
    }

    private fun uniqueIdError(file: BackupFile): BackupError? {
        duplicateIds("categories", file.categories.map { it.id })?.let { return it }
        duplicateIds("payees", file.payees.map { it.id })?.let { return it }
        duplicateIds("payeeIdentifiers", file.payeeIdentifiers.map { it.id })?.let { return it }
        duplicateIds("sessions", file.sessions.map { it.id })?.let { return it }
        duplicateIds("transactions", file.transactions.map { it.id })?.let { return it }
        duplicateIds("uploadLogs", file.uploadLogs.map { it.id })?.let { return it }
        return null
    }

    private fun duplicateIds(table: String, ids: List<Long>): BackupError? {
        val repeated = ids.size - ids.distinct().size
        return if (repeated > 0) BackupError.DuplicateIds(table, repeated) else null
    }

    /**
     * Every cross-table reference has to resolve inside the file. A restore rebuilds these
     * references against ids the database assigns, so one pointing at nothing has no answer other
     * than dropping the row — and a restore that silently drops rows is the failure this whole
     * feature exists to avoid.
     */
    private fun referenceError(file: BackupFile): BackupError? {
        val categoryIds = file.categories.mapTo(mutableSetOf()) { it.id }
        val payeeIds = file.payees.mapTo(mutableSetOf()) { it.id }
        val sessionIds = file.sessions.mapTo(mutableSetOf()) { it.id }
        val transactionIds = file.transactions.mapTo(mutableSetOf()) { it.id }

        val broken = buildList {
            file.payees.forEach {
                if (it.categoryId !in categoryIds) add("payee ${it.id} → category ${it.categoryId}")
            }
            file.payeeIdentifiers.forEach {
                if (it.payeeId !in payeeIds) add("identifier ${it.id} → payee ${it.payeeId}")
            }
            file.transactions.forEach { txn ->
                if (txn.sessionId !in sessionIds) add("transaction ${txn.id} → session ${txn.sessionId}")
                txn.payeeId?.let { if (it !in payeeIds) add("transaction ${txn.id} → payee $it") }
                txn.duplicateOfTransactionId?.let {
                    if (it !in transactionIds) add("transaction ${txn.id} → transaction $it")
                }
            }
            file.uploadLogs.forEach { log ->
                log.sessionId?.let { if (it !in sessionIds) add("upload log ${log.id} → session $it") }
            }
        }

        return if (broken.isEmpty()) null else BackupError.BrokenReferences(broken.size, broken.first())
    }

    private fun valueError(file: BackupFile): BackupError? {
        file.sessions.forEach { session ->
            if (!isKnown<StatementSource>(session.source)) {
                return BackupError.UnknownValue("sessions.source", session.source)
            }
            if (!isKnown<SessionStatus>(session.status)) {
                return BackupError.UnknownValue("sessions.status", session.status)
            }
            if (session.uploadedAtMillis < 0) {
                return BackupError.InvalidValue("sessions.uploadedAtMillis", session.id.toString())
            }
        }
        file.transactions.forEach { txn ->
            if (!isKnown<TransactionType>(txn.type)) {
                return BackupError.UnknownValue("transactions.type", txn.type)
            }
            // amountPaise needs no range check: anything that does not fit a Long fails to parse,
            // so by the time it is here it already fits.
            if (txn.dateTimeUtcMillis < 0) {
                return BackupError.InvalidValue("transactions.dateTimeUtcMillis", txn.id.toString())
            }
        }
        file.uploadLogs.forEach { log ->
            // Null is legitimate here: a statement that failed before its format was recognised
            // was logged without one.
            val source = log.source ?: return@forEach
            if (!isKnown<StatementSource>(source)) {
                return BackupError.UnknownValue("uploadLogs.source", source)
            }
        }
        return null
    }

    private fun collidingNameError(file: BackupFile): BackupError? {
        val seen = mutableSetOf<String>()
        file.payeeIdentifiers.forEach {
            if (!seen.add(it.normalizedName)) {
                return BackupError.ConflictingNames(it.normalizedName)
            }
        }
        return null
    }

    private fun BackupFile.withRecomputedNames(): BackupFile = copy(
        payeeIdentifiers = payeeIdentifiers.map { it.copy(normalizedName = normalizePayee(it.rawName)) },
        transactions = transactions.map { it.copy(normalizedPayee = normalizePayee(it.rawPayee)) }
    )

    private inline fun <reified T : Enum<T>> isKnown(value: String): Boolean =
        enumValues<T>().any { it.name == value }
}
