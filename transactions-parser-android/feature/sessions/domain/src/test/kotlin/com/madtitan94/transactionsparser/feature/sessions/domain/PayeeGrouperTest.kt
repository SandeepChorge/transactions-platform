package com.madtitan94.transactionsparser.feature.sessions.domain

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.madtitan94.transactionsparser.core.domain.model.Payee
import com.madtitan94.transactionsparser.core.domain.model.Transaction
import com.madtitan94.transactionsparser.core.domain.model.TransactionType
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class PayeeGrouperTest {

    private fun txn(
        payee: String,
        amountPaise: Long,
        hour: Int,
        payeeId: Long? = null
    ) = Transaction(
        sessionId = 1L,
        dateTimeUtcMillis = LocalDateTime.of(2026, 7, 3, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        rawPayee = payee,
        normalizedPayee = payee.uppercase(),
        amountPaise = amountPaise,
        type = TransactionType.DEBIT,
        transactionRef = null,
        utr = null,
        payeeId = payeeId
    )

    @Test
    fun `groups repetitive payee like the abc shop example`() {
        // Daily ~1 PM ₹10 coffee, once ₹20 extra — the spec's example.
        val groups = PayeeGrouper.group(
            transactions = listOf(
                txn("abc shop", 1_000, hour = 13),
                txn("abc shop", 2_000, hour = 16)
            ),
            knownPayees = emptyMap()
        )

        assertThat(groups.size).isEqualTo(1)
        val group = groups.first()
        assertThat(group.amountsPaise).containsExactly(1_000L, 2_000L)
        assertThat(group.totalPaise).isEqualTo(3_000L)
        assertThat(group.transactionCount).isEqualTo(2)
        assertThat(group.typicalTimes.map { it.hourOfDay }).containsExactly(13, 16)
    }

    @Test
    fun `deduplicates repeated amounts`() {
        val groups = PayeeGrouper.group(
            transactions = listOf(
                txn("AWDHOOT SNACKS CENTRE", 5_500, hour = 13),
                txn("AWDHOOT SNACKS CENTRE", 5_500, hour = 13),
                txn("AWDHOOT SNACKS CENTRE", 3_500, hour = 11)
            ),
            knownPayees = emptyMap()
        )

        val group = groups.first()
        assertThat(group.amountsPaise).containsExactly(3_500L, 5_500L)
        assertThat(group.typicalTimes.first()).isEqualTo(TypicalTime(hourOfDay = 13, count = 2))
    }

    @Test
    fun `unassigned groups come before assigned ones`() {
        val groups = PayeeGrouper.group(
            transactions = listOf(
                txn("MAPPED SHOP", 100_000, hour = 10, payeeId = 7L),
                txn("NEW SHOP", 500, hour = 12)
            ),
            knownPayees = emptyMap()
        )

        assertThat(groups[0].normalizedPayee).isEqualTo("NEW SHOP")
        assertThat(groups[0].isAssigned).isFalse()
        assertThat(groups[1].isAssigned).isTrue()
    }

    @Test
    fun `attaches known payee mapping for auto-map suggestions`() {
        val known = Payee(id = 3L, alias = "Groceries app", categoryId = 2L)

        val groups = PayeeGrouper.group(
            transactions = listOf(txn("Blinkit", 20_500, hour = 21)),
            knownPayees = mapOf("BLINKIT" to known)
        )

        assertThat(groups.first().knownPayee).isNotNull()
        assertThat(groups.first().knownPayee?.alias).isEqualTo("Groceries app")
    }

    @Test
    fun `unknown payee has no suggestion`() {
        val groups = PayeeGrouper.group(
            transactions = listOf(txn("SRI DATTA SUPER SHOPPE", 1_000, hour = 13)),
            knownPayees = emptyMap()
        )
        assertThat(groups.first().knownPayee).isNull()
    }

    @Test
    fun `excluded duplicates do not inflate the total or the count`() {
        val groups = PayeeGrouper.group(
            transactions = listOf(
                txn("abc shop", 1_000, hour = 13),
                txn("abc shop", 1_000, hour = 13).copy(isDuplicate = true, isExcluded = true)
            ),
            knownPayees = emptyMap()
        )

        val group = groups.single()
        assertThat(group.totalPaise).isEqualTo(1_000L)
        assertThat(group.transactionCount).isEqualTo(1)
        assertThat(group.duplicateCount).isEqualTo(1)
        assertThat(group.duplicateSelection).isEqualTo(DuplicateSelection.ALL)
    }

    @Test
    fun `a duplicate the user chose to count is back in the total`() {
        val groups = PayeeGrouper.group(
            transactions = listOf(
                txn("abc shop", 1_000, hour = 13),
                txn("abc shop", 1_000, hour = 13).copy(isDuplicate = true, isExcluded = false)
            ),
            knownPayees = emptyMap()
        )

        val group = groups.single()
        assertThat(group.totalPaise).isEqualTo(2_000L)
        assertThat(group.duplicateCount).isEqualTo(1)
        assertThat(group.duplicateSelection).isEqualTo(DuplicateSelection.NONE)
    }

    @Test
    fun `a payee whose rows are all excluded still appears so it can be recovered`() {
        val groups = PayeeGrouper.group(
            transactions = listOf(
                txn("abc shop", 1_000, hour = 13).copy(isDuplicate = true, isExcluded = true)
            ),
            knownPayees = emptyMap()
        )

        val group = groups.single()
        assertThat(group.rawPayee).isEqualTo("abc shop")
        assertThat(group.totalPaise).isEqualTo(0L)
        assertThat(group.transactionCount).isEqualTo(0)
        assertThat(group.duplicateCount).isEqualTo(1)
    }

    @Test
    fun `a part-excluded group reports SOME, so the card can't claim they all count`() {
        // Only reachable via the per-transaction toggle on Payee Detail; before that existed a
        // boolean was enough, and would report this group as fully counted.
        val groups = PayeeGrouper.group(
            transactions = listOf(
                txn("abc shop", 1_000, hour = 13).copy(isDuplicate = true, isExcluded = true),
                txn("abc shop", 2_000, hour = 14).copy(isDuplicate = true, isExcluded = false)
            ),
            knownPayees = emptyMap()
        )

        val group = groups.single()
        assertThat(group.duplicateCount).isEqualTo(2)
        assertThat(group.excludedDuplicateCount).isEqualTo(1)
        assertThat(group.duplicateSelection).isEqualTo(DuplicateSelection.SOME)
        // The counted total follows the rows, not the group's label.
        assertThat(group.totalPaise).isEqualTo(2_000L)
    }

    @Test
    fun `a group with no duplicates reports none, so the badge stays hidden`() {
        val groups = PayeeGrouper.group(
            transactions = listOf(txn("abc shop", 1_000, hour = 13)),
            knownPayees = emptyMap()
        )

        assertThat(groups.single().duplicateCount).isEqualTo(0)
        assertThat(groups.single().duplicateSelection).isEqualTo(DuplicateSelection.NONE)
    }
}
