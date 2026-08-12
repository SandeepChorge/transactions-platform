package com.madtitan94.transactionsparser.core.database.account

import androidx.room.withTransaction
import com.madtitan94.transactionsparser.core.database.TransactionsDatabase
import com.madtitan94.transactionsparser.core.database.dao.LegacyOwnershipDao
import com.madtitan94.transactionsparser.core.database.migration.LEGACY_OWNER_ID

/**
 * Hands pre-multi-account rows to their real owner.
 *
 * The 1 -> 2 migration can't read the signed-in session, so it tags existing rows
 * [LEGACY_OWNER_ID] instead. The app was single-account before that migration, so whoever
 * signs in first on this device is by definition the owner of everything already here.
 * Idempotent: once claimed, no rows carry [LEGACY_OWNER_ID] and later calls update nothing.
 */
class LegacyDataClaimer(
    private val database: TransactionsDatabase,
    private val dao: LegacyOwnershipDao
) {
    suspend fun claimFor(ownerId: String) {
        if (ownerId == NO_OWNER_ID || ownerId == LEGACY_OWNER_ID) return

        database.withTransaction {
            // Payees and transactions reference categories/sessions by id, not ownerId,
            // so the order here doesn't matter — but a single transaction means a crash
            // mid-claim can't leave half the data stranded under the legacy owner.
            dao.claimCategories(LEGACY_OWNER_ID, ownerId)
            dao.claimPayees(LEGACY_OWNER_ID, ownerId)
            dao.claimSessions(LEGACY_OWNER_ID, ownerId)
            dao.claimTransactions(LEGACY_OWNER_ID, ownerId)
            dao.claimUploadLogs(LEGACY_OWNER_ID, ownerId)
        }
    }
}
