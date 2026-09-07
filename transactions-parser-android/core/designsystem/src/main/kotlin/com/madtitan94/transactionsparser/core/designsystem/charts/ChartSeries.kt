package com.madtitan94.transactionsparser.core.designsystem.charts

import kotlin.math.floor

/**
 * How a category gets its colour, and what happens to everything that does not fit in seven slots.
 *
 * This file is deliberately free of Compose: it decides *which slot* a category takes, never what
 * colour that slot is. The colours live in `AppColors.chartSeries` and are looked up by the
 * composable at draw time, which is what lets one slot mean one thing in both themes and lets this
 * logic be unit-tested on the JVM.
 *
 * The rule the palette ships with (`design/Charts.dc.html`) is *"colour follows the category, not
 * the rank — filtering to a shorter list must not repaint the survivors."* That is the whole reason
 * [preferredSlot] is a function of the category id alone and not of its position in a sorted list.
 * A chart that assigns slot 0 to whoever happens to be biggest this month tells the user the
 * colours mean something they do not.
 */

/**
 * The number of category colours. Must equal `AppColors.chartSeries.size` in both themes —
 * `ChartSeriesTest` asserts it, because a palette edit that adds an eighth colour without changing
 * this constant would leave that colour permanently unreachable.
 */
const val CHART_SLOT_COUNT = 7

/**
 * How many named categories a chart draws before the rest is folded away.
 *
 * Five, from `design/DashboardSpec.dc.html` W3 and W7, and not seven: the donut legend and the
 * ranked list both stop being readable before the palette runs out.
 */
const val DEFAULT_MAX_NAMED_SLICES = 5

/**
 * One category's spend for the period, as the data layer produces it.
 *
 * [categoryId] is null when the transaction has no category at all — an unmapped payee. Those rows
 * are the thing the dashboard most needs to admit, so they are never dropped; they become the
 * hatched slice in [buildCategorySlices], and nothing else ever joins them there.
 */
data class CategoryAmount(
    val categoryId: Long?,
    val label: String,
    val amountPaise: Long
)

/**
 * One drawable slice, with its colour slot already decided.
 *
 * [slot] is an index into `AppColors.chartSeries`, or null for the uncategorised slice — which is
 * drawn as the 115° hatch rather than as an eighth hue, so that "we don't know" never looks like a
 * category.
 *
 * [sharePercent] values are whole numbers that sum to exactly 100 (see [wholePercentages]), so a
 * legend cannot show a column that adds up to 99.
 */
data class ChartSlice(
    val label: String,
    val amountPaise: Long,
    val slot: Int?,
    val sharePercent: Int
) {
    /**
     * True for the uncategorised slice — money with no category at all, drawn as the hatch.
     *
     * This is deliberately *not* true of the "Other" slice. Both sit at the bottom of the chart and
     * both are folds, but only one of them means the user has something to fix, and the hatch is
     * what says so.
     */
    val isUnnamed: Boolean get() = slot == null
}

/**
 * The slot a category would take if nothing else were competing for it.
 *
 * Category ids are Room autoincrement Longs starting at 1, so the offset is what puts the first
 * category a user creates on the first colour — the amber the artboard leads with. Without it slot
 * 0 would only ever be reached by the seventh category, and a typical account with four or five
 * would never see the palette's first colour at all.
 *
 * The first seven categories land on seven distinct slots. Beyond that two categories can prefer
 * the same one; [buildCategorySlices] resolves that within a single chart, which is the only place
 * a collision is visible.
 */
fun preferredSlot(categoryId: Long): Int {
    val slot = ((categoryId - 1L) % CHART_SLOT_COUNT).toInt()
    return if (slot < 0) slot + CHART_SLOT_COUNT else slot
}

/**
 * Turns a period's per-category totals into the slices a chart draws.
 *
 * Everything the design asks for happens here, in one place, so the donut, the ranked list and the
 * stacked bar cannot disagree about what the period contained:
 *
 * - Non-positive amounts are dropped. A category with nothing in it is not a zero-width slice.
 * - Named categories sort by amount descending; ties break on category id so two runs of the same
 *   data cannot swap two rows.
 * - Named categories past [maxNamed] fold into one [otherLabel] slice. It is still categorised
 *   money, so it takes a real colour.
 * - Rows with no category at all fold into one [uncategorisedLabel] slice, drawn as the hatch and
 *   sorted last.
 * - Slots come from [preferredSlot], with a collision pushed to the next free slot. Two slices in
 *   one chart never share a colour.
 * - Shares are whole percentages summing to 100.
 *
 * **The two folds are separate on purpose.** Merging them — which this function used to do — labels
 * a tail of perfectly well-mapped categories "Uncategorised" and hatches it, telling the user to go
 * fix mapping that is already correct. An account with six categories and nothing unmapped saw its
 * six smallest categories reported as unnamed money. `design/DashboardSpec.dc.html` W3 has it as two
 * things throughout ("top 5, with Uncategorized forced to the bottom regardless of size"), and W4's
 * nudge reads the unmapped total directly — so the merged version also contradicted the banner
 * sitting inches above it, which stayed silent while the donut claimed 16% was unnamed.
 *
 * Either fold is emitted only when it holds something. A period where every rupee has a name shows
 * no hatch at all, rather than a zero-width one the legend would still list.
 */
fun buildCategorySlices(
    amounts: List<CategoryAmount>,
    uncategorisedLabel: String,
    otherLabel: String,
    maxNamed: Int = DEFAULT_MAX_NAMED_SLICES
): List<ChartSlice> {
    val positive = amounts.filter { it.amountPaise > 0L }
    if (positive.isEmpty()) return emptyList()

    val cap = maxNamed.coerceIn(0, CHART_SLOT_COUNT)

    val named = positive
        .filter { it.categoryId != null }
        .sortedWith(compareByDescending<CategoryAmount> { it.amountPaise }.thenBy { it.categoryId })

    val kept = named.take(cap)
    val otherPaise = named.drop(cap).sumOf { it.amountPaise }
    val unnamedPaise = positive.filter { it.categoryId == null }.sumOf { it.amountPaise }

    val taken = mutableSetOf<Int>()
    val slots = kept.map { amount ->
        var slot = preferredSlot(amount.categoryId!!)
        while (!taken.add(slot)) {
            slot = (slot + 1) % CHART_SLOT_COUNT
        }
        slot
    }

    // "Other" is real spend in real categories, so it gets a real colour rather than the hatch. The
    // lowest free slot keeps it deterministic without letting it steal a colour a kept category
    // prefers. At the default cap of five there are always two spare; only a caller asking for all
    // seven named slices can exhaust the palette, and then the hatch is the honest fallback — it is
    // still not a category, whatever it is drawn as.
    val otherSlot = (0 until CHART_SLOT_COUNT).firstOrNull { it !in taken }

    val folds = buildList {
        if (otherPaise > 0L) add(Triple(otherLabel, otherPaise, otherSlot))
        if (unnamedPaise > 0L) add(Triple(uncategorisedLabel, unnamedPaise, null))
    }

    val labels = kept.map { it.label } + folds.map { it.first }
    val values = kept.map { it.amountPaise } + folds.map { it.second }
    val assigned = slots + folds.map { it.third }

    val percentages = wholePercentages(values)

    return values.indices.map { i ->
        ChartSlice(
            label = labels[i],
            amountPaise = values[i],
            slot = assigned[i],
            sharePercent = percentages[i]
        )
    }
}

/**
 * Whole-number percentages that sum to exactly 100.
 *
 * Rounding each share independently gives columns that read 33 / 33 / 33, so this uses the
 * largest-remainder method instead: floor everything, then hand the leftover points to the values
 * that lost the most in the flooring. A total of zero yields all zeroes rather than dividing by it.
 *
 * A genuinely tiny slice can still floor to 0 here. That is correct — rendering it as "<1%" rather
 * than "0%" is a formatting decision, and formatting is not this module's job.
 */
internal fun wholePercentages(values: List<Long>): List<Int> {
    val total = values.sum()
    if (total <= 0L) return List(values.size) { 0 }

    val exact = values.map { it * 100.0 / total }
    val result = exact.map { floor(it).toInt() }.toMutableList()

    var leftover = 100 - result.sum()
    if (leftover <= 0) return result

    val byRemainder = exact.indices.sortedByDescending { exact[it] - floor(exact[it]) }
    var i = 0
    while (leftover > 0) {
        result[byRemainder[i % byRemainder.size]]++
        leftover--
        i++
    }
    return result
}
