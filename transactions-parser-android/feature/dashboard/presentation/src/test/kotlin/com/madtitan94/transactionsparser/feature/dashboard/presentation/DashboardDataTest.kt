package com.madtitan94.transactionsparser.feature.dashboard.presentation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.madtitan94.transactionsparser.core.domain.model.CategoryTotal
import com.madtitan94.transactionsparser.core.domain.model.PayeeSummary
import com.madtitan94.transactionsparser.core.domain.model.TypeTotals
import org.junit.jupiter.api.Test

/**
 * The three sentences the dashboards state in words rather than draw.
 *
 * Every one of them is a claim about the user's own money — "you spent 12% more", "88% of your
 * spend is named", "mostly Groceries" — so the cases pinned here are the ones where an arithmetic
 * shortcut would produce a confident sentence that is not true.
 */
class DashboardDataTest {

    private fun spent(paise: Long) = TypeTotals(debitPaise = paise, debitCount = 1)

    private fun category(id: Long?, name: String?, paise: Long) =
        CategoryTotal(categoryId = id, categoryName = name, totalPaise = paise, transactionCount = 1)

    // --- mapped share -------------------------------------------------------------------------

    @Test
    fun `mapped share is by value, not by count`() {
        val data = DashboardData(
            typeTotals = spent(1_000_00),
            payeeSummary = PayeeSummary(
                payeeCount = 31,
                unmappedPaise = 120_00,
                unmappedPayeeCount = 9,
                unmappedTransactionCount = 40
            )
        )
        // Nine unmapped names across forty transactions still only account for ₹120 of ₹1,000, and
        // the headline is about money rather than about rows.
        assertThat(data.mappedPercent).isEqualTo(88)
    }

    @Test
    fun `a period with no spend is fully mapped, not zero mapped`() {
        val data = DashboardData(typeTotals = TypeTotals(), payeeSummary = PayeeSummary())
        // Dividing by a zero total gives NaN, which renders as "0% mapped" and invents a
        // data-quality problem in a month where nothing was spent at all.
        assertThat(DashboardData().mappedPercent).isEqualTo(100)
        assertThat(data.mappedPercent).isEqualTo(100)
    }

    @Test
    fun `nothing mapped reads as zero rather than as a negative`() {
        val data = DashboardData(
            typeTotals = spent(500_00),
            payeeSummary = PayeeSummary(
                payeeCount = 4,
                unmappedPaise = 500_00,
                unmappedPayeeCount = 4,
                unmappedTransactionCount = 4
            )
        )
        assertThat(data.mappedPercent).isEqualTo(0)
    }

    // --- delta --------------------------------------------------------------------------------

    @Test
    fun `spending more than last period reads as a positive delta`() {
        val data = DashboardData(typeTotals = spent(1_120_00), previousDebitPaise = 1_000_00)
        assertThat(data.deltaPercent).isEqualTo(12)
    }

    @Test
    fun `spending less reads as a negative one`() {
        val data = DashboardData(typeTotals = spent(800_00), previousDebitPaise = 1_000_00)
        assertThat(data.deltaPercent).isEqualTo(-20)
    }

    @Test
    fun `no previous period means no comparison line`() {
        // All time has no "before", so the hero drops the clause instead of comparing against
        // an assumed zero and reporting every account's whole history as pure growth.
        assertThat(DashboardData(typeTotals = spent(900_00)).deltaPercent).isNull()
    }

    @Test
    fun `a previous period of zero means no comparison line`() {
        val data = DashboardData(typeTotals = spent(900_00), previousDebitPaise = 0L)
        // Any increase from nothing is infinite. A first month after an import would otherwise
        // read "+900%" or worse, which looks like a number and means nothing.
        assertThat(data.deltaPercent).isNull()
    }

    @Test
    fun `an unchanged period is flat rather than absent`() {
        val data = DashboardData(typeTotals = spent(1_000_00), previousDebitPaise = 1_000_00)
        assertThat(data.deltaPercent).isEqualTo(0)
    }

    // --- biggest mover ------------------------------------------------------------------------

    @Test
    fun `the mover is what changed, not what is largest`() {
        val data = DashboardData(
            typeTotals = spent(1_200_00),
            categoryTotals = listOf(
                category(1L, "Rent", 800_00),
                category(2L, "Groceries", 400_00)
            ),
            previousDebitPaise = 1_000_00,
            previousCategoryTotals = listOf(
                category(1L, "Rent", 800_00),
                category(2L, "Groceries", 200_00)
            )
        )
        // Rent is the biggest category every single month and explains nothing. Groceries is the
        // ₹200 that the "+20%" beside it actually consists of.
        assertThat(data.biggestMover).isEqualTo("Groceries")
    }

    @Test
    fun `a category that is new this period counts as its whole self`() {
        val data = DashboardData(
            typeTotals = spent(1_300_00),
            categoryTotals = listOf(
                category(1L, "Rent", 800_00),
                category(3L, "Travel", 500_00)
            ),
            previousDebitPaise = 800_00,
            previousCategoryTotals = listOf(category(1L, "Rent", 800_00))
        )
        assertThat(data.biggestMover).isEqualTo("Travel")
    }

    @Test
    fun `a fall names what fell, not what rose`() {
        val data = DashboardData(
            typeTotals = spent(700_00),
            categoryTotals = listOf(
                category(1L, "Rent", 600_00),
                category(2L, "Groceries", 100_00)
            ),
            previousDebitPaise = 1_000_00,
            previousCategoryTotals = listOf(
                category(1L, "Rent", 500_00),
                category(2L, "Groceries", 500_00)
            )
        )
        // Spend is down, so the clause must name a decrease. Naming Rent — which rose — would read
        // "You spent 30% less, mostly Rent" under a downward arrow.
        assertThat(data.biggestMover).isEqualTo("Groceries")
    }

    @Test
    fun `unmapped spend is never named as the mover`() {
        val data = DashboardData(
            typeTotals = spent(1_200_00),
            categoryTotals = listOf(
                category(null, null, 900_00),
                category(2L, "Groceries", 300_00)
            ),
            previousDebitPaise = 1_000_00,
            previousCategoryTotals = listOf(category(2L, "Groceries", 200_00))
        )
        // The null bucket is not a category and has no name to print. It moved the most here, and
        // the sentence still has to be about Groceries rather than about "null".
        assertThat(data.biggestMover).isEqualTo("Groceries")
    }

    @Test
    fun `no mover without a delta to explain`() {
        val data = DashboardData(
            typeTotals = spent(1_200_00),
            categoryTotals = listOf(category(2L, "Groceries", 1_200_00))
        )
        assertThat(data.biggestMover).isNull()
    }

    @Test
    fun `no mover when nothing moved in the direction of the delta`() {
        val data = DashboardData(
            typeTotals = spent(1_100_00),
            categoryTotals = listOf(category(2L, "Groceries", 1_100_00)),
            previousDebitPaise = 1_000_00,
            previousCategoryTotals = listOf(category(2L, "Groceries", 1_100_00))
        )
        // The rise came from a category that has since dropped out of the list entirely, so there
        // is no honest clause to add and the sentence stops after the percentage.
        assertThat(data.biggestMover).isNull()
    }

    @Test
    fun `spend is read from the type split so it cannot drift from the tiles`() {
        val data = DashboardData(typeTotals = TypeTotals(debitPaise = 640_00, creditPaise = 5_000_00))
        // Credits are not spending. Netting them in would make a salary month look free.
        assertThat(data.debitPaise).isEqualTo(640_00L)
    }
}
