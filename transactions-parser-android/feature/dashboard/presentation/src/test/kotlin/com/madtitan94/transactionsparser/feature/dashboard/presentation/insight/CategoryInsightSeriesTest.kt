package com.madtitan94.transactionsparser.feature.dashboard.presentation.insight

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.madtitan94.transactionsparser.core.domain.model.PeriodTotal
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone
import org.junit.jupiter.api.Test

/**
 * The three bucketings the insight cards read.
 *
 * The weekday cases are the ones the phase's Verify block names, and they are written to fail if
 * anyone ever "fixes" the UTC reading into a local-zone one: the fixture times sit near midnight,
 * which is exactly where a zone conversion moves a row onto the wrong day.
 */
class CategoryInsightSeriesTest {

    private fun utc(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun bucket(year: Int, month: Int, day: Int, paise: Long, count: Int = 1) = PeriodTotal(
        startMillis = utc(year, month, day),
        countedTotalPaise = paise,
        countedCount = count
    )

    // ---- Day series -------------------------------------------------------------------------

    @Test
    fun `a quiet day still gets a bar`() {
        val series = buildDaySeries(
            dayTotals = listOf(bucket(2026, 9, 1, 500_00), bucket(2026, 9, 5, 300_00)),
            fromMillis = utc(2026, 9, 1),
            toMillisExclusive = utc(2026, 9, 6)
        )
        // Five days, two with spending. Without zero-filling the chart would put the 1st next to
        // the 5th and show three quiet days as continuous spending.
        assertThat(series.points.size).isEqualTo(5)
        assertThat(series.values).isEqualTo(listOf(500_00L, 0L, 0L, 0L, 300_00L))
    }

    @Test
    fun `the trough is the smallest spending day, not the smallest bar`() {
        val series = buildDaySeries(
            dayTotals = listOf(bucket(2026, 9, 1, 500_00), bucket(2026, 9, 5, 120_00)),
            fromMillis = utc(2026, 9, 1),
            toMillisExclusive = utc(2026, 9, 6)
        )
        // The 2nd, 3rd and 4th are zero-filled and are smaller than either. Reporting one of them
        // would answer "you spent least on Wednesday: ₹0", which is trivially true and not the
        // question the card asks.
        assertThat(series.trough?.totalPaise).isEqualTo(120_00L)
        assertThat(series.peak?.totalPaise).isEqualTo(500_00L)
    }

    @Test
    fun `a period with no spending has no peak and no trough`() {
        val series = buildDaySeries(
            dayTotals = emptyList(),
            fromMillis = utc(2026, 9, 1),
            toMillisExclusive = utc(2026, 9, 4)
        )
        assertThat(series.points.size).isEqualTo(3)
        assertThat(series.peak).isNull()
        assertThat(series.trough).isNull()
    }

    @Test
    fun `all time spans the data rather than starting in 1970`() {
        val series = buildDaySeries(
            dayTotals = listOf(bucket(2026, 6, 10, 100_00), bucket(2026, 6, 12, 200_00)),
            fromMillis = Long.MIN_VALUE,
            toMillisExclusive = Long.MAX_VALUE
        )
        // The sentinels are treated as "no bound", so the chart covers the 10th to the 12th rather
        // than every day since the epoch.
        assertThat(series.points.size).isEqualTo(3)
    }

    @Test
    fun `the exclusive upper bound does not add a bar for the next period`() {
        val series = buildDaySeries(
            dayTotals = listOf(bucket(2026, 9, 1, 100_00)),
            fromMillis = utc(2026, 9, 1),
            toMillisExclusive = utc(2026, 10, 1)
        )
        // September has thirty days. Flooring the exclusive bound itself would put the 1st of
        // October on the end as an empty thirty-first bar.
        assertThat(series.points.size).isEqualTo(30)
    }

    // ---- Weekday series ---------------------------------------------------------------------

    @Test
    fun `weekdays are bucketed Monday first`() {
        // 2026-09-07 is a Monday.
        val series = buildWeekdaySeries(
            listOf(
                bucket(2026, 9, 7, 100_00),
                bucket(2026, 9, 9, 300_00),
                bucket(2026, 9, 13, 700_00)
            )
        )
        assertThat(series.values).isEqualTo(
            listOf(100_00L, 0L, 300_00L, 0L, 0L, 0L, 700_00L)
        )
    }

    @Test
    fun `a weekday is read in UTC, not in the device zone`() {
        // The whole point of the case. Statement timestamps are the PDF's printed wall clock stored
        // as-if-UTC, and a day bucket starts at UTC midnight. Read in Kolkata (UTC+5:30) that
        // instant is still the same date, but read in Los Angeles (UTC-7) it is the *previous* day
        // — so a Sunday's spending would be reported as Saturday's for every user in the Americas.
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            // 2026-09-13 is a Sunday: index 6 Mon-first.
            val series = buildWeekdaySeries(listOf(bucket(2026, 9, 13, 900_00)))
            assertThat(series.values[6]).isEqualTo(900_00L)
            assertThat(series.values[5]).isEqualTo(0L)
            assertThat(series.peakIndex).isEqualTo(6)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `the weekend share is computed, not assumed`() {
        // 2026-09-12 is a Saturday and 2026-09-13 a Sunday; 2026-09-09 a Wednesday.
        val series = buildWeekdaySeries(
            listOf(
                bucket(2026, 9, 9, 200_00),
                bucket(2026, 9, 12, 500_00),
                bucket(2026, 9, 13, 300_00)
            )
        )
        assertThat(series.weekendPercent).isEqualTo(80)
    }

    @Test
    fun `an empty period is not drawable and claims no weekend share`() {
        val series = buildWeekdaySeries(emptyList())
        assertThat(series.isDrawable).isFalse()
        assertThat(series.weekendPercent).isNull()
        assertThat(series.values.size).isEqualTo(DAYS_IN_WEEK)
    }

    // ---- Month series -----------------------------------------------------------------------

    @Test
    fun `the trend window always produces its full run of months`() {
        val series = buildMonthSeries(
            monthTotals = listOf(bucket(2026, 9, 1, 400_00)),
            today = LocalDate.of(2026, 9, 7),
            monthsBack = 6
        )
        // Six bars, five of them empty. A category the user only started spending on this month
        // reads as a rise from nothing, which is what happened.
        assertThat(series.points.size).isEqualTo(6)
        assertThat(series.values.last()).isEqualTo(400_00L)
        assertThat(series.values.first()).isEqualTo(0L)
    }

    @Test
    fun `a rising run is counted in steps at the end of the window`() {
        val series = buildMonthSeries(
            monthTotals = listOf(
                bucket(2026, 7, 1, 100_00),
                bucket(2026, 8, 1, 200_00),
                bucket(2026, 9, 1, 300_00)
            ),
            today = LocalDate.of(2026, 9, 7),
            monthsBack = 3
        )
        // Three months, each above the one before: two steps. The card adds the month the streak
        // started from to say "three months in a row".
        assertThat(series.risingStreak).isEqualTo(2)
        assertThat(series.fallingStreak).isEqualTo(0)
    }

    @Test
    fun `a streak is broken by the most recent month, not by an older one`() {
        val series = buildMonthSeries(
            monthTotals = listOf(
                bucket(2026, 7, 1, 100_00),
                bucket(2026, 8, 1, 400_00),
                bucket(2026, 9, 1, 200_00)
            ),
            today = LocalDate.of(2026, 9, 7),
            monthsBack = 3
        )
        // August rose, September fell. Reporting the rise would describe a month the user has
        // already left behind.
        assertThat(series.risingStreak).isEqualTo(0)
        assertThat(series.fallingStreak).isEqualTo(1)
    }

    @Test
    fun `a single month is not a trend`() {
        val series = buildMonthSeries(
            monthTotals = listOf(bucket(2026, 9, 1, 400_00)),
            today = LocalDate.of(2026, 9, 7),
            monthsBack = 1
        )
        assertThat(series.isDrawable).isFalse()
    }

    @Test
    fun `the window ends with the month in progress`() {
        val series = buildMonthSeries(
            monthTotals = listOf(bucket(2026, 9, 1, 400_00)),
            today = LocalDate.of(2026, 9, 7),
            monthsBack = 3
        )
        // July, August, September — not June, July, August. A window that stopped at the last
        // *complete* month would never show the user what they are spending right now.
        assertThat(series.points.first().startMillis).isEqualTo(utc(2026, 7, 1))
        assertThat(series.points.last().startMillis).isEqualTo(utc(2026, 9, 1))
        assertThat(series.isDrawable).isTrue()
    }
}
