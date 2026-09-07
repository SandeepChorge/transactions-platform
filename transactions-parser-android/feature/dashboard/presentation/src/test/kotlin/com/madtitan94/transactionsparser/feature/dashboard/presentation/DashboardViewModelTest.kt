package com.madtitan94.transactionsparser.feature.dashboard.presentation

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.madtitan94.transactionsparser.core.domain.datasource.DashboardLocalDataSource
import com.madtitan94.transactionsparser.core.domain.model.CategoryTotal
import com.madtitan94.transactionsparser.core.domain.model.DateRange
import com.madtitan94.transactionsparser.core.domain.model.DayTotal
import com.madtitan94.transactionsparser.core.domain.model.PayeeSummary
import com.madtitan94.transactionsparser.core.domain.model.PayeeTotal
import com.madtitan94.transactionsparser.core.domain.model.TypeTotals
import com.madtitan94.transactionsparser.feature.dashboard.domain.CustomDashboard
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardId
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardKey
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardLayout
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardPreferences
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardRange
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    /** A Thursday in the middle of a month, so no range boundary coincides with it. */
    private val today = LocalDate.of(2026, 9, 3)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun utc(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    /**
     * Records the windows it was asked about, because most of what this ViewModel does is decide
     * which two periods to query — and a range that never reaches the data source would still
     * produce a screen that looks entirely correct.
     */
    private class FakeDashboardDataSource : DashboardLocalDataSource {
        val typeWindows = mutableListOf<DateRange>()
        var payeeLimit: Int? = null
        val typeTotals = MutableStateFlow(TypeTotals())

        override fun observeDayTotals(range: DateRange): Flow<List<DayTotal>> = flowOf(emptyList())

        override fun observeTypeTotals(range: DateRange): Flow<TypeTotals> {
            typeWindows += range
            return typeTotals
        }

        override fun observeCategoryTotals(range: DateRange): Flow<List<CategoryTotal>> =
            flowOf(emptyList())

        override fun observeTopPayees(range: DateRange, limit: Int): Flow<List<PayeeTotal>> {
            payeeLimit = limit
            return flowOf(emptyList())
        }

        override fun observePayeeSummary(range: DateRange): Flow<PayeeSummary> =
            flowOf(PayeeSummary())
    }

    /** Shorthand — every dashboard this ViewModel deals with is one of the four that ship. */
    private fun key(id: DashboardId) = DashboardKey.BuiltIn(id)

    private class FakeDashboardPreferences(
        range: DashboardRange = DashboardRange.Default,
        layout: DashboardLayout = DashboardLayout(
            defaultKey = DashboardKey.BuiltIn(DashboardId.PULSE)
        )
    ) : DashboardPreferences {
        val range = MutableStateFlow(range)
        val layout = MutableStateFlow(layout)
        var writes = 0

        override fun observeRange(): Flow<DashboardRange> = range
        override suspend fun setRange(range: DashboardRange) {
            writes++
            this.range.value = range
        }

        override fun observeLayout(): Flow<DashboardLayout> = layout

        // The ViewModel behind Home reads the layout and never writes it — every write below belongs
        // to the manage and builder screens. Failing loudly here is the assertion: a Home screen
        // that quietly rewrote the user's saved layout as they swiped would pass a silent stub.
        override suspend fun setDashboardOrder(order: List<DashboardKey>) = error("not written here")
        override suspend fun setDashboardEnabled(key: DashboardKey, enabled: Boolean) =
            error("not written here")
        override suspend fun setDefaultDashboard(key: DashboardKey) = error("not written here")
        override suspend fun saveCustomDashboard(dashboard: CustomDashboard) =
            error("not written here")
        override suspend fun deleteCustomDashboard(id: String) = error("not written here")
    }

    private fun viewModel(
        dataSource: DashboardLocalDataSource = FakeDashboardDataSource(),
        preferences: DashboardPreferences = FakeDashboardPreferences()
    ) = DashboardViewModel(dataSource, preferences, today = { today })

    @Test
    fun `the screen opens on the starred dashboard`() = runTest {
        val preferences = FakeDashboardPreferences(
            layout = DashboardLayout(defaultKey = key(DashboardId.MAPPING_HEALTH))
        )
        viewModel(preferences = preferences).state.test {
            assertThat(awaitItem().selected).isEqualTo(key(DashboardId.MAPPING_HEALTH))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `choosing a dashboard does not touch the stored default`() = runTest {
        val preferences = FakeDashboardPreferences()
        val vm = viewModel(preferences = preferences)

        vm.onAction(DashboardAction.OnDashboardSelected(key(DashboardId.PAYEES)))

        assertThat(vm.state.value.selected).isEqualTo(key(DashboardId.PAYEES))
        // Swiping to another dashboard is looking around, not re-deciding where Home opens. Writing
        // it back would mean the last dashboard glanced at silently became the landing screen.
        assertThat(preferences.layout.value.defaultKey).isEqualTo(key(DashboardId.PULSE))
        assertThat(preferences.writes).isEqualTo(0)
    }

    @Test
    fun `a preference change underneath the user does not move them`() = runTest {
        val preferences = FakeDashboardPreferences()
        val vm = viewModel(preferences = preferences)
        vm.onAction(DashboardAction.OnDashboardSelected(key(DashboardId.CATEGORIES)))

        preferences.layout.value = DashboardLayout(defaultKey = key(DashboardId.PAYEES))

        assertThat(vm.state.value.selected).isEqualTo(key(DashboardId.CATEGORIES))
    }

    @Test
    fun `a dashboard that has been switched off gives the selection back to the default`() =
        runTest {
            val preferences = FakeDashboardPreferences()
            val vm = viewModel(preferences = preferences)
            vm.onAction(DashboardAction.OnDashboardSelected(key(DashboardId.PAYEES)))

            preferences.layout.value = DashboardLayout(
                disabled = setOf(key(DashboardId.PAYEES)),
                defaultKey = key(DashboardId.PULSE)
            )

            // Otherwise the pager holds a page nothing in the chip row points at, and the screen
            // shows a dashboard the user has just turned off.
            assertThat(vm.state.value.selected).isEqualTo(key(DashboardId.PULSE))
        }

    @Test
    fun `only the dashboards the account enabled are shown, in that order`() = runTest {
        val preferences = FakeDashboardPreferences(
            layout = DashboardLayout(
                order = listOf(key(DashboardId.PAYEES), key(DashboardId.PULSE)),
                disabled = setOf(key(DashboardId.CATEGORIES), key(DashboardId.MAPPING_HEALTH)),
                defaultKey = key(DashboardId.PAYEES)
            )
        )
        val vm = viewModel(preferences = preferences)

        assertThat(vm.state.value.dashboards.map { it.key })
            .isEqualTo(listOf(key(DashboardId.PAYEES), key(DashboardId.PULSE)))
    }

    @Test
    fun `picking a range persists it rather than only holding it in state`() = runTest {
        val preferences = FakeDashboardPreferences()
        val vm = viewModel(preferences = preferences)

        vm.onAction(DashboardAction.OnRangeSelected(DashboardRange.LastMonth))

        // The state change has to arrive *through* the preference, not beside it: a range kept only
        // in memory would look right all session and reset on every launch.
        assertThat(preferences.range.value).isEqualTo(DashboardRange.LastMonth)
        assertThat(vm.state.value.range).isEqualTo(DashboardRange.LastMonth)
    }

    @Test
    fun `a stored range is honoured on open`() = runTest {
        val preferences = FakeDashboardPreferences(range = DashboardRange.ThisWeek)
        assertThat(viewModel(preferences = preferences).state.value.range)
            .isEqualTo(DashboardRange.ThisWeek)
    }

    @Test
    fun `changing the range re-queries both the period and the one before it`() = runTest {
        val dataSource = FakeDashboardDataSource()
        val preferences = FakeDashboardPreferences()
        val vm = viewModel(dataSource = dataSource, preferences = preferences)
        dataSource.typeWindows.clear()

        vm.onAction(DashboardAction.OnRangeSelected(DashboardRange.LastMonth))

        assertThat(dataSource.typeWindows).isEqualTo(
            listOf(
                DateRange(utc(2026, 8, 1), utc(2026, 9, 1)),
                DateRange(utc(2026, 7, 1), utc(2026, 8, 1))
            )
        )
    }

    @Test
    fun `all time asks for no comparison period`() = runTest {
        val dataSource = FakeDashboardDataSource()
        val vm = viewModel(dataSource = dataSource)
        dataSource.typeWindows.clear()

        vm.onAction(DashboardAction.OnRangeSelected(DashboardRange.AllTime))

        assertThat(dataSource.typeWindows).isEqualTo(listOf(DateRange.AllTime))
        assertThat(vm.state.value.data.previousDebitPaise).isEqualTo(null)
    }

    @Test
    fun `a picked custom range is stored as whole days including the one tapped last`() = runTest {
        val preferences = FakeDashboardPreferences()
        val vm = viewModel(preferences = preferences)

        vm.onAction(
            DashboardAction.OnCustomRangePicked(
                fromMillis = utc(2026, 8, 1),
                toMillisInclusive = utc(2026, 8, 26)
            )
        )

        assertThat(preferences.range.value)
            .isEqualTo(DashboardRange.Custom(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 26)))
        assertThat(vm.state.value.isPickingCustomRange).isFalse()
    }

    @Test
    fun `the custom picker opens and closes without changing the range`() = runTest {
        val preferences = FakeDashboardPreferences()
        val vm = viewModel(preferences = preferences)

        vm.onAction(DashboardAction.OnCustomRangeClick)
        assertThat(vm.state.value.isPickingCustomRange).isTrue()

        vm.onAction(DashboardAction.OnCustomRangeDismiss)
        assertThat(vm.state.value.isPickingCustomRange).isFalse()
        // Dismissing is a cancel. Leaving the range on whatever was half-picked would change the
        // whole screen behind a dialog the user backed out of.
        assertThat(preferences.range.value).isEqualTo(DashboardRange.Default)
    }

    @Test
    fun `the payee ranking is fetched deeper than a card shows`() = runTest {
        val dataSource = FakeDashboardDataSource()
        viewModel(dataSource = dataSource)

        // Mapping health filters this list down to the unmapped rows, so asking for exactly the
        // five a card displays could leave that dashboard with one row on an account with dozens.
        assertThat(dataSource.payeeLimit).isNotNull()
        assertThat(dataSource.payeeLimit!! > DASHBOARD_ROW_LIMIT).isTrue()
    }

    @Test
    fun `the resolved window matches the range the chips show`() = runTest {
        val preferences = FakeDashboardPreferences(range = DashboardRange.ThisMonth)
        val vm = viewModel(preferences = preferences)

        assertThat(vm.resolvedRange()).isEqualTo(DateRange(utc(2026, 9, 1), utc(2026, 10, 1)))
        assertThat(vm.rangeLengthInDays()).isEqualTo(30)
    }

    @Test
    fun `data arriving clears the loading state`() = runTest {
        val dataSource = FakeDashboardDataSource()
        val vm = viewModel(dataSource = dataSource)
        dataSource.typeTotals.value = TypeTotals(debitPaise = 400_00, debitCount = 3)

        assertThat(vm.state.value.isLoading).isFalse()
        assertThat(vm.state.value.data.debitPaise).isEqualTo(400_00L)
    }
}
