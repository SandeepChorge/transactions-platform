package com.madtitan94.transactionsparser.core.domain.backup

/**
 * A small but complete account: two categories, a payee owning two statement names, one session,
 * two transactions (one of them a flagged duplicate pointing at the other), and an upload log.
 *
 * Deliberately exercises every nullable field and every cross-table reference, including the
 * self-referencing one — the shapes a round-trip is most likely to lose.
 */
fun sampleTables(
    categories: List<BackupCategory> = listOf(
        BackupCategory(id = 1L, name = "Food", isDeleted = false, deletedAtMillis = null),
        BackupCategory(id = 2L, name = "Travel", isDeleted = true, deletedAtMillis = 1_700_000_000_000L)
    ),
    payees: List<BackupPayee> = listOf(
        BackupPayee(id = 10L, alias = "Corner Cafe", categoryId = 1L, isDeleted = false, deletedAtMillis = null)
    ),
    payeeIdentifiers: List<BackupPayeeIdentifier> = listOf(
        BackupPayeeIdentifier(id = 100L, payeeId = 10L, rawName = "Corner  Cafe", normalizedName = "CORNER CAFE"),
        BackupPayeeIdentifier(id = 101L, payeeId = 10L, rawName = "CORNERCAFE LLP", normalizedName = "CORNERCAFE LLP")
    ),
    sessions: List<BackupSession> = listOf(
        BackupSession(
            id = 1000L,
            fileName = "june.pdf",
            source = "PHONEPE",
            uploadedAtMillis = 1_750_000_000_000L,
            periodStartMillis = 1_749_000_000_000L,
            periodEndMillis = 1_751_000_000_000L,
            status = "COMPLETED",
            isDeleted = false,
            deletedAtMillis = null
        )
    ),
    transactions: List<BackupTransaction> = listOf(
        BackupTransaction(
            id = 10_000L,
            sessionId = 1000L,
            dateTimeUtcMillis = 1_750_000_123_000L,
            rawPayee = "Corner  Cafe",
            normalizedPayee = "CORNER CAFE",
            amountPaise = 25_800L,
            type = "DEBIT",
            transactionRef = "REF1",
            utr = "UTR1",
            payeeId = 10L,
            isDuplicate = false,
            duplicateOfTransactionId = null,
            isExcluded = false,
            isDeleted = false,
            deletedAtMillis = null
        ),
        BackupTransaction(
            id = 10_001L,
            sessionId = 1000L,
            dateTimeUtcMillis = 1_750_000_123_000L,
            rawPayee = "Corner  Cafe",
            normalizedPayee = "CORNER CAFE",
            amountPaise = 25_800L,
            type = "DEBIT",
            transactionRef = null,
            utr = null,
            payeeId = null,
            isDuplicate = true,
            duplicateOfTransactionId = 10_000L,
            isExcluded = true,
            isDeleted = false,
            deletedAtMillis = null
        )
    ),
    uploadLogs: List<BackupUploadLog> = listOf(
        BackupUploadLog(
            id = 5L,
            fileName = "june.pdf",
            uploadedAtMillis = 1_750_000_000_000L,
            success = true,
            source = "PHONEPE",
            failureReason = null,
            sessionId = 1000L,
            isDeleted = false,
            deletedAtMillis = null
        )
    )
) = BackupTables(
    categories = categories,
    payees = payees,
    payeeIdentifiers = payeeIdentifiers,
    sessions = sessions,
    transactions = transactions,
    uploadLogs = uploadLogs
)

fun emptyTables() = BackupTables(
    categories = emptyList(),
    payees = emptyList(),
    payeeIdentifiers = emptyList(),
    sessions = emptyList(),
    transactions = emptyList(),
    uploadLogs = emptyList()
)

fun backupFile(
    tables: BackupTables = sampleTables(),
    formatVersion: Int = BACKUP_FORMAT_VERSION,
    schemaVersion: Int = 4,
    exportedAtMillis: Long = 1_756_000_000_000L,
    app: BackupApp = BackupApp("1.0.33", 33),
    account: BackupAccount? = BackupAccount("someone@example.com", "google-sub-1")
) = BackupFile(
    formatVersion = formatVersion,
    schemaVersion = schemaVersion,
    exportedAtMillis = exportedAtMillis,
    app = app,
    account = account,
    categories = tables.categories,
    payees = tables.payees,
    payeeIdentifiers = tables.payeeIdentifiers,
    sessions = tables.sessions,
    transactions = tables.transactions,
    uploadLogs = tables.uploadLogs
)
