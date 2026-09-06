package com.madtitan94.transactionsparser.feature.dashboard.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madtitan94.transactionsparser.core.designsystem.charts.ChartSlice
import com.madtitan94.transactionsparser.core.designsystem.charts.CategoryAmount
import com.madtitan94.transactionsparser.core.designsystem.charts.buildCategorySlices
import com.madtitan94.transactionsparser.core.designsystem.components.EmptyState
import com.madtitan94.transactionsparser.core.designsystem.components.LoadingIndicator
import com.madtitan94.transactionsparser.core.designsystem.theme.AppDimens
import com.madtitan94.transactionsparser.core.designsystem.theme.AppShapes
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTypography
import com.madtitan94.transactionsparser.core.domain.model.DateRange
import com.madtitan94.transactionsparser.core.domain.model.PayeeTotal
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardDefinition
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardId
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardRange
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardWidgetConfig
import com.madtitan94.transactionsparser.feature.dashboard.domain.HeroMeasure
import com.madtitan94.transactionsparser.feature.dashboard.domain.PayeeScope
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.CategoryDonutWidget
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.CategoryRankedWidget
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.InsightBanner
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.MappedShareHero
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.NetSpendHero
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.PayeeRankedWidget
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.TrendWidget
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.TypeTilesWidget
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.buildTrendSeries
import org.koin.androidx.compose.koinViewModel

@Composable
fun DashboardRoot(
    onOpenPayee: (normalizedPayee: String, rawPayee: String) -> Unit,
    onOpenCategories: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DashboardScreen(
        state = state,
        rangeWindow = viewModel.resolvedRange(),
        onAction = viewModel::onAction,
        onOpenPayee = onOpenPayee,
        onOpenCategories = onOpenCategories
    )
}

@Composable
fun DashboardScreen(
    state: DashboardState,
    rangeWindow: DateRange,
    onAction: (DashboardAction) -> Unit,
    onOpenPayee: (String, String) -> Unit,
    onOpenCategories: () -> Unit
) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(AppTheme.colors.screen)) {
        DashboardHeader(
            title = state.current?.id?.let { stringResource(dashboardNameRes(it)) }.orEmpty(),
            range = state.range,
            onRangeClick = { sheetOpen = true }
        )

        if (state.dashboards.size > 1) {
            DashboardChipRow(
                dashboards = state.dashboards,
                selected = state.selected,
                onSelect = { onAction(DashboardAction.OnDashboardSelected(it)) }
            )
        }

        when {
            state.isLoading -> LoadingIndicator()
            state.dashboards.isEmpty() -> EmptyState(
                title = stringResource(R.string.dash_empty_title),
                description = stringResource(R.string.dash_empty_body)
            )

            else -> DashboardPager(
                state = state,
                rangeWindow = rangeWindow,
                onAction = onAction,
                onOpenPayee = onOpenPayee,
                onOpenCategories = onOpenCategories
            )
        }
    }

    if (sheetOpen) {
        RangeSheet(
            current = state.range,
            onSelect = {
                onAction(DashboardAction.OnRangeSelected(it))
                sheetOpen = false
            },
            onPickCustom = {
                sheetOpen = false
                onAction(DashboardAction.OnCustomRangeClick)
            },
            onDismiss = { sheetOpen = false }
        )
    }

    if (state.isPickingCustomRange) {
        CustomRangePickerDialog(
            initial = state.range,
            onPicked = { from, to -> onAction(DashboardAction.OnCustomRangePicked(from, to)) },
            onDismiss = { onAction(DashboardAction.OnCustomRangeDismiss) }
        )
    }
}

@Composable
private fun DashboardHeader(
    title: String,
    range: DashboardRange,
    onRangeClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = AppDimens.screenHorizontalPadding,
                end = AppDimens.screenHorizontalPadding,
                top = 24.dp,
                bottom = 12.dp
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = AppTypography.title, color = AppTheme.colors.textPrimary)
        RangeChip(range = range, onClick = onRangeClick)
    }
}

/**
 * The dashboard switcher.
 *
 * Chips rather than a tab bar or a dot indicator: the set is user-controlled and can hold anywhere
 * from one to seven entries, and chips are the only one of the three that reads correctly at both
 * ends of that range. Swiping the pager moves the selection too, so the chips are a shortcut rather
 * than the only way across.
 */
@Composable
private fun DashboardChipRow(
    dashboards: List<DashboardDefinition>,
    selected: DashboardId?,
    onSelect: (DashboardId) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = AppDimens.screenHorizontalPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(dashboards, key = { it.id }) { definition ->
            val isSelected = definition.id == selected
            Text(
                text = stringResource(dashboardNameRes(definition.id)),
                style = AppTypography.row,
                color = if (isSelected) AppTheme.colors.onAccent else AppTheme.colors.textSecondary,
                modifier = Modifier
                    .background(
                        if (isSelected) AppTheme.colors.accent else AppTheme.colors.surfaceAlt,
                        AppShapes.button
                    )
                    .clickable { onSelect(definition.id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun DashboardPager(
    state: DashboardState,
    rangeWindow: DateRange,
    onAction: (DashboardAction) -> Unit,
    onOpenPayee: (String, String) -> Unit,
    onOpenCategories: () -> Unit
) {
    val startPage = state.dashboards.indexOfFirst { it.id == state.selected }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = startPage) { state.dashboards.size }

    // The chips and the pager are two views of one selection, so each follows the other. Without
    // the guards this is an infinite loop: settling the pager sets the selection, which scrolls the
    // pager, which settles it again.
    LaunchedEffect(state.selected) {
        val target = state.dashboards.indexOfFirst { it.id == state.selected }
        if (target >= 0 && target != pagerState.currentPage) pagerState.animateScrollToPage(target)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            state.dashboards.getOrNull(page)?.let { settled ->
                if (settled.id != state.selected) onAction(DashboardAction.OnDashboardSelected(settled.id))
            }
        }
    }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        val definition = state.dashboards[page]
        DashboardWidgetList(
            definition = definition,
            state = state,
            rangeWindow = rangeWindow,
            onOpenPayee = onOpenPayee,
            onOpenCategories = onOpenCategories
        )
    }
}

/**
 * The renderer: an ordered list of widget configs in, a scrolling dashboard out.
 *
 * This is the only place that knows how to turn a [DashboardWidgetConfig] into pixels. Every
 * dashboard goes through it, so a dashboard the user composes in a later phase renders the same way
 * a shipped one does, with no code of its own.
 */
@Composable
private fun DashboardWidgetList(
    definition: DashboardDefinition,
    state: DashboardState,
    rangeWindow: DateRange,
    onOpenPayee: (String, String) -> Unit,
    onOpenCategories: () -> Unit
) {
    val data = state.data
    val uncategorised = stringResource(R.string.dash_uncategorised)
    val label = rangeLabel(state.range)

    val slices: List<ChartSlice> = remember(data.categoryTotals, uncategorised) {
        buildCategorySlices(
            amounts = data.categoryTotals.map { total ->
                CategoryAmount(
                    categoryId = total.categoryId,
                    label = total.categoryName ?: uncategorised,
                    amountPaise = total.totalPaise
                )
            },
            uncategorisedLabel = uncategorised
        )
    }

    val trend = remember(data.dayTotals, rangeWindow) {
        buildTrendSeries(data.dayTotals, rangeWindow.fromMillis, rangeWindow.toMillisExclusive)
    }

    val topPayees = remember(data.payees) { data.payees.take(DASHBOARD_ROW_LIMIT) }
    val unmappedPayees = remember(data.payees) {
        data.payees.filter { it.isUnmapped }.take(DASHBOARD_ROW_LIMIT)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppDimens.screenHorizontalPadding,
            end = AppDimens.screenHorizontalPadding,
            top = 16.dp,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(definition.widgets, key = { it.id.name + it.hashCode() }) { widget ->
            when (widget) {
                is DashboardWidgetConfig.Hero -> when (widget.measure) {
                    HeroMeasure.NET_SPEND -> NetSpendHero(
                        rangeLabel = label,
                        totalPaise = data.debitPaise,
                        deltaPercent = data.deltaPercent,
                        biggestMover = data.biggestMover,
                        payeeCount = data.payeeSummary.payeeCount,
                        slices = slices,
                        isEmpty = data.typeTotals.debitCount == 0
                    )

                    HeroMeasure.MAPPED_SHARE -> MappedShareHero(
                        rangeLabel = label,
                        mappedPercent = data.mappedPercent,
                        totalPaise = data.debitPaise
                    )
                }

                // Absent rather than congratulatory when everything has a name: a permanent
                // "all done" card would spend a slot on the dashboard saying nothing is wrong.
                DashboardWidgetConfig.InsightBanner -> if (data.payeeSummary.unmappedPayeeCount > 0) {
                    InsightBanner(
                        unmappedPaise = data.payeeSummary.unmappedPaise,
                        payeeCount = data.payeeSummary.unmappedPayeeCount,
                        onClick = onOpenCategories
                    )
                }

                DashboardWidgetConfig.TypeTiles -> TypeTilesWidget(totals = data.typeTotals)

                // Below three points there is no shape to show, so the widget is omitted rather
                // than drawn as a line between two dots.
                DashboardWidgetConfig.TrendChart -> if (trend.isDrawable) {
                    TrendWidget(series = trend)
                }

                DashboardWidgetConfig.CategoryDonut -> CategoryDonutWidget(
                    slices = slices,
                    totalPaise = data.debitPaise,
                    rangeLabel = label
                )

                is DashboardWidgetConfig.CategoryRanked -> CategoryRankedWidget(
                    slices = slices,
                    valueFormat = widget.value
                )

                is DashboardWidgetConfig.PayeeRanked -> PayeeRankedWidget(
                    payees = when (widget.scope) {
                        PayeeScope.ALL -> topPayees
                        PayeeScope.UNMAPPED_ONLY -> unmappedPayees
                    },
                    scope = widget.scope,
                    onPayeeClick = { payee: PayeeTotal ->
                        onOpenPayee(payee.normalizedName, payee.statementName)
                    }
                )
            }
        }
    }
}

private fun dashboardNameRes(id: DashboardId): Int = when (id) {
    DashboardId.PULSE -> R.string.dash_pulse
    DashboardId.CATEGORIES -> R.string.dash_categories
    DashboardId.MAPPING_HEALTH -> R.string.dash_mapping_health
    DashboardId.PAYEES -> R.string.dash_payees
}
