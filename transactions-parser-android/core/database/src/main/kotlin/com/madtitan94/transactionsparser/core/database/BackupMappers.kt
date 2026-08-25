package com.madtitan94.transactionsparser.core.database

import com.madtitan94.transactionsparser.core.database.entity.CategoryEntity
import com.madtitan94.transactionsparser.core.database.entity.PayeeEntity
import com.madtitan94.transactionsparser.core.database.entity.PayeeIdentifierEntity
import com.madtitan94.transactionsparser.core.database.entity.SessionEntity
import com.madtitan94.transactionsparser.core.database.entity.TransactionEntity
import com.madtitan94.transactionsparser.core.database.entity.UploadLogEntity
import com.madtitan94.transactionsparser.core.domain.backup.BackupCategory
import com.madtitan94.transactionsparser.core.domain.backup.BackupPayee
import com.madtitan94.transactionsparser.core.domain.backup.BackupPayeeIdentifier
import com.madtitan94.transactionsparser.core.domain.backup.BackupSession
import com.madtitan94.transactionsparser.core.domain.backup.BackupTransaction
import com.madtitan94.transactionsparser.core.domain.backup.BackupUploadLog

/**
 * Entity → backup row.
 *
 * Kept apart from [Mappers] because these map the *whole* row rather than the part the app renders,
 * and because every one of them drops `ownerId` on the floor. That omission is the important thing
 * here: the backup format has no owner field, so a restore cannot write rows belonging to anyone
 * but whoever is signed in. Adding one to this file would undo that guarantee silently.
 *
 * There is no mapper in the other direction yet — a restore builds entities itself, because it has
 * to remap every id as it goes and a plain row-to-row mapper would invite trusting the file's.
 */
fun CategoryEntity.toBackupCategory() = BackupCategory(
    id = id,
    name = name,
    isDeleted = isDeleted,
    deletedAtMillis = deletedAtMillis
)

fun PayeeEntity.toBackupPayee() = BackupPayee(
    id = id,
    alias = alias,
    categoryId = categoryId,
    isDeleted = isDeleted,
    deletedAtMillis = deletedAtMillis
)

fun PayeeIdentifierEntity.toBackupPayeeIdentifier() = BackupPayeeIdentifier(
    id = id,
    payeeId = payeeId,
    rawName = rawName,
    normalizedName = normalizedName
)

fun SessionEntity.toBackupSession() = BackupSession(
    id = id,
    fileName = fileName,
    source = source,
    uploadedAtMillis = uploadedAtMillis,
    periodStartMillis = periodStartMillis,
    periodEndMillis = periodEndMillis,
    status = status,
    isDeleted = isDeleted,
    deletedAtMillis = deletedAtMillis
)

fun TransactionEntity.toBackupTransaction() = BackupTransaction(
    id = id,
    sessionId = sessionId,
    dateTimeUtcMillis = dateTimeUtcMillis,
    rawPayee = rawPayee,
    normalizedPayee = normalizedPayee,
    amountPaise = amountPaise,
    type = type,
    transactionRef = transactionRef,
    utr = utr,
    payeeId = payeeId,
    isDuplicate = isDuplicate,
    duplicateOfTransactionId = duplicateOfTransactionId,
    isExcluded = isExcluded,
    isDeleted = isDeleted,
    deletedAtMillis = deletedAtMillis
)

fun UploadLogEntity.toBackupUploadLog() = BackupUploadLog(
    id = id,
    fileName = fileName,
    uploadedAtMillis = uploadedAtMillis,
    success = success,
    source = source,
    failureReason = failureReason,
    sessionId = sessionId,
    isDeleted = isDeleted,
    deletedAtMillis = deletedAtMillis
)
