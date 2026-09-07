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
 * The layout is exposed as the four stored facts in one [DashboardLayout] rather than as a resolved
 * list of dashboards. Every screen here needs a different view of it — Home wants the enabled ones,
 * the manage screen wants the disabled ones too, the builder wants only the custom ones — and
 * deriving those from one read keeps them from disagreeing with each other.
 */
interface DashboardPreferences {

    /** The range the user last chose, falling back to [DashboardRange.Default]. */
    fun observeRange(): Flow<DashboardRange>

    suspend fun setRange(range: DashboardRange)

    /** Order, disabled set, starred dashboard and the user's own dashboards, for this account. */
    fun observeLayout(): Flow<DashboardLayout>

    /**
     * Writes the display order.
     *
     * The whole order at once rather than a move instruction, because a drag has already produced
     * the finished list and re-deriving it from a from/to pair would be a second chance to get it
     * wrong.
     */
    suspend fun setDashboardOrder(order: List<DashboardKey>)

    suspend fun setDashboardEnabled(key: DashboardKey, enabled: Boolean)

    suspend fun setDefaultDashboard(key: DashboardKey)

    /** Creates or replaces a user-built dashboard, matched on [CustomDashboard.id]. */
    suspend fun saveCustomDashboard(dashboard: CustomDashboard)

    suspend fun deleteCustomDashboard(id: String)
}
