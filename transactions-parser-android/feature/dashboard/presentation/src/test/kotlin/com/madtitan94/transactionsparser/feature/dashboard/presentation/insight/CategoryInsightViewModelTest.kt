package com.madtitan94.transactionsparser.feature.dashboard.presentation.insight

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import com.madtitan94.transactionsparser.core.domain.datasource.AnomalyLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.CategoryInsightLocalDataSource
import com.madtitan94.transactionsparser.core.domain.model.AnomalyScope
import com.madtitan94.transactionsparser.core.domain.model.CategoryShare
import com.madtitan94.transactionsparser.core.domain.model.DateRange
import com.madtitan94.transactionsparser.core.domain.model.PayeeTotal
import com.madtitan94.transactionsparser.core.domain.model.PeriodTotal
import com.madtitan94.transactionsparser.core.domain.model.SpendAnomaly
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardRange
import com.madtitan94.transactionsparser.feature.dashboard.presentation.FakeDashboardPreferences
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
class CategoryInsightViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val today = LocalDate.of(2026, 9, 7)

    @BeforeEach
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    /**
     * Records what each stream was asked for, which is most of what this class does.
     *
     * The ranges matter more than the figures here: the screen's whole contract is that three cards
     * follow the shared filter and one deliberately does not.
     */
    private class FakeInsights(
        private val share: CategoryShare = CategoryShare(0L, 0, 0, 0L),
        private val previous: CategoryShare = CategoryShare(0L, 0, 0, 0L)
    ) : CategoryInsightLocalDataSource {

        val shareRanges = mutableListOf<DateRange>()
        val dayRanges = mutableListOf<DateRange>()
        val monthRanges = mutableListOf<DateRange>()
        var lastCategoryId: Long? = -99L
        var lastLimit = 0

        override fun observeMonthTotals(
            categoryId: Long?,
            range: DateRange
        ): Flow<List<PeriodTotal>> {
            lastCategoryId = categoryId
            monthRanges += range
            return flowOf(emptyList())
        }

        override fun observeDayTotals(
            categoryId: Long?,
            range: DateRange
        ): Flow<List<PeriodTotal>> {
            dayRanges += range
            return flowOf(emptyList())
        }

        override fun observeTopPayees(
            categoryId: Long?,
            range: DateRange,
            limit: Int
        ): Flow<List<PayeeTotal>> {
            lastLimit = limit
            return flowOf(emptyList())
        }

        override fun observeShare(categoryId: Long?, range: DateRange): Flow<CategoryShare> {
            shareRanges += range
            // The first ask is the current window and the second is the period before it, in the
            // order the ViewModel builds them.
            return flowOf(if (shareRanges.size == 1) share else previous)
        }
    }

    private fun handle(categoryId: Long = 7L, categoryName: String? = "Food") =
        SavedStateHandle(
            mapOf("categoryId" to categoryId, "categoryName" to categoryName)
        )

    /** Records the scope it was asked for — the insight screen must ask about its own category. */
    private class FakeAnomalies(
        anomalies: List<SpendAnomaly> = emptyList()
    ) : AnomalyLocalDataSource {
        var scope: AnomalyScope? = null
        var baseline: DateRange? = null
        val found = MutableStateFlow(anomalies)

        override fun observeAnomalies(
            range: DateRange,
            baseline: DateRange,
            scope: AnomalyScope,
            multiplier: Double,
            minSampleCount: Int,
            minAmountPaise: Long,
            limit: Int
        ): Flow<List<SpendAnomaly>> {
            this.scope = scope
            this.baseline = baseline
            return found
        }
    }

    private fun anomaly(id: Long) = SpendAnomaly(
        transactionId = id,
        dateTimeUtcMillis = utc(2026, 5, 12),
        label = "Payee $id",
        statementName = "PAYEE $id",
        normalizedName = "payee$id",
        categoryId = 7L,
        categoryName = "Food",
        amountPaise = 500_000L,
        baselineMeanPaise = 100_000L,
        baselineSampleCount = 8
    )

    private fun viewModel(
        insights: FakeInsights = FakeInsights(),
        anomalies: AnomalyLocalDataSource = FakeAnomalies(),
        preferences: FakeDashboardPreferences = FakeDashboardPreferences(),
        categoryId: Long = 7L,
        categoryName: String? = "Food"
    ) = CategoryInsightViewModel(
        savedStateHandle = handle(categoryId, categoryName),
        insights = insights,
        anomalies = anomalies,
        preferences = preferences,
        today = { today }
    )

    private fun utc(year: Int, month: Int, day: Int) =
        LocalDate.of(year, month, day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `the sentinel id is read back as the unmapped bucket`() = runTest {
        val insights = FakeInsights()
        viewModel(insights = insights, categoryId = -1L, categoryName = null)
        // -1 travels through navigation because a type-safe route has no nullable Long. It has to
        // arrive at the query as a null, or the unmapped bucket would be asked for as a category
        // that cannot exist and every card would come back empty.
        assertThat(insights.lastCategoryId).isNull()
    }

    @Test
    fun `a real id is passed through untouched`() = runTest {
        val insights = FakeInsights()
        viewModel(insights = insights, categoryId = 7L)
        assertThat(insights.lastCategoryId).isEqualTo(7L)
    }

    @Test
    fun `the name travels with the route so the header draws on the first frame`() = runTest {
        val vm = viewModel()
        vm.state.test {
            assertThat(awaitItem().categoryName).isEqualTo("Food")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the day card follows the shared filter`() = runTest {
        val insights = FakeInsights()
        val preferences = FakeDashboardPreferences()
        preferences.range.value = DashboardRange.LastMonth
        viewModel(insights = insights, preferences = preferences)

        // August 2026, resolved against the pinned today.
        assertThat(insights.dayRanges.last()).isEqualTo(
            DateRange(utc(2026, 8, 1), utc(2026, 9, 1))
        )
    }

    @Test
    fun `the trend card ignores the shared filter`() = runTest {
        val insights = FakeInsights()
        val preferences = FakeDashboardPreferences()
        preferences.range.value = DashboardRange.Today
        viewModel(insights = insights, preferences = preferences)

        // "Today" is one day. The trend still asks for six whole months ending with September,
        // because "how is this going over time" is not a question a single day can answer.
        assertThat(insights.monthRanges.last()).isEqualTo(
            DateRange(utc(2026, 4, 1), utc(2026, 10, 1))
        )
    }

    @Test
    fun `changing the trend window rewidens the month query without touching the filter`() =
        runTest {
            val insights = FakeInsights()
            val preferences = FakeDashboardPreferences()
            val vm = viewModel(insights = insights, preferences = preferences)
            val daysBefore = insights.dayRanges.size

            vm.onAction(
                CategoryInsightAction.OnTrendWindowSelected(TrendWindow.ONE_YEAR)
            )

            assertThat(insights.monthRanges.last()).isEqualTo(
                DateRange(utc(2025, 10, 1), utc(2026, 10, 1))
            )
            // The shared range never changed, so the preference must not have been written.
            assertThat(preferences.range.value).isEqualTo(DashboardRange.Default)
            assertThat(insights.dayRanges.size > daysBefore).isEqualTo(true)
        }

    @Test
    fun `picking a range writes it back to the shared preference`() = runTest {
        val preferences = FakeDashboardPreferences()
        val vm = viewModel(preferences = preferences)

        vm.onAction(CategoryInsightAction.OnRangeSelected(DashboardRange.AllTime))

        // Written to the same preference Home reads. There is one period the user is thinking in,
        // not a dashboard period and a separate insight period that quietly disagree.
        assertThat(preferences.range.value).isEqualTo(DashboardRange.AllTime)
    }

    @Test
    fun `all time loads rather than hanging on a period that has no before`() = runTest {
        val preferences = FakeDashboardPreferences()
        preferences.range.value = DashboardRange.AllTime
        val vm = viewModel(preferences = preferences)

        vm.state.test {
            val state = awaitItem()
            // `combine` emits nothing until every source has a value, so a missing comparison flow
            // would leave this screen spinning on a range that is perfectly loadable.
            assertThat(state.isLoading).isFalse()
            assertThat(state.data.deltaPercent).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the delta is dropped when the period before held nothing`() = runTest {
        val insights = FakeInsights(
            share = CategoryShare(500_00L, 4, 2, 1_000_00L),
            previous = CategoryShare(0L, 0, 0, 0L)
        )
        val vm = viewModel(insights = insights)

        vm.state.test {
            val data = awaitItem().data
            // Every increase from zero is infinite. Printing one would be a number nobody can read.
            assertThat(data.deltaPercent).isNull()
            assertThat(data.share.sharePercent).isEqualTo(50)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the share is null for a period with no spending at all`() = runTest {
        val vm = viewModel(insights = FakeInsights(share = CategoryShare(0L, 0, 0, 0L)))

        vm.state.test {
            // "0% of all spend" is a claim about a period that had spending. An empty month has
            // not earned it.
            assertThat(awaitItem().data.share.sharePercent).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the payee card asks for the row limit the card shows`() = runTest {
        val insights = FakeInsights()
        viewModel(insights = insights)
        assertThat(insights.lastLimit).isEqualTo(INSIGHT_PAYEE_LIMIT)
    }

    @Test
    fun `the insight screen asks about its own category, not the whole account`() = runTest {
        // Filtering the account-wide list instead would hide this category's own unusual charge
        // whenever a few larger ones elsewhere crowded it out of the limited result set — on the
        // one screen dedicated to that category.
        val found = FakeAnomalies()
        viewModel(anomalies = found)

        assertThat(found.scope).isEqualTo(AnomalyScope.OneCategory(7L))
    }

    @Test
    fun `the unmapped bucket asks about itself rather than about every category`() = runTest {
        // The sentinel arrives as a null category id, which is a category to ask about here, not a
        // missing argument — and the bucket users most need callouts from.
        val found = FakeAnomalies()
        viewModel(anomalies = found, categoryId = -1L, categoryName = null)

        assertThat(found.scope).isEqualTo(AnomalyScope.OneCategory(null))
    }

    @Test
    fun `the callout baseline stops where the viewed period starts`() = runTest {
        val found = FakeAnomalies()
        val preferences = FakeDashboardPreferences().apply { range.value = DashboardRange.ThisMonth }
        viewModel(anomalies = found, preferences = preferences)

        assertThat(found.baseline).isEqualTo(DateRange(utc(2026, 6, 1), utc(2026, 9, 1)))
    }

    @Test
    fun `dismissing a callout on this screen removes it`() = runTest {
        val found = FakeAnomalies(listOf(anomaly(1L), anomaly(2L)))
        val preferences = FakeDashboardPreferences()
        val vm = viewModel(anomalies = found, preferences = preferences)

        assertThat(vm.state.value.anomalies.map { it.transactionId }).isEqualTo(listOf(1L, 2L))

        vm.onAction(CategoryInsightAction.OnDismissAnomaly(2L))

        assertThat(vm.state.value.anomalies.map { it.transactionId }).isEqualTo(listOf(1L))
    }

    @Test
    fun `all time shows no callouts here either`() = runTest {
        val found = FakeAnomalies(listOf(anomaly(1L)))
        val preferences = FakeDashboardPreferences().apply { range.value = DashboardRange.AllTime }
        val vm = viewModel(anomalies = found, preferences = preferences)

        assertThat(vm.state.value.anomalies).isEqualTo(emptyList<SpendAnomaly>())
        assertThat(found.scope).isNull()
    }
}
