package com.madtitan94.transactionsparser.core.domain.model

enum class TransactionType { DEBIT, CREDIT }

enum class StatementSource { PHONEPE, GOOGLE_PAY }

enum class SessionStatus { PENDING, COMPLETED, CANCELLED }

data class Category(
    val id: Long = 0L,
    val name: String
)

/**
 * A payee mapping created by the user: statement name -> user-known alias + category.
 * [normalizedName] is the dedup key used to auto-map payees on future uploads.
 */
data class Payee(
    val id: Long = 0L,
    val rawName: String,
    val normalizedName: String,
    val alias: String,
    val categoryId: Long
)

data class StatementSession(
    val id: Long = 0L,
    val fileName: String,
    val source: StatementSource,
    val uploadedAtMillis: Long,
    val periodStartMillis: Long?,
    val periodEndMillis: Long?,
    val status: SessionStatus
)

/**
 * Progress for one session, as the history list shows it.
 *
 * [countedCount] and [mappedCount] respect the user's exclusions; [transactionCount] counts every
 * row the session imported. The two differ whenever a statement repeats an earlier one, and the
 * gap is what lets an all-duplicate import say so rather than reporting "0 of 0".
 */
data class SessionSummary(
    val session: StatementSession,
    val transactionCount: Int,
    val countedCount: Int,
    val mappedCount: Int
)

/**
 * A single statement transaction persisted for a session.
 * [dateTimeUtcMillis] stores the statement's wall-clock date-time as if it were UTC,
 * so it round-trips losslessly regardless of device timezone.
 */
data class Transaction(
    val id: Long = 0L,
    val sessionId: Long,
    val dateTimeUtcMillis: Long,
    val rawPayee: String,
    val normalizedPayee: String,
    val amountPaise: Long,
    val type: TransactionType,
    val transactionRef: String?,
    val utr: String?,
    val payeeId: Long?,
    /** Detected at import as a repeat of [duplicateOfTransactionId]. Not user-editable. */
    val isDuplicate: Boolean = false,
    val duplicateOfTransactionId: Long? = null,
    /** Left out of every total. Starts equal to [isDuplicate]; the user can toggle it. */
    val isExcluded: Boolean = false
)

/**
 * Everything the Payee Detail header shows, aggregated in SQL rather than by loading rows —
 * the transaction list beneath it is paginated precisely because loading them all doesn't scale.
 *
 * [countedTotalPaise] and [countedCount] respect the user's exclusions; [transactionCount] and
 * the date range describe every row, so a payee whose transactions are all excluded still shows
 * the period they fall in rather than looking empty.
 */
data class PayeeTotals(
    val countedTotalPaise: Long = 0L,
    val countedCount: Int = 0,
    val transactionCount: Int = 0,
    val duplicateCount: Int = 0,
    val excludedDuplicateCount: Int = 0,
    val firstMillis: Long? = null,
    val lastMillis: Long? = null
)

/**
 * Subtotal for one day or one month of a payee's history.
 *
 * Kept out of the paged stream on purpose: a day's rows can straddle a page boundary, so a
 * separator that summed only what it could see would show a subtotal that changes as you scroll.
 * [startMillis] is the start of the period, matching how the rows themselves are read back.
 */
data class PeriodTotal(
    val startMillis: Long,
    val countedTotalPaise: Long,
    val countedCount: Int
)

/**
 * One transaction flattened for export, with the payee mapping and statement resolved in SQL.
 *
 * Separate from [Transaction] because an export is a different shape from what the app renders:
 * it carries the human-readable alias and category the user actually mapped, not the foreign
 * keys, and it keeps [isDuplicate] and [isExcluded] as visible columns rather than filtering
 * rows out — a spreadsheet the user can audit beats one that silently disagrees with the app.
 */
data class TransactionExportRow(
    val dateTimeUtcMillis: Long,
    val rawPayee: String,
    /** Null while the payee is unmapped — exported as an empty cell, not as a guess. */
    val alias: String?,
    val category: String?,
    val amountPaise: Long,
    val type: TransactionType,
    val transactionRef: String?,
    val utr: String?,
    val isDuplicate: Boolean,
    val isExcluded: Boolean,
    val statementFileName: String
)

/**
 * The identity fields of an already-stored transaction — everything duplicate matching needs,
 * without loading whole rows for an import that may only match a handful of them.
 */
data class TransactionKey(
    val id: Long,
    val transactionRef: String?,
    val utr: String?,
    val normalizedPayee: String,
    val amountPaise: Long,
    val dateTimeUtcMillis: Long
)

data class UploadLog(
    val id: Long = 0L,
    val fileName: String,
    val uploadedAtMillis: Long,
    val success: Boolean,
    val source: StatementSource?,
    val failureReason: String?,
    val sessionId: Long?
)

data class UserSession(
    val googleId: String,
    val name: String,
    val email: String,
    val photoUrl: String?
)

fun normalizePayee(raw: String): String =
    raw.trim().replace(Regex("\\s+"), " ").uppercase()
