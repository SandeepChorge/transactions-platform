package com.madtitan94.transactionsparser.feature.sessions.domain

import com.madtitan94.transactionsparser.core.domain.model.Payee
import com.madtitan94.transactionsparser.core.domain.model.Transaction
import java.time.Instant
import java.time.ZoneOffset

data class TypicalTime(val hourOfDay: Int, val count: Int)

data class PayeeGroup(
    val normalizedPayee: String,
    val rawPayee: String,
    /** Distinct amounts, ascending — the "repetitive amounts" summary. */
    val amountsPaise: List<Long>,
    val totalPaise: Long,
    val transactionCount: Int,
    /** Most frequent hours of day, count-descending, max 3. */
    val typicalTimes: List<TypicalTime>,
    /** Existing saved mapping for this payee, when the user mapped it before. */
    val knownPayee: Payee?,
    /** True when every transaction in this group already has a payee assigned. */
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
                PayeeGroup(
                    normalizedPayee = normalized,
                    rawPayee = group.first().rawPayee,
                    amountsPaise = group.map { it.amountPaise }.distinct().sorted(),
                    totalPaise = group.sumOf { it.amountPaise },
                    transactionCount = group.size,
                    typicalTimes = typicalTimes(group),
                    knownPayee = knownPayees[normalized],
                    isAssigned = group.all { it.payeeId != null }
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
