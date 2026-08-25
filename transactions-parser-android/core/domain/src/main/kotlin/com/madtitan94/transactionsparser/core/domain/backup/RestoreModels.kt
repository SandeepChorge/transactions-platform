package com.madtitan94.transactionsparser.core.domain.backup

import com.madtitan94.transactionsparser.core.domain.util.Error

/**
 * Why a backup file cannot be restored.
 *
 * Deliberately specific rather than a single "invalid file": the user is holding what may be the
 * only copy of their data, and "this backup was written by a newer version of the app" tells them
 * what to do next in a way that "import failed" does not.
 */
sealed interface BackupError : Error {
    /** Not JSON, not an object, or missing one of the tables a backup must carry. */
    data object NotABackup : BackupError

    data class UnsupportedFormat(val formatVersion: Int) : BackupError

    /** Written by a build whose database is ahead of this one's, so its rows may not fit. */
    data class NewerSchema(val fileSchemaVersion: Int, val supportedSchemaVersion: Int) : BackupError

    /**
     * A reference points at a row the file does not contain. [example] names one of them, because
     * a bare count leaves nobody able to say what went wrong.
     */
    data class BrokenReferences(val count: Int, val example: String) : BackupError

    data class DuplicateIds(val table: String, val count: Int) : BackupError

    /** An enum column carries something this build has no meaning for. */
    data class UnknownValue(val field: String, val value: String) : BackupError

    data class InvalidValue(val field: String, val detail: String) : BackupError

    /** Two statement names in the file normalize to the same thing, which one account cannot hold. */
    data class ConflictingNames(val normalizedName: String) : BackupError

    /** The file could not be read at all — missing, unreadable, or too large. */
    data object CouldNotRead : BackupError
}

/**
 * A backup that has passed every check, with what the confirmation screen needs to describe it.
 *
 * [file] is the validated content, not the raw file: normalized names have been recomputed under
 * this build's rules, so what gets written is what this app would have written itself.
 */
data class BackupPreview(
    val file: BackupFile,
    val summary: BackupSummary,
    val exportedAtMillis: Long,
    val appVersionName: String,
    val account: BackupAccount?,
    /**
     * True when the file was exported by a different Google account than the one signed in. The
     * restore still works — moving data between accounts is the point — but it is the surprise
     * that has to be prevented, so the flow asks first.
     */
    val isDifferentAccount: Boolean
)

/**
 * One transaction as the restore will write it: the file's row, plus what duplicate detection
 * decided about it a moment ago.
 *
 * The file's own ids are left untouched here; remapping them needs the ids the database hands back
 * on insert, so it happens in the data layer.
 */
data class RestoreTransaction(
    val source: BackupTransaction,
    val isDuplicate: Boolean,
    val isExcluded: Boolean,
    /**
     * A row *already in this database* that this one repeats. Null when it repeats another row from
     * the same file — [source]'s own `duplicateOfTransactionId` names that one, in the file's ids,
     * and it can only be resolved once the file's rows have been inserted.
     */
    val duplicateOfLocalId: Long?
)

/**
 * Everything the data layer needs to write a restore, with duplicate detection already applied.
 *
 * Carries backup rows rather than domain models on purpose: the writer has to see the file's ids to
 * rebuild the references between tables, and a domain model would have thrown them away.
 */
data class RestorePayload(
    val categories: List<BackupCategory>,
    val payees: List<BackupPayee>,
    val payeeIdentifiers: List<BackupPayeeIdentifier>,
    val sessions: List<BackupSession>,
    val transactions: List<RestoreTransaction>,
    val uploadLogs: List<BackupUploadLog>
)

/**
 * A statement name the file mapped to one payee that this account already maps to another.
 *
 * One account can only read a statement name one way, so something has to give. The local mapping
 * wins — it is the one the user has been using — and the conflict is reported rather than applied
 * silently.
 */
data class IdentifierConflict(
    val normalizedName: String,
    val keptPayeeAlias: String,
    val filePayeeAlias: String
)

/** What a restore actually did, per table, for the screen shown when it finishes. */
data class RestoreReport(
    val categoriesInserted: Int,
    /** Categories the account already had under the same name, so the file's rows point at those. */
    val categoriesReused: Int,
    val payees: Int,
    val payeeIdentifiers: Int,
    val sessions: Int,
    val transactions: Int,
    val uploadLogs: Int,
    /**
     * Transactions the file carried that this account already had. Kept and flagged rather than
     * dropped, and excluded from totals — exactly what an overlapping statement upload does.
     */
    val duplicatesFlagged: Int,
    val identifierConflicts: List<IdentifierConflict>
)
