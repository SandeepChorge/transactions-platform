package com.madtitan94.transactionsparser.core.domain.duplicate

import com.madtitan94.transactionsparser.core.domain.model.Transaction
import com.madtitan94.transactionsparser.core.domain.model.TransactionKey

/**
 * Decides which incoming transactions repeat one the account already has.
 *
 * Matching is on payment identity, never on dates — which is what makes re-importing a statement
 * whose period overlaps an earlier one fall out for free, with no period-comparison logic.
 *
 * Pure by design: it takes the existing keys as an argument rather than querying, so every rule
 * below is testable without a database.
 *
 * Lives here rather than in `feature:upload` because two features now import transactions — a PDF
 * statement and a backup file — and they must agree on what counts as a repeat. A restore that
 * deduplicated differently from an upload would leave the same rows counted twice depending on how
 * they arrived.
 */
object DuplicateDetector {

    /**
     * Why a row was considered a repeat. Ref and UTR are issuer-assigned identifiers and are
     * treated as authoritative; [COMPOSITE] is a weaker inference used only for rows that carry
     * neither, so it's worth telling the two apart when explaining a flag to the user.
     */
    enum class MatchKind { REF, UTR, COMPOSITE }

    /**
     * [duplicateOfId] is null when the row it repeats is in the same import batch and therefore
     * has no database id yet. The flag still stands — only the back-link is unavailable, and the
     * "original" in that case was imported at the same moment anyway.
     */
    data class Match(val duplicateOfId: Long?, val kind: MatchKind)

    /**
     * Returns [candidates] with duplicates flagged and excluded, in the original order.
     *
     * [existingKeys] are the stored transactions worth comparing against — typically whatever
     * `TransactionLocalDataSource.findDuplicateKeys` returned for this batch.
     */
    fun flag(
        candidates: List<Transaction>,
        existingKeys: List<TransactionKey>
    ): List<Transaction> {
        val byRef = existingKeys.mapNotNull { key -> key.transactionRef?.let { it to key } }.toMap()
        val byUtr = existingKeys.mapNotNull { key -> key.utr?.let { it to key } }.toMap()
        val byComposite = existingKeys
            .filter { it.transactionRef == null && it.utr == null }
            .associateBy { compositeKey(it.normalizedPayee, it.amountPaise, it.dateTimeUtcMillis) }

        // A statement can also repeat a payment inside itself, so rows already accepted from this
        // same batch have to be matchable too — otherwise the second copy sails through. These
        // rows have no id yet, so only their keys are tracked.
        val seenRefs = mutableSetOf<String>()
        val seenUtrs = mutableSetOf<String>()
        val seenComposites = mutableSetOf<String>()

        return candidates.map { txn ->
            val match = matchFor(txn, byRef, byUtr, byComposite, seenRefs, seenUtrs, seenComposites)

            if (match == null) {
                // Only unflagged rows are registered as "seen", so a third copy points at the
                // original rather than at the second copy that was itself flagged.
                txn.transactionRef?.let(seenRefs::add)
                txn.utr?.let(seenUtrs::add)
                if (txn.transactionRef == null && txn.utr == null) {
                    seenComposites.add(
                        compositeKey(txn.normalizedPayee, txn.amountPaise, txn.dateTimeUtcMillis)
                    )
                }
                txn
            } else {
                txn.copy(
                    isDuplicate = true,
                    duplicateOfTransactionId = match.duplicateOfId,
                    // Flagged rows start excluded so totals are right the moment the import
                    // finishes; the user can put any of them back.
                    isExcluded = true
                )
            }
        }
    }

    private fun matchFor(
        txn: Transaction,
        byRef: Map<String, TransactionKey>,
        byUtr: Map<String, TransactionKey>,
        byComposite: Map<String, TransactionKey>,
        seenRefs: Set<String>,
        seenUtrs: Set<String>,
        seenComposites: Set<String>
    ): Match? {
        txn.transactionRef?.let { ref ->
            byRef[ref]?.let { return Match(it.id, MatchKind.REF) }
            if (ref in seenRefs) return Match(duplicateOfId = null, MatchKind.REF)
        }
        txn.utr?.let { utr ->
            byUtr[utr]?.let { return Match(it.id, MatchKind.UTR) }
            if (utr in seenUtrs) return Match(duplicateOfId = null, MatchKind.UTR)
        }
        // Weakest signal, so it only applies when the statement gave us nothing better to go on.
        if (txn.transactionRef == null && txn.utr == null) {
            val key = compositeKey(txn.normalizedPayee, txn.amountPaise, txn.dateTimeUtcMillis)
            byComposite[key]?.let { return Match(it.id, MatchKind.COMPOSITE) }
            if (key in seenComposites) return Match(duplicateOfId = null, MatchKind.COMPOSITE)
        }
        return null
    }

    private fun compositeKey(normalizedPayee: String, amountPaise: Long, dateTimeUtcMillis: Long) =
        "$normalizedPayee|$amountPaise|$dateTimeUtcMillis"
}
