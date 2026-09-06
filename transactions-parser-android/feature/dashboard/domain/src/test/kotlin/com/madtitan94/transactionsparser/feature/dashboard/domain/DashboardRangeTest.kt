package com.madtitan94.transactionsparser.feature.dashboard.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.madtitan94.transactionsparser.core.domain.model.DateRange
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The range arithmetic, pinned against the one mistake this app knows how to make.
 *
 * Every boundary here is asserted as a UTC instant rather than as "some millis", because a range
 * built in the device zone would still pass a test that only checked the two bounds were a month
 * apart. What has to hold is that the boundary lands on midnight *UTC* — the clock the statement
 * rows are stored in.
 */
class DashboardRangeTest {

    private fun utc(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    /** A Thursday, deliberately mid-week and mid-month so no boundary coincides with it. */
    private val today = LocalDate.of(2026, 9, 3)

    @Test
    fun `today spans one day from midnight utc`() {
        assertThat(DashboardRange.Today.resolve(today))
            .isEqualTo(DateRange(utc(2026, 9, 3), utc(2026, 9, 4)))
    }

    @Test
    fun `this week starts on monday`() {
        // 3 Sep 2026 is a Thursday, so the week it belongs to opens on Monday the 31st of August.
        assertThat(DashboardRange.ThisWeek.resolve(today))
            .isEqualTo(DateRange(utc(2026, 8, 31), utc(2026, 9, 7)))
    }

    @Test
    fun `this month runs from the first to the first of the next`() {
        assertThat(DashboardRange.ThisMonth.resolve(today))
            .isEqualTo(DateRange(utc(2026, 9, 1), utc(2026, 10, 1)))
    }

    @Test
    fun `last month is the calendar month before, not thirty days`() {
        assertThat(DashboardRange.LastMonth.resolve(today))
            .isEqualTo(DateRange(utc(2026, 8, 1), utc(2026, 9, 1)))
    }

    @Test
    fun `a month boundary is midnight utc rather than midnight anywhere else`() {
        val range = DashboardRange.ThisMonth.resolve(today)
        // 1 Sep 2026 00:00 UTC. Any local-zone construction of "the first of the month" would land
        // hours either side of this, moving rows between months for everyone outside UTC.
        assertThat(range.fromMillis).isEqualTo(1_788_220_800_000L)
    }

    @Test
    fun `all time is unbounded in both directions`() {
        assertThat(DashboardRange.AllTime.resolve(today)).isEqualTo(DateRange.AllTime)
    }

    @Test
    fun `a custom range includes the day the user picked as its end`() {
        val range = DashboardRange.Custom(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 26))
        // The picker says "1 Aug – 26 Aug" and the user means both days, so the exclusive bound is
        // the 27th. Stopping at the 26th would silently drop that day's transactions.
        assertThat(range.resolve(today)).isEqualTo(DateRange(utc(2026, 8, 1), utc(2026, 8, 27)))
    }

    @Test
    fun `a single day custom range is one day, not an empty one`() {
        val range = DashboardRange.Custom(LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 4))
        assertThat(range.resolve(today)).isEqualTo(DateRange(utc(2026, 8, 4), utc(2026, 8, 5)))
    }

    @Test
    fun `consecutive months cannot both claim the boundary instant`() {
        val august = DashboardRange.LastMonth.resolve(today)
        val september = DashboardRange.ThisMonth.resolve(today)
        // Half-open: August ends exactly where September begins, so midnight on the 1st belongs to
        // September alone. An inclusive upper bound would count that transaction twice.
        assertThat(august.toMillisExclusive).isEqualTo(september.fromMillis)
    }

    @Test
    fun `the period before this month is last month`() {
        assertThat(DashboardRange.ThisMonth.previous(today))
            .isEqualTo(DateRange(utc(2026, 8, 1), utc(2026, 9, 1)))
    }

    @Test
    fun `the period before last month is the month before that`() {
        assertThat(DashboardRange.LastMonth.previous(today))
            .isEqualTo(DateRange(utc(2026, 7, 1), utc(2026, 8, 1)))
    }

    @Test
    fun `the period before a week is the week before it`() {
        assertThat(DashboardRange.ThisWeek.previous(today))
            .isEqualTo(DateRange(utc(2026, 8, 24), utc(2026, 8, 31)))
    }

    @Test
    fun `all time has no period before it`() {
        // Rather than comparing against an invented baseline of zero, which would make every
        // account's first period look like infinite growth.
        assertThat(DashboardRange.AllTime.previous(today)).isNull()
    }

    @Test
    fun `a month knows its own length in days`() {
        assertThat(DashboardRange.ThisMonth.lengthInDays(today)).isEqualTo(30)
        assertThat(DashboardRange.LastMonth.lengthInDays(today)).isEqualTo(31)
        assertThat(DashboardRange.ThisWeek.lengthInDays(today)).isEqualTo(7)
        assertThat(DashboardRange.Today.lengthInDays(today)).isEqualTo(1)
    }

    @Test
    fun `all time has no length`() {
        assertThat(DashboardRange.AllTime.lengthInDays(today)).isNull()
    }

    @Test
    fun `the default range is a period rather than everything`() {
        // All time would make the range chip look broken on a fresh account: every widget would
        // show the same figures whichever period the user picked.
        assertThat(DashboardRange.Default).isEqualTo(DashboardRange.ThisMonth)
    }

    @Test
    fun `every selectable range carries a distinct persistence key`() {
        val keys = DashboardRange.Selectable.map { it.key }
        assertThat(keys.toSet().size).isEqualTo(keys.size)
        assertThat(DashboardRange.Custom(today, today).key).isEqualTo("CUSTOM")
    }

    @Test
    fun `a year end does not roll the month into the wrong one`() {
        val newYearsDay = LocalDate.of(2027, 1, 1)
        assertThat(DashboardRange.LastMonth.resolve(newYearsDay))
            .isEqualTo(DateRange(utc(2026, 12, 1), utc(2027, 1, 1)))
        assertThat(DashboardRange.ThisMonth.previous(newYearsDay)).isNotNull()
    }
}
