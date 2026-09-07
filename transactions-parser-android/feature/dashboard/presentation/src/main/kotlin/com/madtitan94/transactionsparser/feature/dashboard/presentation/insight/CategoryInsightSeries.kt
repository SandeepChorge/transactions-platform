package com.madtitan94.transactionsparser.feature.dashboard.presentation.insight

import com.madtitan94.transactionsparser.core.domain.model.PeriodTotal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

private const val MILLIS_PER_DAY = 86_400_000L

/** Mon–Sun. Fixed, because the weekday card's whole point is that the buckets never move. */
const val DAYS_IN_WEEK = 7

/** Below this there is no shape to read, only a couple of bars. The card hides itself instead. */
private const val MIN_DRAWABLE_BUCKETS = 2

/**
 * One bucket of a category insight chart, carrying the instant it started so a callout can name it.
 *
 * The label is not built here: this file is pure Kotlin with no access to resources or the device
 * locale, and formatting a date is the screen's job.
 */
data class InsightPoint(
    val startMillis: Long,
    val totalPaise: Long
)

/**
 * The spend-by-day card's series, zero-filled across the whole range.
 *
 * Zero-filling is the point, the same as it is for the dashboard's trend line: the day aggregate
 * only returns days that have rows, so drawing it raw would put the 3rd next to the 19th and show
 * a fortnight of quiet as continuous spending.
 *
 * [peak] and [trough] are what the card exists to answer — "which day did I spend most, and least".
 */
data class DaySeries(
    val points: List<InsightPoint>,
    val peak: InsightPoint?,
    val trough: InsightPoint?
) {
    val values: List<Long> get() = points.map { it.totalPaise }
    val isDrawable: Boolean get() = points.size >= MIN_DRAWABLE_BUCKETS

    /** Index of [peak] in [points], for the chart to colour. Null when there is no spending at all. */
    val peakIndex: Int? get() = peak?.let { p -> points.indexOfFirst { it.startMillis == p.startMillis } }
    val troughIndex: Int? get() = trough?.let { t -> points.indexOfFirst { it.startMillis == t.startMillis } }
}

/**
 * The by-day-of-week card's series: the same rows as [DaySeries], re-bucketed Mon–Sun.
 *
 * Always seven entries, in [DayOfWeek] order, including the ones worth nothing — a week with no
 * Sunday spending has a Sunday of zero, not six bars and a gap where the seventh should be.
 */
data class WeekdaySeries(
    /** Mon-first, seven long. Index 0 is Monday, matching `DayOfWeek.MONDAY.value - 1`. */
    val values: List<Long>,
    val peakIndex: Int?,
    /**
     * Saturday and Sunday's share of the period, 0-100, or null when nothing was spent.
     *
     * The design's callout — "you spend the most on weekends" — is a claim, so it is computed rather
     * than assumed: the screen only makes it when this actually clears half the total.
     */
    val weekendPercent: Int?
) {
    val total: Long get() = values.sum()
    val isDrawable: Boolean get() = total > 0L
}

/**
 * The trend card's series: months, oldest first, zero-filled across the whole window.
 *
 * Deliberately built over a months-back window rather than the dashboard's active filter. "How is
 * this going over time" is a longer question than any single period answers, and a trend redrawn
 * every time the user changes the filter would answer a different question each time.
 */
data class MonthSeries(
    val points: List<InsightPoint>,
    /**
     * How many consecutive months at the end of the window rose over the one before.
     *
     * Zero when the last month fell or held level. It is the callout's whole content — "trending up
     * for three straight months" is a fact a reader can act on, where a line's slope is a guess.
     */
    val risingStreak: Int,
    /** The mirror of [risingStreak]: consecutive falling months at the end of the window. */
    val fallingStreak: Int
) {
    val values: List<Long> get() = points.map { it.totalPaise }
    val isDrawable: Boolean get() = points.size >= MIN_DRAWABLE_BUCKETS
}

/**
 * Zero-fills a category's day totals across `[fromMillis, toMillisExclusive)`.
 *
 * All time arrives as [com.madtitan94.transactionsparser.core.domain.model.DateRange.AllTime]'s
 * sentinels rather than as nulls, so both bounds fall back to the span the data itself covers — the
 * alternative is a chart that starts in 1970, which is a true rendering of an unbounded range and a
 * useless one.
 *
 * **[trough] is the smallest day that had spending, not the smallest bucket.** Once the series is
 * zero-filled the smallest bucket is almost always a zero, and "you spent least on Tuesday: ₹0" is
 * both trivially true and not the question. A day the user spent nothing on is a day they did not
 * spend, not their cheapest day.
 */
fun buildDaySeries(
    dayTotals: List<PeriodTotal>,
    fromMillis: Long,
    toMillisExclusive: Long
): DaySeries {
    val from = fromMillis.takeUnless { it == Long.MIN_VALUE }
    val to = toMillisExclusive.takeUnless { it == Long.MAX_VALUE }

    val spentByDay = dayTotals.associate { floorToDay(it.startMillis) to it.countedTotalPaise }
    if (spentByDay.isEmpty() && (from == null || to == null)) {
        return DaySeries(emptyList(), null, null)
    }

    val firstDay = from?.let { floorToDay(it) } ?: spentByDay.keys.min()
    // The upper bound is exclusive, so the last day inside it is one millisecond earlier. Flooring
    // the bound itself would add an empty bucket for the first day of the *next* period.
    val lastDay = to?.let { floorToDay(it - 1) } ?: spentByDay.keys.max()
    if (lastDay < firstDay) return DaySeries(emptyList(), null, null)

    val points = buildList {
        var cursor = firstDay
        while (cursor <= lastDay) {
            add(InsightPoint(cursor, spentByDay[cursor] ?: 0L))
            cursor += MILLIS_PER_DAY
        }
    }

    val spending = points.filter { it.totalPaise > 0L }
    return DaySeries(
        points = points,
        peak = spending.maxByOrNull { it.totalPaise },
        trough = spending.minByOrNull { it.totalPaise }
    )
}

/**
 * Re-buckets the same day totals Mon–Sun.
 *
 * **The weekday is read against [ZoneOffset.UTC], and that is not a bug to fix.** Statement
 * timestamps are the PDF's printed wall clock stored as-if-UTC, so a UTC reading lands on the day
 * the row displays under everywhere else in the app. Converting "to the user's timezone" first
 * would shift transactions onto the wrong weekday for every user who is not on UTC and quietly
 * wreck the one answer this card exists to give.
 *
 * Built from the day rows rather than from a query of its own so the two cards cannot disagree
 * about what a day held.
 */
fun buildWeekdaySeries(dayTotals: List<PeriodTotal>): WeekdaySeries {
    val totals = LongArray(DAYS_IN_WEEK)
    dayTotals.forEach { day ->
        val date = LocalDate.ofInstant(
            java.time.Instant.ofEpochMilli(day.startMillis),
            ZoneOffset.UTC
        )
        totals[date.dayOfWeek.value - 1] += day.countedTotalPaise
    }

    val values = totals.toList()
    val total = values.sum()
    if (total <= 0L) return WeekdaySeries(values, null, null)

    val weekend = totals[DayOfWeek.SATURDAY.value - 1] + totals[DayOfWeek.SUNDAY.value - 1]
    return WeekdaySeries(
        values = values,
        peakIndex = values.indexOf(values.max()),
        weekendPercent = ((weekend * 100.0) / total).toInt()
    )
}

/**
 * Zero-fills a category's month totals across a months-back window ending with the current month.
 *
 * [monthsBack] months are always produced, so switching 3M → 6M → 1Y changes the width of the chart
 * rather than the number of bars that happen to have data. A category the user only started spending
 * on last month reads as a rise from nothing, which is what happened.
 */
fun buildMonthSeries(
    monthTotals: List<PeriodTotal>,
    today: LocalDate,
    monthsBack: Int
): MonthSeries {
    val spentByMonth = monthTotals.associate { floorToMonth(it.startMillis) to it.countedTotalPaise }
    val thisMonth = YearMonth.from(today)

    val points = (monthsBack - 1 downTo 0).map { back ->
        val month = thisMonth.minusMonths(back.toLong())
        val start = month.atDay(1).toEpochDay() * MILLIS_PER_DAY
        InsightPoint(start, spentByMonth[start] ?: 0L)
    }

    return MonthSeries(
        points = points,
        risingStreak = trailingStreak(points) { previous, current -> current > previous },
        fallingStreak = trailingStreak(points) { previous, current -> current < previous }
    )
}

/**
 * How many month-over-month steps at the end of the series satisfy [rising].
 *
 * Counted in steps rather than in months: three months that each beat the one before is two steps,
 * and the callout says "three months" by adding the month the streak started from. Reporting the
 * step count as a month count would claim one month more than the data shows.
 */
private inline fun trailingStreak(
    points: List<InsightPoint>,
    rising: (previous: Long, current: Long) -> Boolean
): Int {
    var streak = 0
    for (index in points.lastIndex downTo 1) {
        if (!rising(points[index - 1].totalPaise, points[index].totalPaise)) break
        streak++
    }
    return streak
}

private fun floorToDay(millis: Long): Long = Math.floorDiv(millis, MILLIS_PER_DAY) * MILLIS_PER_DAY

/** Matches the SQL bucket, which is `start of month` in UTC. */
private fun floorToMonth(millis: Long): Long =
    LocalDate.ofEpochDay(Math.floorDiv(millis, MILLIS_PER_DAY))
        .withDayOfMonth(1)
        .toEpochDay() * MILLIS_PER_DAY
