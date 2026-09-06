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
 * Which slice a tap at [angleDegrees] landed on, or null when there is nothing to hit.
 *
 * Each slice claims everything from its own start to the next slice's start, so the gap between two
 * slices belongs to the one before it. A 2dp gap is a couple of degrees wide and a tap that fell in
 * one would otherwise do nothing at all, which reads as a broken chart rather than as a near miss.
 *
 * The caller has already decided the tap was on the ring; this only answers *where* around it.
 */
internal fun sliceIndexAt(arcs: List<DonutArc>, angleDegrees: Float): Int? {
    if (arcs.isEmpty()) return null

    val origin = arcs.first().startDegrees
    val relative = (((angleDegrees - origin) % 360f) + 360f) % 360f
    val starts = arcs.map { (((it.startDegrees - origin) % 360f) + 360f) % 360f }

    // The last slice whose start is at or before the tap. Slices are laid out in order, so this is
    // the one whose claim contains it.
    return arcs.indices.lastOrNull { starts[it] <= relative }
}

/**
 * Which bucket of a trend line a touch at [x] is nearest to.
 *
 * Nearest rather than "the bucket whose segment was touched": the line is a polyline between
 * points, and the thing being read out is a day's total, not a position along a segment. Snapping
 * to the point means the marker always lands on real data.
 *
 * Fewer than two buckets has nothing to scrub — that matches [trendFractions], which draws nothing
 * below two points either.
 */
internal fun bucketIndexAt(x: Float, width: Float, bucketCount: Int): Int? {
    if (bucketCount < 2 || width <= 0f) return null
    val step = width / (bucketCount - 1)
    return (x / step).let { Math.round(it) }.coerceIn(0, bucketCount - 1)
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
