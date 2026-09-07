package com.madtitan94.transactionsparser.core.designsystem.charts

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

/**
 * The rules the palette ships with, as tests.
 *
 * The claim these are defending is `design/Charts.dc.html`'s: *"colour follows the category, not the
 * rank — filtering to a shorter list must not repaint the survivors."* That is a property, not an
 * appearance, which is why it can be asserted here rather than eyeballed on a device.
 */
class ChartSeriesTest {

    private fun amount(id: Long?, label: String, paise: Long) = CategoryAmount(id, label, paise)

    /** Both fold labels are fixed here so a test only has to say what it is actually varying. */
    private fun build(
        amounts: List<CategoryAmount>,
        maxNamed: Int = DEFAULT_MAX_NAMED_SLICES
    ) = buildCategorySlices(
        amounts = amounts,
        uncategorisedLabel = "Uncategorised",
        otherLabel = "Other",
        maxNamed = maxNamed
    )

    @Test
    fun `a category keeps its slot when a bigger one is filtered out`() {
        val full = listOf(
            amount(1L, "Groceries", 10_960_00),
            amount(2L, "Rent", 8_010_00),
            amount(3L, "Transport", 6_325_00)
        )

        val before = build(full)
        val after = build(full.drop(1))

        val transportBefore = before.first { it.label == "Transport" }.slot
        val transportAfter = after.first { it.label == "Transport" }.slot

        assertThat(transportAfter).isEqualTo(transportBefore)
    }

    @Test
    fun `the eighth category folds into Other rather than taking a new colour`() {
        val eight = (1L..8L).map { amount(it, "Category $it", (9L - it) * 1_000_00) }

        val slices = build(eight, maxNamed = 5)

        assertThat(slices).hasSize(6)
        assertThat(slices.map { it.label })
            .containsExactly("Category 1", "Category 2", "Category 3", "Category 4", "Category 5", "Other")
        // Categories 6, 7 and 8 — 3 + 2 + 1 thousand rupees — end up in the one folded slice.
        assertThat(slices.last().amountPaise).isEqualTo(6_000_00L)
    }

    @Test
    fun `an overflowing tail is never reported as uncategorised money`() {
        // The bug this is here to stop: an account with more than five categories and nothing
        // unmapped saw its smallest categories added up, labelled "Uncategorised" and hatched,
        // while W4's nudge — which reads the unmapped total directly — correctly showed nothing.
        val everythingMapped = (1L..8L).map { amount(it, "Category $it", (9L - it) * 1_000_00) }

        val slices = build(everythingMapped, maxNamed = 5)

        assertThat(slices.none { it.label == "Uncategorised" }).isEqualTo(true)
        assertThat(slices.none { it.isUnnamed }).isEqualTo(true)
        assertThat(slices.last().slot).isNotNull()
    }

    @Test
    fun `an overflowing tail and unmapped money are two separate slices`() {
        val both = (1L..8L).map { amount(it, "Category $it", (9L - it) * 1_000_00) } +
            amount(null, "raw upi string", 500_00)

        val slices = build(both, maxNamed = 5)

        assertThat(slices).hasSize(7)
        assertThat(slices.map { it.label }.takeLast(2)).containsExactly("Other", "Uncategorised")
        assertThat(slices[5].amountPaise).isEqualTo(6_000_00L)
        assertThat(slices[5].slot).isNotNull()
        assertThat(slices[6].amountPaise).isEqualTo(500_00L)
        assertThat(slices[6].slot).isNull()
    }

    @Test
    fun `the Other slice never steals a colour a kept category prefers`() {
        val eight = (1L..8L).map { amount(it, "Category $it", (9L - it) * 1_000_00) }

        val slices = build(eight, maxNamed = 5)

        val slots = slices.mapNotNull { it.slot }
        assertThat(slots).hasSize(6)
        assertThat(slots.toSet()).hasSize(6)
    }

    @Test
    fun `uncategorised spend gets its own slice and sorts to the bottom`() {
        val slices = build(
            listOf(
                amount(null, "raw upi string", 1_690_00),
                amount(1L, "Groceries", 10_960_00)
            )
        )

        assertThat(slices).hasSize(2)
        assertThat(slices.last().label).isEqualTo("Uncategorised")
        assertThat(slices.last().amountPaise).isEqualTo(1_690_00L)
        assertThat(slices.last().isUnnamed).isEqualTo(true)
    }

    @Test
    fun `a period where everything has a name shows no unnamed slice at all`() {
        val slices = build(
            listOf(amount(1L, "Groceries", 500_00), amount(2L, "Rent", 400_00))
        )

        assertThat(slices).hasSize(2)
        assertThat(slices.none { it.isUnnamed }).isEqualTo(true)
    }

    @Test
    fun `the first category a user creates gets the first colour`() {
        // The artboard leads with amber, and an account with four categories should see it.
        assertThat(preferredSlot(1L)).isEqualTo(0)
    }

    @Test
    fun `two categories preferring the same slot never share a colour in one chart`() {
        // 1 and 8 both prefer slot 0.
        val slices = build(
            listOf(amount(1L, "Groceries", 900_00), amount(8L, "Travel", 800_00))
        )

        val slots = slices.mapNotNull { it.slot }
        assertThat(slots).hasSize(2)
        assertThat(slots.toSet()).hasSize(2)
    }

    @Test
    fun `non-positive amounts are dropped rather than drawn as zero-width slices`() {
        val slices = build(
            listOf(
                amount(1L, "Groceries", 500_00),
                amount(2L, "Refunded", 0L),
                amount(3L, "Reversed", -100_00)
            )
        )

        assertThat(slices).hasSize(1)
        assertThat(slices.single().label).isEqualTo("Groceries")
    }

    @Test
    fun `an empty period produces no slices`() {
        assertThat(build(emptyList())).isEmpty()
        assertThat(build(listOf(amount(1L, "Groceries", 0L)))).isEmpty()
    }

    @Test
    fun `ties break on category id so two runs of the same data agree`() {
        val tied = listOf(amount(3L, "Third", 100_00), amount(1L, "First", 100_00))

        val once = build(tied)
        val twice = build(tied.reversed())

        assertThat(once.map { it.label }).containsExactly("First", "Third")
        assertThat(twice.map { it.label }).containsExactly("First", "Third")
    }

    @Test
    fun `shares are whole numbers that add up to a hundred`() {
        val slices = build(
            (1L..3L).map { amount(it, "Category $it", 100_00) }
        )

        assertThat(slices.sumOf { it.sharePercent }).isEqualTo(100)
        assertThat(slices.map { it.sharePercent }.toSet()).hasSize(2) // 34 / 33 / 33
    }

    @Test
    fun `every slot the palette offers is reachable`() {
        val seven = (1L..7L).map { amount(it, "Category $it", (8L - it) * 1_000_00) }

        val slots = build(seven, maxNamed = CHART_SLOT_COUNT)
            .mapNotNull { it.slot }

        assertThat(slots.toSet()).isEqualTo((0 until CHART_SLOT_COUNT).toSet())
    }

    @Test
    fun `preferred slot is stable and inside the palette for any category id`() {
        listOf(0L, 1L, 7L, 8L, 999L, Long.MAX_VALUE).forEach { id ->
            val slot = preferredSlot(id)
            assertThat(slot).isEqualTo(preferredSlot(id))
            assertThat(slot in 0 until CHART_SLOT_COUNT).isEqualTo(true)
        }
    }

    @Test
    fun `whole percentages of an empty total are all zero rather than a division by it`() {
        assertThat(wholePercentages(listOf(0L, 0L))).containsExactly(0, 0)
        assertThat(wholePercentages(emptyList())).isEmpty()
    }

    @Test
    fun `the uncategorised slice keeps a slot of null so it is drawn as the hatch`() {
        val slices = build(
            listOf(amount(1L, "Groceries", 500_00), amount(null, "unknown", 100_00))
        )

        assertThat(slices.first().slot).isNotNull()
        assertThat(slices.last().slot).isNull()
    }
}
