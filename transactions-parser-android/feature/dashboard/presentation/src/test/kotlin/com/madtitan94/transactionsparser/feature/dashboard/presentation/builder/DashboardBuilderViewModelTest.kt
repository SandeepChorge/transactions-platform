package com.madtitan94.transactionsparser.feature.dashboard.presentation.builder

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import com.madtitan94.transactionsparser.feature.dashboard.domain.CustomDashboard
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardLayout
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardWidgetId
import com.madtitan94.transactionsparser.feature.dashboard.presentation.FakeDashboardPreferences
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
class DashboardBuilderViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private val weekends = CustomDashboard(
        id = "abc",
        name = "Weekends",
        widgets = listOf(DashboardWidgetId.TREND_CHART, DashboardWidgetId.HERO_KPI)
    )

    /**
     * The route arguments as navigation hands them over.
     *
     * Written as the `SavedStateHandle` the ViewModel actually reads rather than as a constructor
     * parameter, so the test exercises the same `toRoute` decoding the app does — a mismatch there
     * is invisible in a unit test that bypasses it.
     */
    private fun handle(dashboardId: String? = null) =
        SavedStateHandle(mapOf("dashboardId" to dashboardId))

    private fun viewModel(
        preferences: FakeDashboardPreferences = FakeDashboardPreferences(),
        dashboardId: String? = null
    ) = DashboardBuilderViewModel(handle(dashboardId), preferences)

    @Test
    fun `a new dashboard starts with every card offered and none chosen`() = runTest {
        val vm = viewModel()

        assertThat(vm.state.value.isEditing).isFalse()
        assertThat(vm.state.value.rows.map { it.widget })
            .isEqualTo(DashboardWidgetId.entries.toList())
        assertThat(vm.state.value.selected).isEqualTo(emptyList<DashboardWidgetId>())
        // Nothing chosen and nothing named, so there is nothing to save yet.
        assertThat(vm.state.value.canSave).isFalse()
    }

    @Test
    fun `editing seeds the chosen cards first, in their saved order`() = runTest {
        val preferences = FakeDashboardPreferences(DashboardLayout(custom = listOf(weekends)))
        val vm = viewModel(preferences, dashboardId = "abc")

        assertThat(vm.state.value.isEditing).isTrue()
        assertThat(vm.state.value.name).isEqualTo("Weekends")
        // Chosen cards lead because that is the dashboard as it will be read; the rest follow as
        // the menu of what could be added.
        assertThat(vm.state.value.selected)
            .containsExactly(DashboardWidgetId.TREND_CHART, DashboardWidgetId.HERO_KPI)
        assertThat(vm.state.value.rows.map { it.widget }.take(2))
            .containsExactly(DashboardWidgetId.TREND_CHART, DashboardWidgetId.HERO_KPI)
    }

    @Test
    fun `a dashboard needs a name and at least one card`() = runTest {
        val vm = viewModel()

        vm.onAction(DashboardBuilderAction.OnNameChanged("Weekends"))
        assertThat(vm.state.value.canSave).isFalse()

        vm.onAction(
            DashboardBuilderAction.OnWidgetToggled(DashboardWidgetId.HERO_KPI, isSelected = true)
        )
        assertThat(vm.state.value.canSave).isTrue()

        // Whitespace is not a name. The chip row would render it as a blank chip.
        vm.onAction(DashboardBuilderAction.OnNameChanged("   "))
        assertThat(vm.state.value.canSave).isFalse()
    }

    @Test
    fun `saving writes the ticked cards in list order, not in catalogue order`() = runTest {
        val preferences = FakeDashboardPreferences()
        val vm = viewModel(preferences)

        vm.onAction(DashboardBuilderAction.OnNameChanged("Weekends"))
        vm.onAction(
            DashboardBuilderAction.OnWidgetToggled(DashboardWidgetId.CATEGORY_DONUT, isSelected = true)
        )
        vm.onAction(
            DashboardBuilderAction.OnWidgetToggled(DashboardWidgetId.HERO_KPI, isSelected = true)
        )
        // Drag the donut above the hero. Order is half of what the builder decides, so a save that
        // wrote the enum's own order would silently discard it.
        val donutIndex = vm.state.value.rows.indexOfFirst { it.widget == DashboardWidgetId.CATEGORY_DONUT }
        vm.onAction(DashboardBuilderAction.OnMoved(from = donutIndex, to = 0))

        vm.onAction(DashboardBuilderAction.OnSave)

        val saved = preferences.layout.value.custom.single()
        assertThat(saved.name).isEqualTo("Weekends")
        assertThat(saved.widgets)
            .containsExactly(DashboardWidgetId.CATEGORY_DONUT, DashboardWidgetId.HERO_KPI)
    }

    @Test
    fun `nothing is written until Save`() = runTest {
        val preferences = FakeDashboardPreferences()
        val vm = viewModel(preferences)

        vm.onAction(DashboardBuilderAction.OnNameChanged("Weekends"))
        vm.onAction(
            DashboardBuilderAction.OnWidgetToggled(DashboardWidgetId.HERO_KPI, isSelected = true)
        )
        vm.onAction(DashboardBuilderAction.OnMoved(from = 0, to = 3))

        // A half-built dashboard appearing on Home while the user is still deciding is worse than
        // making them press a button.
        assertThat(preferences.layout.value.custom).isEqualTo(emptyList<CustomDashboard>())
    }

    @Test
    fun `saving an edit replaces the dashboard instead of adding a second one`() = runTest {
        val preferences = FakeDashboardPreferences(DashboardLayout(custom = listOf(weekends)))
        val vm = viewModel(preferences, dashboardId = "abc")

        vm.onAction(DashboardBuilderAction.OnNameChanged("Weekend spending"))
        vm.onAction(DashboardBuilderAction.OnSave)

        val saved = preferences.layout.value.custom.single()
        assertThat(saved.id).isEqualTo("abc")
        assertThat(saved.name).isEqualTo("Weekend spending")
    }

    @Test
    fun `a new dashboard gets an id of its own`() = runTest {
        val preferences = FakeDashboardPreferences(DashboardLayout(custom = listOf(weekends)))
        val vm = viewModel(preferences)

        vm.onAction(DashboardBuilderAction.OnNameChanged("Weekends"))
        vm.onAction(
            DashboardBuilderAction.OnWidgetToggled(DashboardWidgetId.HERO_KPI, isSelected = true)
        )
        vm.onAction(DashboardBuilderAction.OnSave)

        assertThat(preferences.layout.value.custom).hasSize(2)
        assertThat(preferences.layout.value.custom.last().id).isNotEqualTo("abc")
    }

    @Test
    fun `the screen closes only after the write has landed`() = runTest {
        val preferences = FakeDashboardPreferences()
        val vm = viewModel(preferences)
        vm.onAction(DashboardBuilderAction.OnNameChanged("Weekends"))
        vm.onAction(
            DashboardBuilderAction.OnWidgetToggled(DashboardWidgetId.HERO_KPI, isSelected = true)
        )

        vm.events.test {
            vm.onAction(DashboardBuilderAction.OnSave)
            assertThat(awaitItem()).isEqualTo(DashboardBuilderEvent.Saved)
            // Otherwise the manage screen behind this one is shown without the dashboard that was
            // just saved on it.
            assertThat(preferences.layout.value.custom).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an incomplete dashboard cannot be saved by pressing Save`() = runTest {
        val preferences = FakeDashboardPreferences()
        val vm = viewModel(preferences)

        vm.onAction(DashboardBuilderAction.OnSave)

        // The button is disabled, but the guard lives in the ViewModel too — the enabled state of a
        // button is a UI fact, and it is not what should be keeping an unnamed dashboard off Home.
        assertThat(preferences.layout.value.custom).isEqualTo(emptyList<CustomDashboard>())
    }
}
