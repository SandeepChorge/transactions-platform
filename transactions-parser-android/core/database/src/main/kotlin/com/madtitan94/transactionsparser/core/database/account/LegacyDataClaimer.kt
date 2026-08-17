package com.madtitan94.transactionsparser.core.database.account

import androidx.room.withTransaction
import com.madtitan94.transactionsparser.core.database.TransactionsDatabase
import com.madtitan94.transactionsparser.core.database.dao.LegacyOwnershipDao
import com.madtitan94.transactionsparser.core.database.migration.LEGACY_OWNER_ID

/**
 * Hands rows that predate the current owner-id scheme to their real owner.
 *
 * Two cases, both one-time and both idempotent:
 *
 * 1. The 1 -> 2 migration can't read the signed-in session, so it tags existing rows
 *    [LEGACY_OWNER_ID]. The app was single-account before that migration, so whoever signs in
 *    first on this device is by definition the owner of everything already here.
 * 2. Builds before the switch to Google's `sub` stored the account's **email** as the owner id.
 *    Those rows are re-keyed to the stable `sub` the first time that same account signs in.
 *
 * Case 2 only ever moves rows the signing-in account already owned — the email is passed in
 * from that account's own session — so it can't reach another account's data.
 */
class LegacyDataClaimer(
    private val database: TransactionsDatabase,
    private val dao: LegacyOwnershipDao
) {
    /**
     * @param ownerId the signed-in account's `sub`.
     * @param priorOwnerIds owner ids this same account used before, e.g. its email address.
     */
    suspend fun claimFor(ownerId: String, priorOwnerIds: List<String> = emptyList()) {
        if (ownerId == NO_OWNER_ID || ownerId == LEGACY_OWNER_ID) return

        val sources = (listOf(LEGACY_OWNER_ID) + priorOwnerIds)
            .filter { it.isNotBlank() && it != ownerId }
            .distinct()

        database.withTransaction {
            // Payees and transactions reference categories/sessions by id, not ownerId, so the
            // order doesn't matter — but a single transaction means a crash mid-claim can't
            // leave half the data stranded under the old owner.
            sources.forEach { from ->
                dao.claimCategories(from, ownerId)
                dao.claimPayees(from, ownerId)
                dao.claimSessions(from, ownerId)
                dao.claimTransactions(from, ownerId)
                dao.claimUploadLogs(from, ownerId)
            }
        }
    }
}
