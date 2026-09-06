package com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.madtitan94.transactionsparser.core.domain.model.DayTotal
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The bucketing that turns a sparse day aggregate into a line with the right shape.
 *
 * The tests that matter here are the ones about days with *no* rows. The aggregate returns nothing
 * for a quiet day, so a chart that plots it raw draws a straight line across the gap and reports
 * steady spending through a fortnight of none. Every assertion about list size below is really an
 * assertion that the quiet days survived.
 */
class TrendBucketsTest {

    private fun utc(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun day(year: Int, month: Int, dayOfMonth: Int, debitPaise: Long) = DayTotal(
        startMillis = utc(year, month, dayOfMonth),
        debitPaise = debitPaise,
        creditPaise = 0L,
        transactionCount = 1
    )

    @Test
    fun `a quiet day still gets a point`() {
        val series = buildTrendSeries(
            dayTotals = listOf(day(2026, 9, 1, 500_00), day(2026, 9, 5, 300_00)),
            fromMillis = utc(2026, 9, 1),
            toMillisExclusive = utc(2026, 9, 6)
        )
        // Five days in the range, two of them with spending. Without zero-filling this would be a
        // two-point line implying the 2nd–4th were as busy as the 1st.
        assertThat(series.values).isEqualTo(listOf(500_00L, 0L, 0L, 0L, 300_00L))
    }

    @Test
    fun `the last day of the range is included`() {
        val series = buildTrendSeries(
            dayTotals = listOf(day(2026, 9, 30, 100_00)),
            fromMillis = utc(2026, 9, 1),
            toMillisExclusive = utc(2026, 10, 1)
        )
        // Thirty buckets, not thirty-one: the exclusive bound is the 1st of October, which belongs
        // to next month and must not appear as an empty day on the end of this one.
        assertThat(series.values.size).isEqualTo(30)
        assertThat(series.values.last()).isEqualTo(100_00L)
    }

    @Test
    fun `the average is the period's, not the busy days'`() {
        val series = buildTrendSeries(
            dayTotals = listOf(day(2026, 9, 1, 400_00)),
            fromMillis = utc(2026, 9, 1),
            toMillisExclusive = utc(2026, 9, 5)
        )
        // ₹400 across four days is ₹100/day. Dividing by the one day that had a transaction would
        // read "₹400 avg/day" under a chart that visibly shows three days of nothing.
        assertThat(series.averagePaise).isEqualTo(100_00L)
    }

    @Test
    fun `a two month span buckets by day`() {
        val series = buildTrendSeries(
            dayTotals = emptyList(),
            fromMillis = utc(2026, 8, 1),
            toMillisExclusive = utc(2026, 9, 1)
        )
        assertThat(series.bucketSize).isEqualTo(TrendBucketSize.DAY)
        assertThat(series.values.size).isEqualTo(31)
    }

    @Test
    fun `a span past two months buckets by week`() {
        val series = buildTrendSeries(
            dayTotals = listOf(day(2026, 9, 3, 250_00)),
            fromMillis = utc(2026, 6, 1),
            toMillisExclusive = utc(2026, 10, 1)
        )
        assertThat(series.bucketSize).isEqualTo(TrendBucketSize.WEEK)
        // Weeks open on Monday, so the first bucket is the Monday on or before 1 June 2026 — the
        // 1st itself, which is a Monday.
        assertThat(series.startMillis.first()).isEqualTo(utc(2026, 6, 1))
        assertThat(series.values.sum()).isEqualTo(250_00L)
    }

    @Test
    fun `a multi year span buckets by month`() {
        val series = buildTrendSeries(
            dayTotals = listOf(day(2024, 3, 15, 900_00)),
            fromMillis = utc(2024, 1, 1),
            toMillisExclusive = utc(2026, 10, 1)
        )
        assertThat(series.bucketSize).isEqualTo(TrendBucketSize.MONTH)
        assertThat(series.startMillis.first()).isEqualTo(utc(2024, 1, 1))
        // Jan 2024 through Sep 2026 inclusive: two full years plus nine months.
        assertThat(series.values.size).isEqualTo(33)
        assertThat(series.values[2]).isEqualTo(900_00L)
    }

    @Test
    fun `a week bucket sums the days inside it`() {
        val series = buildTrendSeries(
            dayTotals = listOf(day(2026, 6, 1, 100_00), day(2026, 6, 3, 50_00)),
            fromMillis = utc(2026, 6, 1),
            toMillisExclusive = utc(2026, 10, 1)
        )
        assertThat(series.values.first()).isEqualTo(150_00L)
    }

    @Test
    fun `all time spans the data rather than starting at the epoch`() {
        val series = buildTrendSeries(
            dayTotals = listOf(day(2026, 8, 20, 100_00), day(2026, 8, 24, 200_00)),
            fromMillis = Long.MIN_VALUE,
            toMillisExclusive = Long.MAX_VALUE
        )
        // DateRange.AllTime's sentinels arrive here whenever a caller passes the resolved window
        // straight through. Flooring Long.MAX_VALUE to a day overflows, and a chart honouring
        // Long.MIN_VALUE would run from 1970 with one visible spike at the far right.
        assertThat(series.startMillis.first()).isEqualTo(utc(2026, 8, 20))
        assertThat(series.startMillis.last()).isEqualTo(utc(2026, 8, 24))
        assertThat(series.values).isEqualTo(listOf(100_00L, 0L, 0L, 0L, 200_00L))
    }

    @Test
    fun `all time with no data at all draws nothing`() {
        val series = buildTrendSeries(
            dayTotals = emptyList(),
            fromMillis = Long.MIN_VALUE,
            toMillisExclusive = Long.MAX_VALUE
        )
        // There is no span to infer and no bound to fall back on, so there is no chart. The widget
        // shows its empty line instead of a flat zero, which would claim the account had spent
        // nothing rather than that nothing is known.
        assertThat(series.values).isEqualTo(emptyList<Long>())
        assertThat(series.isDrawable).isFalse()
    }

    @Test
    fun `an empty bounded range is still drawn as its own zeroes`() {
        val series = buildTrendSeries(
            dayTotals = emptyList(),
            fromMillis = utc(2026, 9, 1),
            toMillisExclusive = utc(2026, 9, 8)
        )
        // A week with no spending is a real answer, unlike the unbounded case above: the user asked
        // about a period the app knows the bounds of, and every day in it was quiet.
        assertThat(series.values).isEqualTo(List(7) { 0L })
        assertThat(series.averagePaise).isEqualTo(0L)
    }

    @Test
    fun `two points are not a trend`() {
        val series = buildTrendSeries(
            dayTotals = listOf(day(2026, 9, 1, 100_00)),
            fromMillis = utc(2026, 9, 1),
            toMillisExclusive = utc(2026, 9, 3)
        )
        assertThat(series.values.size).isEqualTo(2)
        assertThat(series.isDrawable).isFalse()
    }

    @Test
    fun `three points are`() {
        val series = buildTrendSeries(
            dayTotals = emptyList(),
            fromMillis = utc(2026, 9, 1),
            toMillisExclusive = utc(2026, 9, 4)
        )
        assertThat(series.values.size).isEqualTo(MIN_TREND_BUCKETS)
        assertThat(series.isDrawable).isTrue()
    }

    @Test
    fun `a day total sitting outside the range is not counted`() {
        val series = buildTrendSeries(
            dayTotals = listOf(day(2026, 8, 31, 999_00), day(2026, 9, 2, 100_00)),
            fromMillis = utc(2026, 9, 1),
            toMillisExclusive = utc(2026, 9, 4)
        )
        // The aggregate is already range-scoped, so this cannot normally happen — but a stray row
        // silently inflating the average would be invisible on the chart, so the walk over the
        // range's own days is what decides, not the list handed in.
        assertThat(series.values).isEqualTo(listOf(0L, 100_00L, 0L))
    }

    @Test
    fun `an inverted range draws nothing rather than throwing`() {
        val series = buildTrendSeries(
            dayTotals = emptyList(),
            fromMillis = utc(2026, 9, 10),
            toMillisExclusive = utc(2026, 9, 1)
        )
        assertThat(series.values).isEqualTo(emptyList<Long>())
    }
}
