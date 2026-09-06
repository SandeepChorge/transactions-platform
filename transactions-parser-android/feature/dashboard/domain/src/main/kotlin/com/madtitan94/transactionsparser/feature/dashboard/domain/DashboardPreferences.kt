package com.madtitan94.transactionsparser.feature.dashboard.domain

import kotlinx.coroutines.flow.Flow

/**
 * What the user has decided about their dashboards, per account.
 *
 * Preferences rather than the database, following `design/DashboardSpec.dc.html` §3: none of this is
 * statement data, none of it needs a migration when it changes shape, and losing it costs the user a
 * tap rather than a record.
 *
 * Per account matters because the range is a reading habit, not a device setting: two accounts on
 * one phone are two different sets of statements covering two different periods, and inheriting the
 * other one's range would silently show the wrong month.
 *
 * Only the range is writable here. Which dashboards are enabled and in what order is read but not
 * written — the screen that writes it is Phase 7's, and shipping a reader first means the chip row
 * already honours a preference file that does not exist yet, rather than having to be rewritten
 * when it does.
 */
interface DashboardPreferences {

    /** The range the user last chose, falling back to [DashboardRange.Default]. */
    fun observeRange(): Flow<DashboardRange>

    suspend fun setRange(range: DashboardRange)

    /**
     * The dashboards to show, in order, filtered to those this build knows about.
     *
     * Never empty: an account that has switched everything off still gets the default set back,
     * because a Home screen with nothing on it is indistinguishable from a broken one.
     */
    fun observeEnabledDashboards(): Flow<List<DashboardId>>

    /** Which dashboard the chip row opens on. */
    fun observeDefaultDashboard(): Flow<DashboardId>
}
