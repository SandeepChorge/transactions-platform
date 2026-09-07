package com.madtitan94.transactionsparser.feature.dashboard.presentation.insight

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madtitan94.transactionsparser.core.domain.datasource.AnomalyLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.CategoryInsightLocalDataSource
import com.madtitan94.transactionsparser.core.domain.model.AnomalyScope
import com.madtitan94.transactionsparser.core.domain.model.AnomalyThresholds
import com.madtitan94.transactionsparser.core.domain.model.CategoryShare
import com.madtitan94.transactionsparser.core.domain.model.DateRange
import com.madtitan94.transactionsparser.core.domain.model.PayeeTotal
import com.madtitan94.transactionsparser.core.domain.model.PeriodTotal
import com.madtitan94.transactionsparser.core.domain.model.SpendAnomaly
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardPreferences
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardRange
import com.madtitan94.transactionsparser.feature.dashboard.domain.anomalyBaseline
import com.madtitan94.transactionsparser.feature.dashboard.domain.previous
import com.madtitan94.transactionsparser.feature.dashboard.domain.resolve
import com.madtitan94.transactionsparser.feature.dashboard.presentation.navigation.CategoryInsightRoute
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Rows a card shows before "See all" takes over, matching the dashboard's own ranked lists. */
const val INSIGHT_PAYEE_LIMIT = 5

/** How far back the trend card looks, per the design's 3M / 6M / 1Y toggle. */
enum class TrendWindow(val months: Int) {
    THREE_MONTHS(3),
    SIX_MONTHS(6),
    ONE_YEAR(12);

    companion object {
        /**
         * Six months, matching the artboard's own selected chip.
         *
         * Three is barely a trend and twelve compresses the recent months a user actually asks
         * about into the right-hand third of the chart.
         */
        val Default = SIX_MONTHS
    }
}

/**
 * Everything the four cards read, for one category.
 *
 * One bundle rather than a field per card, for the reason `DashboardData` is one bundle: the header
 * and three of the four cards are all describing the same period, and independently refreshed
 * fields could leave the header stating one month's total above a chart drawing another's.
 */
data class CategoryInsightData(
    val share: CategoryShare = CategoryShare(0L, 0, 0, 0L),
    /** The same range one period earlier — what makes "18% more than last month" sayable. */
    val previousPaise: Long? = null,
    val dayTotals: List<PeriodTotal> = emptyList(),
    val monthTotals: List<PeriodTotal> = emptyList(),
    val payees: List<PayeeTotal> = emptyList()
) {
    /**
     * How the period compares with the one before it, as a signed whole percentage.
     *
     * Null when there is nothing to compare against — All time has no "before", and a previous
     * period of zero makes every increase infinite, so the honest answer is to drop the line rather
     * than print a number that cannot be read. Same rule as the dashboard hero's own delta, so the
     * two screens cannot phrase the same comparison differently.
     */
    val deltaPercent: Int?
        get() {
            val before = previousPaise ?: return null
            if (before <= 0L) return null
            return (((share.totalPaise - before).toDouble() / before) * 100).toInt()
        }
}

data class CategoryInsightState(
    val isLoading: Boolean = true,
    /** Null is the unmapped bucket, not a missing argument; see [CategoryInsightLocalDataSource]. */
    val categoryId: Long? = null,
    /** Null for the unmapped bucket, where the screen supplies its own *Uncategorised* label. */
    val categoryName: String? = null,
    val range: DashboardRange = DashboardRange.Default,
    val trendWindow: TrendWindow = TrendWindow.Default,
    val data: CategoryInsightData = CategoryInsightData(),
    /** Charges out of character for *this* category, with the ones already waved off removed. */
    val anomalies: List<SpendAnomaly> = emptyList(),
    /** Open while the shared range picker is on screen. */
    val isPickingCustomRange: Boolean = false
)

sealed interface CategoryInsightAction {
    data class OnTrendWindowSelected(val window: TrendWindow) : CategoryInsightAction
    data class OnRangeSelected(val range: DashboardRange) : CategoryInsightAction
    data object OnCustomRangeClick : CategoryInsightAction
    data class OnCustomRangePicked(val fromMillis: Long, val toMillisInclusive: Long) :
        CategoryInsightAction
    data object OnCustomRangeDismiss : CategoryInsightAction
    data class OnDismissAnomaly(val transactionId: Long) : CategoryInsightAction
}

/**
 * One category, four angles.
 *
 * **The range is the dashboard's own, read from and written back to the same preference.** This is
 * deliberate and is what "based on the filter selected" means in requirement 4: there is one period
 * the user is thinking in, not a dashboard period and a separate insight period that silently
 * disagree. Changing it here changes it on Home too, which is the behaviour a shared filter has.
 *
 * The trend card is the one exception, and it is local state rather than a preference: "how is this
 * going over time" is a longer question than any single period answers, so it looks past the filter
 * at a months-back window instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryInsightViewModel(
    savedStateHandle: SavedStateHandle,
    private val insights: CategoryInsightLocalDataSource,
    private val anomalies: AnomalyLocalDataSource,
    private val preferences: DashboardPreferences,
    /** Injected so a test can pin "today" instead of depending on the machine's calendar. */
    private val today: () -> LocalDate = LocalDate::now
) : ViewModel() {

    /**
     * The route's arguments, read by key rather than through `toRoute<CategoryInsightRoute>()`.
     *
     * Same reason the builder does it: `toRoute` decodes via `android.os.Bundle`, which is not
     * mocked on the JVM, so every test here would fail inside navigation rather than on anything
     * this class does. The keys are named on the route so the two cannot drift apart.
     */
    private val categoryId: Long? =
        savedStateHandle.get<Long>(CategoryInsightRoute.ID_ARG)
            ?.takeUnless { it == CategoryInsightRoute.UNCATEGORISED }

    private val categoryName: String? = savedStateHandle[CategoryInsightRoute.NAME_ARG]

    private val _state = MutableStateFlow(
        CategoryInsightState(categoryId = categoryId, categoryName = categoryName)
    )
    val state = _state.asStateFlow()

    private val trendWindow = MutableStateFlow(TrendWindow.Default)

    init {
        viewModelScope.launch {
            preferences.observeRange().collect { range ->
                _state.update { it.copy(range = range) }
            }
        }

        viewModelScope.launch {
            combine(preferences.observeRange(), trendWindow) { range, window -> range to window }
                .flatMapLatest { (range, window) -> observeData(range, window) }
                .collect { data -> _state.update { it.copy(isLoading = false, data = data) } }
        }

        // A separate collector rather than a sixth source in `observeData`'s combine. The five
        // there are the card data and arrive together on purpose — a share and the days beneath it
        // must describe one moment — while a callout is an independent claim about the same period
        // and has no such pairing to preserve. `combine` also stops at five arguments, and nesting
        // one to carry a banner would obscure why the other five are grouped at all.
        viewModelScope.launch {
            preferences.observeRange()
                .flatMapLatest { range -> observeAnomalies(range) }
                .collect { found -> _state.update { it.copy(anomalies = found) } }
        }
    }

    /**
     * Unusual charges inside this one category, over the shared range.
     *
     * Scoped to the category in SQL rather than filtered out of the dashboard's account-wide list.
     * The account-wide read is limited, so filtering it here would silently hide a category's own
     * anomaly whenever a few larger ones elsewhere crowded it out of the top of the list — and the
     * screen that would hide it is the one screen dedicated to that category.
     */
    private fun observeAnomalies(range: DashboardRange): Flow<List<SpendAnomaly>> {
        val on = today()
        val baseline = range.anomalyBaseline(on, AnomalyThresholds.BASELINE_MONTHS)
            ?: return flowOf(emptyList())

        return combine(
            anomalies.observeAnomalies(
                range = range.resolve(on),
                baseline = baseline,
                scope = AnomalyScope.OneCategory(categoryId),
                multiplier = AnomalyThresholds.MULTIPLIER,
                minSampleCount = AnomalyThresholds.MIN_SAMPLE_COUNT,
                minAmountPaise = AnomalyThresholds.MIN_AMOUNT_PAISE,
                limit = AnomalyThresholds.FETCH_LIMIT
            ),
            preferences.observeDismissedAnomalies()
        ) { found, dismissed ->
            found.filterNot { it.transactionId in dismissed }.take(AnomalyThresholds.LIMIT)
        }
    }

    private fun observeData(range: DashboardRange, window: TrendWindow) = run {
        val on = today()
        val current = range.resolve(on)
        val before = range.previous(on)

        combine(
            insights.observeShare(categoryId, current),
            insights.observeDayTotals(categoryId, current),
            insights.observeMonthTotals(categoryId, trendRange(on, window)),
            insights.observeTopPayees(categoryId, current, INSIGHT_PAYEE_LIMIT),
            // A missing "before" still has to emit, or `combine` never produces a first value and
            // the screen stays on its spinner for a range that is otherwise perfectly loadable.
            previousShare(before)
        ) { share, days, months, payees, previous ->
            CategoryInsightData(
                share = share,
                previousPaise = previous?.totalPaise,
                dayTotals = days,
                monthTotals = months,
                payees = payees
            )
        }
    }

    /**
     * The same category over the period before this one, or a single null when there is no before.
     *
     * It has to emit either way: `combine` produces nothing until every source has a value, so an
     * All-time range — which has no previous period — would otherwise leave the screen on its
     * spinner forever on a perfectly loadable set of data.
     */
    private fun previousShare(before: DateRange?): Flow<CategoryShare?> =
        if (before == null) flowOf(null) else insights.observeShare(categoryId, before)

    /**
     * The window the trend card asks for: whole months, ending with the month in progress.
     *
     * Built in UTC because statement timestamps are the printed wall clock stored as-if-UTC, the
     * same reason `DashboardRange.resolve` does its arithmetic there. The upper bound is the first
     * instant of *next* month, so the current month is included whole rather than truncated at
     * today — a half-finished month drawn against complete ones would read as a collapse in spending
     * on the first of every month.
     */
    private fun trendRange(on: LocalDate, window: TrendWindow): DateRange {
        val firstOfThisMonth = on.withDayOfMonth(1)
        val from = firstOfThisMonth.minusMonths((window.months - 1).toLong())
        return DateRange(
            fromMillis = from.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            toMillisExclusive = firstOfThisMonth.plusMonths(1).atStartOfDay()
                .toInstant(ZoneOffset.UTC).toEpochMilli()
        )
    }

    /** The window currently on screen, for anything that needs to name or bucket it. */
    fun resolvedRange(): DateRange = _state.value.range.resolve(today())

    /** Today, in the same clock the buckets use, so the screen and the query agree on the month. */
    fun currentDate(): LocalDate = today()

    fun onAction(action: CategoryInsightAction) {
        when (action) {
            is CategoryInsightAction.OnTrendWindowSelected -> {
                _state.update { it.copy(trendWindow = action.window) }
                trendWindow.value = action.window
            }

            is CategoryInsightAction.OnDismissAnomaly -> viewModelScope.launch {
                preferences.dismissAnomaly(action.transactionId)
            }
            is CategoryInsightAction.OnRangeSelected -> viewModelScope.launch {
                preferences.setRange(action.range)
            }

            CategoryInsightAction.OnCustomRangeClick ->
                _state.update { it.copy(isPickingCustomRange = true) }

            CategoryInsightAction.OnCustomRangeDismiss ->
                _state.update { it.copy(isPickingCustomRange = false) }

            is CategoryInsightAction.OnCustomRangePicked -> {
                _state.update { it.copy(isPickingCustomRange = false) }
                viewModelScope.launch {
                    preferences.setRange(
                        DashboardRange.Custom(
                            fromDate = action.fromMillis.toUtcDate(),
                            toDateInclusive = action.toMillisInclusive.toUtcDate()
                        )
                    )
                }
            }
        }
    }
}

/**
 * Material's date picker hands back UTC midnight for the day the user tapped, which is already the
 * clock this app's rows are stored in — so this is a read, not a conversion.
 */
private fun Long.toUtcDate(): LocalDate =
    java.time.Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
