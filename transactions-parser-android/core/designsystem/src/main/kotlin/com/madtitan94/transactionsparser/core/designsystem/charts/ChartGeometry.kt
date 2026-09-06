package com.madtitan94.transactionsparser.core.designsystem.charts

/**
 * The arithmetic behind each chart, kept out of the composables that draw it.
 *
 * A `Canvas` block cannot be unit-tested without a device, so anything that could be wrong in an
 * interesting way — an arc that does not close the ring, a bar scaled against the wrong maximum, a
 * division by an empty period — lives here as a plain function instead. The composables are then
 * thin enough that reading them is enough to trust them.
 */

/**
 * Where the first slice starts: twelve o'clock, from `design/DashboardSpec.dc.html` W7.
 *
 * Compose measures sweep angles clockwise from three o'clock, so twelve o'clock is -90.
 */
internal const val DONUT_START_DEGREES = -90f

/** One slice of the ring, in the angles `drawArc` wants. */
internal data class DonutArc(val startDegrees: Float, val sweepDegrees: Float)

/**
 * Splits the ring between [weights], leaving [gapDegrees] of the card showing between slices.
 *
 * The gap is a deviation from W7, which specifies no gaps. It is here because two adjacent slots of
 * the series can be close enough in value that a shared edge reads as one slice, and the app's own
 * segmented total bar already separates its segments the same way (`AppDimens.barSegmentGap`). Pass
 * `0f` for the spec's original look.
 *
 * A single slice gets no gap — a notch in an otherwise complete ring would read as missing data
 * rather than as a separator. If the gaps would consume the whole circle they are dropped for the
 * same reason.
 */
internal fun donutArcs(weights: List<Long>, gapDegrees: Float): List<DonutArc> {
    val positive = weights.filter { it > 0L }
    val total = positive.sum()
    if (total <= 0L) return emptyList()

    val gap = if (weights.size < 2 || gapDegrees * weights.size >= 360f) 0f else gapDegrees
    val available = 360f - gap * weights.size

    var cursor = DONUT_START_DEGREES
    return weights.map { weight ->
        val sweep = if (weight > 0L) available * (weight.toFloat() / total) else 0f
        val arc = DonutArc(startDegrees = cursor, sweepDegrees = sweep)
        cursor += sweep + gap
        arc
    }
}

/**
 * Heights for the trend line, as fractions of the plot, measured from a zero baseline.
 *
 * Zero rather than the smallest value in the range: the widget fills the area under the line, and
 * an area whose floor is the cheapest day would make a quiet month look like an expensive one. It
 * also gives W2's specified empty behaviour for free — a period of all zeroes is a flat line along
 * the bottom, not an undefined one.
 */
internal fun trendFractions(values: List<Long>): List<Float> {
    val max = values.maxOrNull() ?: 0L
    if (max <= 0L) return List(values.size) { 0f }
    return values.map { (it.toFloat() / max).coerceIn(0f, 1f) }
}

/**
 * Heights for the paired in/out bars, both scaled against the largest single value across *both*
 * series (W6).
 *
 * Scaling each series against its own maximum would draw a ₹500 income week the same height as a
 * ₹50,000 spending week, which is the one comparison the widget exists to make.
 */
internal fun pairedBarFractions(
    inValues: List<Long>,
    outValues: List<Long>
): List<Pair<Float, Float>> {
    val buckets = minOf(inValues.size, outValues.size)
    val max = ((inValues.take(buckets) + outValues.take(buckets)).maxOrNull() ?: 0L)
    if (max <= 0L) return List(buckets) { 0f to 0f }
    return (0 until buckets).map { i ->
        (inValues[i].toFloat() / max).coerceIn(0f, 1f) to
            (outValues[i].toFloat() / max).coerceIn(0f, 1f)
    }
}

/**
 * How full a ranked row's track is, against the largest row rather than the total (W3).
 *
 * Against the total, a period split evenly across five categories would draw five tracks each a
 * fifth full and the ranking would be invisible. Against the largest, the top row is always full
 * and the rest are read relative to it, which is what the eye is actually being asked to compare.
 */
fun trackFraction(value: Long, largest: Long): Float {
    if (largest <= 0L || value <= 0L) return 0f
    return (value.toFloat() / largest).coerceIn(0f, 1f)
}
