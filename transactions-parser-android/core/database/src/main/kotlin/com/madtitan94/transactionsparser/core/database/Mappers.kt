package com.madtitan94.transactionsparser.core.database

import com.madtitan94.transactionsparser.core.database.dao.DuplicateCandidate
import com.madtitan94.transactionsparser.core.database.dao.PayeeTotalsRow
import com.madtitan94.transactionsparser.core.database.dao.PeriodTotalRow
import com.madtitan94.transactionsparser.core.database.dao.SessionSummaryRow
import com.madtitan94.transactionsparser.core.database.dao.TransactionExportRowEntity
import com.madtitan94.transactionsparser.core.database.entity.CategoryEntity
import com.madtitan94.transactionsparser.core.database.entity.PayeeEntity
import com.madtitan94.transactionsparser.core.database.entity.SessionEntity
import com.madtitan94.transactionsparser.core.database.entity.TransactionEntity
import com.madtitan94.transactionsparser.core.database.entity.UploadLogEntity
import com.madtitan94.transactionsparser.core.domain.model.Category
import com.madtitan94.transactionsparser.core.domain.model.Payee
import com.madtitan94.transactionsparser.core.domain.model.PayeeTotals
import com.madtitan94.transactionsparser.core.domain.model.PeriodTotal
import com.madtitan94.transactionsparser.core.domain.model.SessionStatus
import com.madtitan94.transactionsparser.core.domain.model.SessionSummary
import com.madtitan94.transactionsparser.core.domain.model.StatementSession
import com.madtitan94.transactionsparser.core.domain.model.StatementSource
import com.madtitan94.transactionsparser.core.domain.model.Transaction
import com.madtitan94.transactionsparser.core.domain.model.TransactionExportRow
import com.madtitan94.transactionsparser.core.domain.model.TransactionKey
import com.madtitan94.transactionsparser.core.domain.model.TransactionType
import com.madtitan94.transactionsparser.core.domain.model.UploadLog

fun CategoryEntity.toCategory() = Category(id = id, name = name)
fun Category.toCategoryEntity(ownerId: String) = CategoryEntity(id = id, ownerId = ownerId, name = name)

fun PayeeEntity.toPayee() = Payee(
    id = id,
    rawName = rawName,
    normalizedName = normalizedName,
    alias = alias,
    categoryId = categoryId
)

fun Payee.toPayeeEntity(ownerId: String) = PayeeEntity(
    id = id,
    ownerId = ownerId,
    rawName = rawName,
    normalizedName = normalizedName,
    alias = alias,
    categoryId = categoryId
)

fun SessionEntity.toStatementSession() = StatementSession(
    id = id,
    fileName = fileName,
    source = StatementSource.valueOf(source),
    uploadedAtMillis = uploadedAtMillis,
    periodStartMillis = periodStartMillis,
    periodEndMillis = periodEndMillis,
    status = SessionStatus.valueOf(status)
)

fun StatementSession.toSessionEntity(ownerId: String) = SessionEntity(
    id = id,
    ownerId = ownerId,
    fileName = fileName,
    source = source.name,
    uploadedAtMillis = uploadedAtMillis,
    periodStartMillis = periodStartMillis,
    periodEndMillis = periodEndMillis,
    status = status.name
)

fun SessionSummaryRow.toSessionSummary() = SessionSummary(
    session = session.toStatementSession(),
    transactionCount = transactionCount,
    countedCount = countedCount,
    mappedCount = mappedCount
)

fun TransactionEntity.toTransaction() = Transaction(
    id = id,
    sessionId = sessionId,
    dateTimeUtcMillis = dateTimeUtcMillis,
    rawPayee = rawPayee,
    normalizedPayee = normalizedPayee,
    amountPaise = amountPaise,
    type = TransactionType.valueOf(type),
    transactionRef = transactionRef,
    utr = utr,
    payeeId = payeeId,
    isDuplicate = isDuplicate,
    duplicateOfTransactionId = duplicateOfTransactionId,
    isExcluded = isExcluded
)

fun Transaction.toTransactionEntity(ownerId: String) = TransactionEntity(
    id = id,
    ownerId = ownerId,
    sessionId = sessionId,
    dateTimeUtcMillis = dateTimeUtcMillis,
    rawPayee = rawPayee,
    normalizedPayee = normalizedPayee,
    amountPaise = amountPaise,
    type = type.name,
    transactionRef = transactionRef,
    utr = utr,
    payeeId = payeeId,
    isDuplicate = isDuplicate,
    duplicateOfTransactionId = duplicateOfTransactionId,
    isExcluded = isExcluded
)

fun TransactionExportRowEntity.toTransactionExportRow() = TransactionExportRow(
    dateTimeUtcMillis = dateTimeUtcMillis,
    rawPayee = rawPayee,
    alias = alias,
    category = category,
    amountPaise = amountPaise,
    type = TransactionType.valueOf(type),
    transactionRef = transactionRef,
    utr = utr,
    isDuplicate = isDuplicate,
    isExcluded = isExcluded,
    statementFileName = statementFileName
)

fun DuplicateCandidate.toTransactionKey() = TransactionKey(
    id = id,
    transactionRef = transactionRef,
    utr = utr,
    normalizedPayee = normalizedPayee,
    amountPaise = amountPaise,
    dateTimeUtcMillis = dateTimeUtcMillis
)

fun UploadLogEntity.toUploadLog() = UploadLog(
    id = id,
    fileName = fileName,
    uploadedAtMillis = uploadedAtMillis,
    success = success,
    source = source?.let(StatementSource::valueOf),
    failureReason = failureReason,
    sessionId = sessionId
)

fun UploadLog.toUploadLogEntity(ownerId: String) = UploadLogEntity(
    id = id,
    ownerId = ownerId,
    fileName = fileName,
    uploadedAtMillis = uploadedAtMillis,
    success = success,
    source = source?.name,
    failureReason = failureReason,
    sessionId = sessionId
)

/** A payee with no rows aggregates to all-NULL, which reads as an empty total, not an error. */
fun PayeeTotalsRow.toPayeeTotals() = PayeeTotals(
    countedTotalPaise = countedTotalPaise ?: 0L,
    countedCount = countedCount,
    transactionCount = transactionCount,
    duplicateCount = duplicateCount,
    excludedDuplicateCount = excludedDuplicateCount,
    firstMillis = firstMillis,
    lastMillis = lastMillis
)

fun PeriodTotalRow.toPeriodTotal() = PeriodTotal(
    startMillis = startMillis,
    countedTotalPaise = countedTotalPaise ?: 0L,
    countedCount = countedCount
)
