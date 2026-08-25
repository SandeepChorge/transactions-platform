package com.madtitan94.transactionsparser.core.domain.backup

import kotlinx.serialization.Serializable

/**
 * The version of the *file format*, bumped whenever the shape below stops being readable by an
 * older app. Distinct from the Room schema version, which describes the database the rows came
 * from — a backup can be re-encoded without the database changing, and vice versa.
 */
const val BACKUP_FORMAT_VERSION: Int = 1

/**
 * A complete, lossless copy of one account's data.
 *
 * **No row carries an `ownerId`, and that is deliberate.** Ownership is resolved inside the data
 * layer from whoever is signed in; if a file could name an owner, a restore could write rows owned
 * by an account that is not signed in — invisible to every query and unreachable forever. Leaving
 * the field out of the format entirely is a stronger guarantee than remembering not to read it.
 *
 * Ids are kept because the relationships between the tables have to be expressible, but they are
 * file-local handles only. A restore remaps every one of them and never trusts them as database ids.
 *
 * None of the table properties has a default. That is what makes a missing key a parse failure
 * rather than a silently empty table — an important distinction when the file is the only copy of
 * the user's data.
 *
 * [account] is nullable so a backup taken while the session read was racing still produces a valid
 * file; it is metadata for the "this data belongs to a different account" prompt and nothing else.
 */
@Serializable
data class BackupFile(
    val formatVersion: Int,
    /** The Room version the rows came from, so a file from a newer app can be refused rather than truncated. */
    val schemaVersion: Int,
    val exportedAtMillis: Long,
    val app: BackupApp,
    val account: BackupAccount?,
    val categories: List<BackupCategory>,
    val payees: List<BackupPayee>,
    val payeeIdentifiers: List<BackupPayeeIdentifier>,
    val sessions: List<BackupSession>,
    val transactions: List<BackupTransaction>,
    val uploadLogs: List<BackupUploadLog>
)

@Serializable
data class BackupApp(
    val versionName: String,
    val versionCode: Int
)

@Serializable
data class BackupAccount(
    val email: String,
    val googleId: String
)

@Serializable
data class BackupCategory(
    val id: Long,
    val name: String,
    val isDeleted: Boolean,
    val deletedAtMillis: Long?
)

@Serializable
data class BackupPayee(
    val id: Long,
    val alias: String,
    val categoryId: Long,
    val isDeleted: Boolean,
    val deletedAtMillis: Long?
)

/** No soft-delete columns, matching the table: an identifier moves between payees or goes with one. */
@Serializable
data class BackupPayeeIdentifier(
    val id: Long,
    val payeeId: Long,
    val rawName: String,
    val normalizedName: String
)

@Serializable
data class BackupSession(
    val id: Long,
    val fileName: String,
    /** [com.madtitan94.transactionsparser.core.domain.model.StatementSource] by name, as Room stores it. */
    val source: String,
    val uploadedAtMillis: Long,
    val periodStartMillis: Long?,
    val periodEndMillis: Long?,
    /** [com.madtitan94.transactionsparser.core.domain.model.SessionStatus] by name. */
    val status: String,
    val isDeleted: Boolean,
    val deletedAtMillis: Long?
)

/**
 * Amounts stay integer paise and timestamps stay epoch millis read as-if-UTC — the two things a
 * "friendlier" format would round-trip badly. Statement times are the PDF's printed wall clock, so
 * any formatting step invites the timezone conversion that corrupts them.
 */
@Serializable
data class BackupTransaction(
    val id: Long,
    val sessionId: Long,
    val dateTimeUtcMillis: Long,
    val rawPayee: String,
    val normalizedPayee: String,
    val amountPaise: Long,
    /** [com.madtitan94.transactionsparser.core.domain.model.TransactionType] by name. */
    val type: String,
    val transactionRef: String?,
    val utr: String?,
    val payeeId: Long?,
    val isDuplicate: Boolean,
    val duplicateOfTransactionId: Long?,
    val isExcluded: Boolean,
    val isDeleted: Boolean,
    val deletedAtMillis: Long?
)

@Serializable
data class BackupUploadLog(
    val id: Long,
    val fileName: String,
    val uploadedAtMillis: Long,
    val success: Boolean,
    val source: String?,
    val failureReason: String?,
    val sessionId: Long?,
    val isDeleted: Boolean,
    val deletedAtMillis: Long?
)

/**
 * The six tables as read from the database, before an envelope is wrapped around them.
 *
 * Separate from [BackupFile] so the data layer can hand back rows without also having to know the
 * app version, the signed-in account or the clock — none of which are its business.
 */
data class BackupTables(
    val categories: List<BackupCategory>,
    val payees: List<BackupPayee>,
    val payeeIdentifiers: List<BackupPayeeIdentifier>,
    val sessions: List<BackupSession>,
    val transactions: List<BackupTransaction>,
    val uploadLogs: List<BackupUploadLog>
)

/**
 * One read of the whole account, with the schema version the rows were read under.
 *
 * The version is reported by the database itself rather than by a constant in this module, so it
 * cannot drift from what actually shipped.
 */
data class BackupSnapshot(
    val schemaVersion: Int,
    val tables: BackupTables
)

/** What the user is told after a backup is written. */
data class BackupSummary(
    val categories: Int,
    val payees: Int,
    val payeeIdentifiers: Int,
    val sessions: Int,
    val transactions: Int,
    val uploadLogs: Int
) {
    val totalRows: Int
        get() = categories + payees + payeeIdentifiers + sessions + transactions + uploadLogs
}
