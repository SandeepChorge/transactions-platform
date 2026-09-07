package com.madtitan94.transactionsparser.feature.dashboard.presentation.manage

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.madtitan94.transactionsparser.core.domain.analytics.AnalyticsEvent
import com.madtitan94.transactionsparser.feature.dashboard.domain.CustomDashboard
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardId
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardKey
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardLayout
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardWidgetId
import com.madtitan94.transactionsparser.feature.dashboard.presentation.FakeDashboardPreferences
import com.madtitan94.transactionsparser.feature.dashboard.presentation.RecordingAnalyticsTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardLayoutViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val analytics = RecordingAnalyticsTracker()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun key(id: DashboardId) = DashboardKey.BuiltIn(id)

    private val weekends = CustomDashboard(
        id = "abc",
        name = "Weekends",
        widgets = listOf(DashboardWidgetId.HERO_KPI)
    )

    @Test
    fun `switched-off dashboards stay on the list rather than disappearing from it`() = runTest {
        val preferences = FakeDashboardPreferences(
            DashboardLayout(disabled = setOf(key(DashboardId.PAYEES)))
        )
        val vm = DashboardLayoutViewModel(preferences, analytics)

        // The whole job of this screen is offering them back. A switch you cannot find is a setting
        // you cannot undo.
        val payees = vm.state.value.dashboards.single { it.key == key(DashboardId.PAYEES) }
        assertThat(payees.isEnabled).isFalse()
        assertThat(vm.state.value.dashboards).hasSize(4)
    }

    @Test
    fun `switching one off writes it and the count follows`() = runTest {
        val preferences = FakeDashboardPreferences()
        val vm = DashboardLayoutViewModel(preferences, analytics)

        vm.onAction(
            DashboardLayoutAction.OnEnabledChanged(key(DashboardId.CATEGORIES), isEnabled = false)
        )

        assertThat(preferences.layout.value.disabled).isEqualTo(setOf(key(DashboardId.CATEGORIES)))
        assertThat(vm.state.value.enabledCount).isEqualTo(3)
    }

    @Test
    fun `a drag moves the row in state before the write comes back`() = runTest {
        val preferences = FakeDashboardPreferences()
        val vm = DashboardLayoutViewModel(preferences, analytics)

        vm.onAction(DashboardLayoutAction.OnMoved(from = 0, to = 2))

        // The optimistic half is the point: a drag fires this on every row it crosses, and a list
        // that waited for the preference flow would let the finger get ahead of it and move the
        // same row twice.
        assertThat(vm.state.value.dashboards.map { it.key }).containsExactly(
            key(DashboardId.CATEGORIES),
            key(DashboardId.MAPPING_HEALTH),
            key(DashboardId.PULSE),
            key(DashboardId.PAYEES)
        )
        assertThat(preferences.layout.value.order).isEqualTo(
            vm.state.value.dashboards.map { it.key }
        )
    }

    @Test
    fun `a move outside the list is ignored rather than throwing`() = runTest {
        val preferences = FakeDashboardPreferences()
        val vm = DashboardLayoutViewModel(preferences, analytics)

        vm.onAction(DashboardLayoutAction.OnMoved(from = 0, to = 9))

        assertThat(preferences.orderWrites).isEqualTo(0)
        assertThat(vm.state.value.dashboards.map { it.key }).isEqualTo(
            preferences.layout.value.all.map { it.key }
        )
    }

    @Test
    fun `starring a dashboard persists it`() = runTest {
        val preferences = FakeDashboardPreferences()
        val vm = DashboardLayoutViewModel(preferences, analytics)

        vm.onAction(DashboardLayoutAction.OnDefaultSelected(key(DashboardId.PAYEES)))

        assertThat(preferences.layout.value.defaultKey).isEqualTo(key(DashboardId.PAYEES))
        assertThat(vm.state.value.defaultKey).isEqualTo(key(DashboardId.PAYEES))
    }

    @Test
    fun `starring a built-in dashboard reports which one`() = runTest {
        val vm = DashboardLayoutViewModel(FakeDashboardPreferences(), analytics)

        vm.onAction(DashboardLayoutAction.OnDefaultSelected(key(DashboardId.PAYEES)))

        assertThat(analytics.only<AnalyticsEvent.DefaultDashboardChanged>().map { it.dashboard })
            .containsExactly("PAYEES")
    }

    @Test
    fun `starring a user-built dashboard reports the kind, never the id`() = runTest {
        // The id is `custom:<uuid>` and unique to one person. Sent as a dimension it answers
        // nothing and turns an anonymous event into an identifying one.
        val preferences = FakeDashboardPreferences(DashboardLayout(custom = listOf(weekends)))
        val vm = DashboardLayoutViewModel(preferences, analytics)

        vm.onAction(DashboardLayoutAction.OnDefaultSelected(DashboardKey.Custom("abc")))

        val reported = analytics.only<AnalyticsEvent.DefaultDashboardChanged>().single().dashboard
        assertThat(reported).isEqualTo("CUSTOM")
        assertThat(reported.contains("abc")).isFalse()
    }

    @Test
    fun `only a dashboard the user built can be deleted`() = runTest {
        val preferences = FakeDashboardPreferences(DashboardLayout(custom = listOf(weekends)))
        val vm = DashboardLayoutViewModel(preferences, analytics)

        val builtIn = vm.state.value.dashboards.single { it.key == key(DashboardId.PULSE) }
        val custom = vm.state.value.dashboards.single { it.key == DashboardKey.Custom("abc") }
        assertThat(builtIn.isCustom).isFalse()
        assertThat(custom.isCustom).isTrue()

        // Confirming a delete on one of the four that ship must be a no-op, not a crash and not a
        // silent removal — they cannot be recreated from this screen.
        vm.onAction(DashboardLayoutAction.OnDeleteClick(builtIn))
        vm.onAction(DashboardLayoutAction.OnDeleteConfirmed)
        assertThat(preferences.layout.value.custom).isEqualTo(listOf(weekends))
    }

    @Test
    fun `deleting a custom dashboard removes it and closes the dialog`() = runTest {
        val preferences = FakeDashboardPreferences(DashboardLayout(custom = listOf(weekends)))
        val vm = DashboardLayoutViewModel(preferences, analytics)
        val custom = vm.state.value.dashboards.single { it.key == DashboardKey.Custom("abc") }

        vm.onAction(DashboardLayoutAction.OnDeleteClick(custom))
        assertThat(vm.state.value.pendingDelete).isEqualTo(custom)

        vm.onAction(DashboardLayoutAction.OnDeleteConfirmed)

        assertThat(vm.state.value.pendingDelete).isNull()
        assertThat(preferences.layout.value.custom).isEqualTo(emptyList<CustomDashboard>())
        assertThat(vm.state.value.dashboards.map { it.key }).doesNotContain(DashboardKey.Custom("abc"))
    }
}
