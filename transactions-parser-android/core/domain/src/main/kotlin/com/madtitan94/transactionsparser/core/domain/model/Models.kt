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

data class SessionSummary(
    val session: StatementSession,
    val transactionCount: Int,
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
    val payeeId: Long?
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
