package com.madtitan94.transactionsparser.feature.dashboard.domain

import com.madtitan94.transactionsparser.core.domain.model.DateRange
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * The period every widget on every dashboard is asking about.
 *
 * One range is hoisted above the whole dashboard rather than owned per widget, so a donut and the
 * trend line beside it cannot disagree about which month is on screen.
 *
 * The subtlety here is which clock decides what "this month" means. Statement rows carry the PDF's
 * printed wall time stored as-if-UTC, so a range boundary has to be built with [ZoneOffset.UTC] or
 * every user outside UTC would see the first and last days of a month fall into the neighbouring
 * one. But the *calendar date* the user means by "today" comes from the device, not from UTC —
 * someone in India opening the app at 02:00 on the 1st means the 1st, not the 31st. So the calendar
 * arithmetic is done on a plain [LocalDate] supplied by the caller, and only the final conversion to
 * milliseconds is pinned to UTC. That pairing is deliberate and is not a timezone conversion: no
 * instant is ever shifted, the day boundary is simply named by the local calendar and measured in
 * the storage clock.
 */
sealed interface DashboardRange {

    /** Stable key for preferences. Unknown keys on read fall back rather than throwing. */
    val key: String

    data object Today : DashboardRange {
        override val key = "TODAY"
    }

    data object ThisWeek : DashboardRange {
        override val key = "THIS_WEEK"
    }

    data object ThisMonth : DashboardRange {
        override val key = "THIS_MONTH"
    }

    data object LastMonth : DashboardRange {
        override val key = "LAST_MONTH"
    }

    data object AllTime : DashboardRange {
        override val key = "ALL_TIME"
    }

    /**
     * A user-picked span, inclusive of both dates as the picker presents them.
     *
     * The picker says "1 Aug to 26 Aug" and the user means both days included, so the conversion to
     * a half-open [DateRange] adds a day to the upper bound. Storing it inclusive keeps the value
     * the user chose recoverable for the chip label and the picker's re-open state; converting late
     * keeps the off-by-one in exactly one place.
     */
    data class Custom(
        val fromDate: LocalDate,
        val toDateInclusive: LocalDate
    ) : DashboardRange {
        override val key = "CUSTOM"
    }

    companion object {
        /**
         * What a fresh account sees, and what an unreadable stored preference falls back to.
         *
         * This month rather than All time: a dashboard is a report on a period, and the first
         * statement a user imports covers one month, so All time would make the range chip look
         * broken — every widget identical whichever period they pick.
         */
        val Default: DashboardRange = ThisMonth

        /** The chip row's options, in the order they are offered. [Custom] is reached by picking. */
        val Selectable: List<DashboardRange> = listOf(Today, ThisWeek, ThisMonth, LastMonth, AllTime)
    }
}

/**
 * Resolves this range against the calendar date the user is living in.
 *
 * [today] is passed rather than read from a clock so the whole thing is a pure function: the
 * ViewModel supplies `LocalDate.now()` and a test supplies a fixed date, and neither has to reason
 * about what the machine's zone happens to be.
 *
 * Weeks start on Monday. That is the ISO week and matches what the trend chart's day buckets
 * produce; it is a choice rather than a fact, and it is the only place it is made.
 */
fun DashboardRange.resolve(today: LocalDate): DateRange = when (this) {
    DashboardRange.Today -> daysFrom(today, days = 1)
    DashboardRange.ThisWeek -> daysFrom(today.minusDays((today.dayOfWeek.value - 1).toLong()), days = 7)
    DashboardRange.ThisMonth -> {
        val first = today.withDayOfMonth(1)
        DateRange(first.asUtcMillis(), first.plusMonths(1).asUtcMillis())
    }
    DashboardRange.LastMonth -> {
        val first = today.withDayOfMonth(1).minusMonths(1)
        DateRange(first.asUtcMillis(), first.plusMonths(1).asUtcMillis())
    }
    DashboardRange.AllTime -> DateRange.AllTime
    is DashboardRange.Custom -> DateRange(
        fromMillis = fromDate.asUtcMillis(),
        toMillisExclusive = toDateInclusive.plusDays(1).asUtcMillis()
    )
}

/**
 * The equal-length window immediately before this one, for the hero card's "than last month" line.
 *
 * Calendar months are not equal-length, so [DashboardRange.ThisMonth] steps back a whole month
 * rather than 30 days — comparing a 31-day August against 31 days ending in July would be arithmetic
 * nobody could check against their own statement. Every other range steps back by its own duration.
 *
 * All time has no "before", and neither does anything unbounded, so this returns null and the hero
 * card drops the comparison line rather than inventing a baseline of zero.
 */
fun DashboardRange.previous(today: LocalDate): DateRange? = when (this) {
    DashboardRange.AllTime -> null
    DashboardRange.ThisMonth -> DashboardRange.LastMonth.resolve(today)
    DashboardRange.LastMonth -> {
        val first = today.withDayOfMonth(1).minusMonths(2)
        DateRange(first.asUtcMillis(), first.plusMonths(1).asUtcMillis())
    }
    else -> {
        val current = resolve(today)
        val span = current.toMillisExclusive - current.fromMillis
        DateRange(current.fromMillis - span, current.fromMillis)
    }
}

/**
 * The window an anomaly callout measures this range's charges against.
 *
 * [months] whole months ending exactly where the viewed period begins — never overlapping it. The
 * non-overlap is the whole point: a charge allowed into its own baseline raises the average it is
 * being judged by, and one large enough to matter would hide itself completely.
 *
 * Null for All time, and that is the honest answer rather than a missing case. All time has no
 * "before" — every charge the account holds is already inside the period being examined — so there
 * is no habit left over to call anything unusual against, and the callout is dropped instead of
 * being computed from a baseline that would have to include the charges themselves.
 *
 * The arithmetic is done on a [LocalDate] and pinned to UTC only at the end, exactly as [resolve]
 * does, for the reason given at the top of this file.
 */
fun DashboardRange.anomalyBaseline(today: LocalDate, months: Long): DateRange? {
    if (this == DashboardRange.AllTime) return null
    val windowStart = resolve(today).fromMillis
    val startDate = LocalDate.ofEpochDay(Math.floorDiv(windowStart, MILLIS_PER_DAY))
    return DateRange(
        fromMillis = startDate.minusMonths(months).asUtcMillis(),
        toMillisExclusive = windowStart
    )
}

/**
 * How many days the range covers, or null when it is unbounded.
 *
 * The trend chart uses this to decide whether it is drawing days or weeks, and the hero card uses it
 * to decide whether a comparison is worth showing at all.
 */
fun DashboardRange.lengthInDays(today: LocalDate): Int? = when (this) {
    DashboardRange.AllTime -> null
    else -> {
        val range = resolve(today)
        ChronoUnit.DAYS.between(
            LocalDate.ofEpochDay(Math.floorDiv(range.fromMillis, MILLIS_PER_DAY)),
            LocalDate.ofEpochDay(Math.floorDiv(range.toMillisExclusive, MILLIS_PER_DAY))
        ).toInt()
    }
}

private const val MILLIS_PER_DAY = 86_400_000L

private fun daysFrom(start: LocalDate, days: Long) =
    DateRange(start.asUtcMillis(), start.plusDays(days).asUtcMillis())

/** Midnight of this calendar date, measured in the clock the rows are stored in. */
private fun LocalDate.asUtcMillis(): Long = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
