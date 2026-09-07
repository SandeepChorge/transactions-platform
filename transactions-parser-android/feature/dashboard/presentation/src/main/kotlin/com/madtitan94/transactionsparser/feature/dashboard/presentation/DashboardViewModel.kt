package com.madtitan94.transactionsparser.feature.dashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madtitan94.transactionsparser.core.domain.datasource.DashboardLocalDataSource
import com.madtitan94.transactionsparser.core.domain.model.CategoryTotal
import com.madtitan94.transactionsparser.core.domain.model.DateRange
import com.madtitan94.transactionsparser.core.domain.model.DayTotal
import com.madtitan94.transactionsparser.core.domain.model.PayeeSummary
import com.madtitan94.transactionsparser.core.domain.model.PayeeTotal
import com.madtitan94.transactionsparser.core.domain.model.TypeTotals
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardDefinition
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardKey
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardPreferences
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardRange
import com.madtitan94.transactionsparser.feature.dashboard.domain.lengthInDays
import com.madtitan94.transactionsparser.feature.dashboard.domain.previous
import com.madtitan94.transactionsparser.feature.dashboard.domain.resolve
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * How many payees a ranking asks for.
 *
 * Larger than the five a card shows, because the unmapped-only ranking filters this list down after
 * the fact — asking for five would mean five *overall*, of which perhaps one is unmapped, and the
 * Mapping health work queue would look almost empty on an account where it is anything but.
 */
private const val PAYEE_FETCH_LIMIT = 40

/** What a card shows before "See all" takes over. From `DashboardSpec` W3/W10. */
const val DASHBOARD_ROW_LIMIT = 5

/**
 * Everything the widgets read, for one range.
 *
 * One bundle rather than a field per widget because every widget on every dashboard is looking at
 * the same period: if these could be refreshed independently, a donut and the ranked list beside it
 * could be showing two different months for a frame, and that is exactly the disagreement the
 * hoisted range exists to prevent.
 */
data class DashboardData(
    val dayTotals: List<DayTotal> = emptyList(),
    val typeTotals: TypeTotals = TypeTotals(),
    val categoryTotals: List<CategoryTotal> = emptyList(),
    val payees: List<PayeeTotal> = emptyList(),
    val payeeSummary: PayeeSummary = PayeeSummary(),
    /** Spend in the equal-length period before this one, or null when there is no "before". */
    val previousDebitPaise: Long? = null,
    /** The same categories, one period earlier — what makes "mostly Groceries" sayable. */
    val previousCategoryTotals: List<CategoryTotal> = emptyList()
) {
    /** Total spend in the range. Taken from the type split so it cannot drift from the tiles. */
    val debitPaise: Long get() = typeTotals.debitPaise

    /**
     * Share of spend that has a payee name on it, as a whole percentage.
     *
     * By value, per `DashboardSpec` W8. A period with no spend at all reads as 100%: nothing is
     * unnamed, and showing 0% for an empty month would invent a data-quality problem that does not
     * exist.
     */
    val mappedPercent: Int
        get() = when {
            debitPaise <= 0L -> 100
            else -> (((debitPaise - payeeSummary.unmappedPaise).toDouble() / debitPaise) * 100)
                .toInt()
                .coerceIn(0, 100)
        }

    /**
     * How the period compares with the one before it, as a signed whole percentage.
     *
     * Null when there is nothing to compare against — no previous period, or a previous period of
     * zero. A previous period of zero is the important one: every increase from zero is infinite, so
     * the honest answer is to drop the line rather than print a number that cannot be interpreted.
     */
    val deltaPercent: Int?
        get() {
            val before = previousDebitPaise ?: return null
            if (before <= 0L) return null
            return (((debitPaise - before).toDouble() / before) * 100).toInt()
        }

    /**
     * The category that moved the most between the two periods — the "mostly Groceries" clause.
     *
     * Largest absolute increase rather than largest category: naming the biggest category every
     * month says nothing, while naming what *changed* explains the delta the sentence just stated.
     * Only increases qualify when spend went up, and only decreases when it went down, so the clause
     * can never contradict the arrow beside it.
     */
    val biggestMover: String?
        get() {
            val delta = deltaPercent ?: return null
            val before = previousCategoryTotals.associate { it.categoryId to it.totalPaise }
            val movers = categoryTotals
                .filter { it.categoryId != null && it.categoryName != null }
                .map { it.categoryName!! to (it.totalPaise - (before[it.categoryId] ?: 0L)) }
            val candidates = if (delta >= 0) movers.filter { it.second > 0L } else movers.filter { it.second < 0L }
            return candidates.maxByOrNull { kotlin.math.abs(it.second) }?.first
        }
}

data class DashboardState(
    val isLoading: Boolean = true,
    val range: DashboardRange = DashboardRange.Default,
    val dashboards: List<DashboardDefinition> = emptyList(),
    val selected: DashboardKey? = null,
    val data: DashboardData = DashboardData(),
    /** Open while the custom-range picker is on screen. */
    val isPickingCustomRange: Boolean = false
) {
    val current: DashboardDefinition? get() = dashboards.firstOrNull { it.key == selected }
}

sealed interface DashboardAction {
    data class OnDashboardSelected(val key: DashboardKey) : DashboardAction
    data class OnRangeSelected(val range: DashboardRange) : DashboardAction
    data object OnCustomRangeClick : DashboardAction
    data class OnCustomRangePicked(val fromMillis: Long, val toMillisInclusive: Long) : DashboardAction
    data object OnCustomRangeDismiss : DashboardAction
}

/**
 * Owns the one range every widget reads, and the queries that range implies.
 *
 * The range is hoisted here rather than held per widget for the reason `DashboardSpec` §3 gives:
 * one period applies to the whole screen. Persisting it makes it a habit rather than a setting —
 * a user who reads their statements by month should not re-pick "This month" every launch.
 *
 * All five aggregates are queried for every dashboard, not just the ones the visible dashboard
 * draws. They are indexed aggregates over one account's rows for one period, so the cost is small,
 * and paying it up front is what makes swiping between dashboards instant instead of showing a
 * spinner on each one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val dashboards: DashboardLocalDataSource,
    private val preferences: DashboardPreferences,
    /** Injected so a test can pin "today" instead of depending on the machine's calendar. */
    private val today: () -> LocalDate = LocalDate::now
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.observeLayout().collect { layout ->
                val definitions = layout.enabled
                _state.update { current ->
                    current.copy(
                        dashboards = definitions,
                        // Keep the user where they are while they are looking: the starred
                        // dashboard decides where the screen *opens*, not where it jumps to when a
                        // preference changes underneath them.
                        selected = current.selected?.takeIf { chosen -> definitions.any { it.key == chosen } }
                            ?: layout.defaultDashboard
                    )
                }
            }
        }

        viewModelScope.launch {
            preferences.observeRange().collect { range ->
                _state.update { it.copy(range = range) }
            }
        }

        viewModelScope.launch {
            preferences.observeRange()
                .flatMapLatest { range -> observeData(range) }
                .collect { data -> _state.update { it.copy(isLoading = false, data = data) } }
        }
    }

    private fun observeData(range: DashboardRange): kotlinx.coroutines.flow.Flow<DashboardData> {
        val on = today()
        val window = range.resolve(on)
        val before = range.previous(on)

        val current = combine(
            dashboards.observeDayTotals(window),
            dashboards.observeTypeTotals(window),
            dashboards.observeCategoryTotals(window),
            dashboards.observeTopPayees(window, PAYEE_FETCH_LIMIT),
            dashboards.observePayeeSummary(window)
        ) { days, types, categories, payees, summary ->
            DashboardData(
                dayTotals = days,
                typeTotals = types,
                categoryTotals = categories,
                payees = payees,
                payeeSummary = summary
            )
        }

        val comparison = when (before) {
            null -> flowOf(null to emptyList<CategoryTotal>())
            else -> combine(
                dashboards.observeTypeTotals(before),
                dashboards.observeCategoryTotals(before)
            ) { types, categories -> types.debitPaise as Long? to categories }
        }

        return combine(current, comparison) { data, (previousDebit, previousCategories) ->
            data.copy(
                previousDebitPaise = previousDebit,
                previousCategoryTotals = previousCategories
            )
        }
    }

    /** The window currently on screen, for anything that needs to name or re-query it. */
    fun resolvedRange(): DateRange = _state.value.range.resolve(today())

    /** How many days the range spans, or null for All time. The trend chart buckets against it. */
    fun rangeLengthInDays(): Int? = _state.value.range.lengthInDays(today())

    fun onAction(action: DashboardAction) {
        when (action) {
            is DashboardAction.OnDashboardSelected -> _state.update { it.copy(selected = action.key) }
            is DashboardAction.OnRangeSelected -> viewModelScope.launch {
                preferences.setRange(action.range)
            }
            DashboardAction.OnCustomRangeClick -> _state.update { it.copy(isPickingCustomRange = true) }
            DashboardAction.OnCustomRangeDismiss -> _state.update { it.copy(isPickingCustomRange = false) }
            is DashboardAction.OnCustomRangePicked -> {
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
    java.time.Instant.ofEpochMilli(this).atZone(java.time.ZoneOffset.UTC).toLocalDate()
