package com.madtitan94.transactionsparser.feature.dashboard.presentation

import com.madtitan94.transactionsparser.feature.dashboard.domain.CustomDashboard
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardKey
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardLayout
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardPreferences
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A writable stand-in for the preference file, shared by the manage and builder tests.
 *
 * Every write lands back in [layout] rather than being merely recorded, because the screens under
 * test read what they write: an optimistic reorder is only correct if the flow that follows agrees
 * with it, and a stub that swallowed the write would hide exactly that disagreement.
 */
class FakeDashboardPreferences(
    layout: DashboardLayout = DashboardLayout()
) : DashboardPreferences {

    val layout = MutableStateFlow(layout)
    val range = MutableStateFlow<DashboardRange>(DashboardRange.Default)

    /** Counts order writes, so a test can tell one drag from a drag written on every frame. */
    var orderWrites = 0

    override fun observeRange(): Flow<DashboardRange> = range

    override suspend fun setRange(range: DashboardRange) {
        this.range.value = range
    }

    override fun observeLayout(): Flow<DashboardLayout> = layout

    override suspend fun setDashboardOrder(order: List<DashboardKey>) {
        orderWrites++
        layout.value = layout.value.copy(order = order)
    }

    override suspend fun setDashboardEnabled(key: DashboardKey, enabled: Boolean) {
        val disabled = layout.value.disabled.toMutableSet()
        if (enabled) disabled.remove(key) else disabled.add(key)
        layout.value = layout.value.copy(disabled = disabled)
    }

    override suspend fun setDefaultDashboard(key: DashboardKey) {
        layout.value = layout.value.copy(defaultKey = key)
    }

    override suspend fun saveCustomDashboard(dashboard: CustomDashboard) {
        val existing = layout.value.custom
        val index = existing.indexOfFirst { it.id == dashboard.id }
        val updated = if (index >= 0) {
            existing.toMutableList().apply { set(index, dashboard) }
        } else {
            existing + dashboard
        }
        layout.value = layout.value.copy(custom = updated)
    }

    override suspend fun deleteCustomDashboard(id: String) {
        layout.value = layout.value.copy(custom = layout.value.custom.filterNot { it.id == id })
    }

    /** Written to, not merely recorded, so a dismissal actually removes the callout under test. */
    val dismissedAnomalies = MutableStateFlow<Set<Long>>(emptySet())

    override fun observeDismissedAnomalies(): Flow<Set<Long>> = dismissedAnomalies

    override suspend fun dismissAnomaly(transactionId: Long) {
        dismissedAnomalies.value = dismissedAnomalies.value + transactionId
    }
}
