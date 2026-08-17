package com.madtitan94.transactionsparser.feature.sessions.domain

import com.madtitan94.transactionsparser.core.domain.model.Payee
import com.madtitan94.transactionsparser.core.domain.model.Transaction
import java.time.Instant
import java.time.ZoneOffset

data class TypicalTime(val hourOfDay: Int, val count: Int)

/**
 * How much of a group's flagged rows are left out of the totals.
 *
 * A boolean was enough while the group button was the only way to change exclusion, because it
 * always wrote all-or-nothing. The per-transaction toggle on Payee Detail can leave a group part
 * way, and [SOME] is what stops the card from claiming those rows are counted when some are not.
 */
enum class DuplicateSelection {
    /** Every flagged row counts toward the totals. */
    NONE,

    /** Some are counted and some are not — reachable only via the per-transaction toggle. */
    SOME,

    /** No flagged row counts. What a fresh import produces. */
    ALL;

    companion object {
        fun of(total: Int, excluded: Int): DuplicateSelection = when {
            total == 0 || excluded == 0 -> NONE
            excluded == total -> ALL
            else -> SOME
        }
    }
}

data class PayeeGroup(
    val normalizedPayee: String,
    val rawPayee: String,
    /** Distinct amounts, ascending — the "repetitive amounts" summary. Excluded rows omitted. */
    val amountsPaise: List<Long>,
    /** Sum of counted rows only — excluded duplicates never inflate this. */
    val totalPaise: Long,
    /** Counted rows only, so this always agrees with [totalPaise]. */
    val transactionCount: Int,
    /** Rows in this group flagged as repeats of an earlier import, counted or not. */
    val duplicateCount: Int,
    /** Flagged rows here that are left out of the totals. */
    val excludedDuplicateCount: Int,
    /** How much of [duplicateCount] is excluded, as the three states the card can show. */
    val duplicateSelection: DuplicateSelection,
    /** Most frequent hours of day, count-descending, max 3. */
    val typicalTimes: List<TypicalTime>,
    /** Existing saved mapping for this payee, when the user mapped it before. */
    val knownPayee: Payee?,
    /** True when every counted transaction in this group already has a payee assigned. */
    val isAssigned: Boolean
)

object PayeeGrouper {

    fun group(
        transactions: List<Transaction>,
        knownPayees: Map<String, Payee>
    ): List<PayeeGroup> {
        return transactions
            .groupBy { it.normalizedPayee }
            .map { (normalized, group) ->
                // Every summary figure comes from the counted rows so they agree with each other;
                // the group itself still shows up when all of its rows are excluded, otherwise a
                // wrongly-flagged payee would vanish with no way to bring it back.
                val counted = group.filterNot { it.isExcluded }
                val duplicates = group.filter { it.isDuplicate }
                val excludedDuplicates = duplicates.count { it.isExcluded }
                PayeeGroup(
                    normalizedPayee = normalized,
                    rawPayee = group.first().rawPayee,
                    amountsPaise = counted.map { it.amountPaise }.distinct().sorted(),
                    totalPaise = counted.sumOf { it.amountPaise },
                    transactionCount = counted.size,
                    duplicateCount = duplicates.size,
                    excludedDuplicateCount = excludedDuplicates,
                    duplicateSelection = DuplicateSelection.of(duplicates.size, excludedDuplicates),
                    typicalTimes = typicalTimes(counted),
                    knownPayee = knownPayees[normalized],
                    isAssigned = counted.all { it.payeeId != null }
                )
            }
            .sortedWith(
                compareBy<PayeeGroup> { it.isAssigned }
                    .thenByDescending { it.totalPaise }
            )
    }

    private fun typicalTimes(group: List<Transaction>): List<TypicalTime> {
        return group
            .groupingBy { hourOfDay(it.dateTimeUtcMillis) }
            .eachCount()
            .map { (hour, count) -> TypicalTime(hour, count) }
            .sortedWith(compareByDescending<TypicalTime> { it.count }.thenBy { it.hourOfDay })
            .take(3)
    }

    private fun hourOfDay(utcMillis: Long): Int =
        Instant.ofEpochMilli(utcMillis).atOffset(ZoneOffset.UTC).hour
}
