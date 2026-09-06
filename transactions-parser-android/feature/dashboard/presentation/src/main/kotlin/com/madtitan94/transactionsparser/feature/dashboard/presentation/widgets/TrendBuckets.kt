package com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets

import com.madtitan94.transactionsparser.core.domain.model.DayTotal
import java.time.LocalDate

private const val MILLIS_PER_DAY = 86_400_000L

/** Fewer than three points is not a trend, it is two dots and a straight line between them. */
const val MIN_TREND_BUCKETS = 3

/** Where daily buckets stop being legible on a phone-width chart. Two months of days. */
private const val MAX_DAILY_BUCKETS = 62

/** Where weekly buckets stop being legible. Roughly a year and a half of weeks. */
private const val MAX_WEEKLY_BUCKETS = 80

enum class TrendBucketSize { DAY, WEEK, MONTH }

/**
 * The trend line's points, zero-filled and in chronological order.
 *
 * Zero-filling is the whole job. The day aggregate only returns days that have rows, so plotting it
 * raw would draw a straight line from the 3rd to the 19th and hide the two quiet weeks between them
 * — the chart would show spending as steady precisely when it was not. Every bucket in the range
 * gets a point, including the ones worth nothing.
 *
 * [averagePaise] is the mean across *all* buckets rather than across the ones with spending in them.
 * "₹1.4k avg/day" has to mean what a reader assumes it means: the period's total divided by the
 * period's length, not divided by the number of days that happened to be busy.
 */
data class TrendSeries(
    val values: List<Long>,
    val startMillis: List<Long>,
    val bucketSize: TrendBucketSize,
    val averagePaise: Long
) {
    /** Below three points the caller hides the widget rather than drawing a line with no shape. */
    val isDrawable: Boolean get() = values.size >= MIN_TREND_BUCKETS
}

/**
 * Buckets a range's day totals for the trend chart.
 *
 * [fromMillis]/[toMillisExclusive] are the range's own bounds when it has them. All time has none,
 * so it falls back to the span the data itself covers — the alternative would be a chart running
 * from 1970, which is a true rendering of an unbounded range and a useless one.
 *
 * The bucket size follows the span rather than the user's chosen range name, so a custom range of
 * four months buckets the same way "this quarter" would.
 */
fun buildTrendSeries(
    dayTotals: List<DayTotal>,
    fromMillis: Long?,
    toMillisExclusive: Long?
): TrendSeries {
    // All time arrives as DateRange.AllTime's sentinels rather than as nulls when a caller passes
    // the resolved window straight through. Treating them as "no bound" here means the chart spans
    // the data instead of overflowing on a floor of Long.MAX_VALUE.
    val from = fromMillis?.takeUnless { it == Long.MIN_VALUE }
    val to = toMillisExclusive?.takeUnless { it == Long.MAX_VALUE }

    if (dayTotals.isEmpty() && (from == null || to == null)) {
        return TrendSeries(emptyList(), emptyList(), TrendBucketSize.DAY, 0L)
    }

    val spentByDay = dayTotals.associate { floorToDay(it.startMillis) to it.debitPaise }

    val firstDay = from?.let { floorToDay(it) } ?: spentByDay.keys.min()
    // The upper bound is exclusive, so the last day inside it is one millisecond earlier. Flooring
    // the bound itself would add an empty bucket for the first day of the *next* period.
    val lastDay = to?.let { floorToDay(it - 1) } ?: spentByDay.keys.max()
    if (lastDay < firstDay) return TrendSeries(emptyList(), emptyList(), TrendBucketSize.DAY, 0L)

    val dayCount = ((lastDay - firstDay) / MILLIS_PER_DAY).toInt() + 1
    val size = when {
        dayCount <= MAX_DAILY_BUCKETS -> TrendBucketSize.DAY
        dayCount <= MAX_WEEKLY_BUCKETS * 7 -> TrendBucketSize.WEEK
        else -> TrendBucketSize.MONTH
    }

    val buckets = linkedMapOf<Long, Long>()
    var cursor = firstDay
    while (cursor <= lastDay) {
        val key = bucketStart(cursor, size)
        buckets[key] = (buckets[key] ?: 0L) + (spentByDay[cursor] ?: 0L)
        cursor += MILLIS_PER_DAY
    }

    val values = buckets.values.toList()
    val average = if (values.isEmpty()) 0L else values.sum() / values.size

    return TrendSeries(
        values = values,
        startMillis = buckets.keys.toList(),
        bucketSize = size,
        averagePaise = average
    )
}

private fun floorToDay(millis: Long): Long = Math.floorDiv(millis, MILLIS_PER_DAY) * MILLIS_PER_DAY

/**
 * The bucket a day belongs to, named by its first day.
 *
 * Read back against UTC like every other statement timestamp in the app: these millis are
 * printed wall clock stored as-if-UTC, so a local-zone reading would move the last day of a month
 * into the next one for anyone east of Greenwich.
 */
private fun bucketStart(dayMillis: Long, size: TrendBucketSize): Long = when (size) {
    TrendBucketSize.DAY -> dayMillis
    TrendBucketSize.WEEK -> {
        val date = LocalDate.ofEpochDay(Math.floorDiv(dayMillis, MILLIS_PER_DAY))
        date.minusDays((date.dayOfWeek.value - 1).toLong()).toEpochDay() * MILLIS_PER_DAY
    }
    TrendBucketSize.MONTH -> {
        val date = LocalDate.ofEpochDay(Math.floorDiv(dayMillis, MILLIS_PER_DAY))
        date.withDayOfMonth(1).toEpochDay() * MILLIS_PER_DAY
    }
}
