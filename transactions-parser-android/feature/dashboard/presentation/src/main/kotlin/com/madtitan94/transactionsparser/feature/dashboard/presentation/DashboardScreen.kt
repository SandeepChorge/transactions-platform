package com.madtitan94.transactionsparser.feature.dashboard.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardKey
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardRange
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardWidgetConfig
import com.madtitan94.transactionsparser.feature.dashboard.domain.HeroMeasure
import com.madtitan94.transactionsparser.feature.dashboard.domain.PayeeScope
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.AnomalyCallout
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.AnomalyCalloutUi
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.CategoryDonutWidget
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.CategoryRankedWidget
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.InsightBanner
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.MappedShareHero
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.NetSpendHero
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.PayeeRankedWidget
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.TrendWidget
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.TypeTilesWidget
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.buildTrendSeries
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.toCalloutUi
import org.koin.androidx.compose.koinViewModel

@Composable
fun DashboardRoot(
    onOpenPayee: (normalizedPayee: String, rawPayee: String) -> Unit,
    onOpenCategories: () -> Unit,
    onManageDashboards: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCategoryInsight: (categoryId: Long?, categoryName: String?) -> Unit,
    viewModel: DashboardViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DashboardScreen(
        state = state,
        rangeWindow = viewModel.resolvedRange(),
        onAction = viewModel::onAction,
        onOpenPayee = onOpenPayee,
        onOpenCategories = onOpenCategories,
        onManageDashboards = onManageDashboards,
        onOpenSearch = onOpenSearch,
        onOpenCategoryInsight = onOpenCategoryInsight
    )
}

@Composable
fun DashboardScreen(
    state: DashboardState,
    rangeWindow: DateRange,
    onAction: (DashboardAction) -> Unit,
    onOpenPayee: (String, String) -> Unit,
    onOpenCategories: () -> Unit,
    onManageDashboards: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCategoryInsight: (categoryId: Long?, categoryName: String?) -> Unit
) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(AppTheme.colors.screen)) {
        DashboardHeader(
            title = state.current?.let { dashboardName(it) }.orEmpty(),
            range = state.range,
            onRangeClick = { sheetOpen = true },
            onSearchClick = onOpenSearch,
            onManageClick = onManageDashboards
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
                onOpenCategories = onOpenCategories,
                onOpenCategoryInsight = onOpenCategoryInsight
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

/**
 * How much of the header row the range chip may take before the title starts losing letters.
 *
 * Measured, not guessed: 164dp is what a custom range needs to print both dates in full over two
 * lines, chevron and padding included. At 150dp it dropped the closing year — "30 May 20…" — and the
 * dates are the half worth keeping, because the tab row directly below already spells the dashboard
 * out in full while nothing else on screen carries the range. Every fixed range is far shorter and
 * never reaches the cap, so nothing truncates unless a custom range is set on a narrow screen.
 */
private val RANGE_CHIP_MAX_WIDTH = 164.dp

@Composable
private fun DashboardHeader(
    title: String,
    range: DashboardRange,
    onRangeClick: () -> Unit,
    onSearchClick: () -> Unit,
    onManageClick: () -> Unit
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
        // spacedBy with a weighted title, not SpaceBetween: the weight fills the leftover so the
        // chip still sits hard right, and the gap is reserved before the title is measured. Under
        // SpaceBetween a 360dp phone rendered "Mapping health" touching a custom range's chip.
        horizontalArrangement = Arrangement.spacedBy(AppDimens.labelToAmountGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shrunk to fit rather than ellipsised. The header carries three things now — name, period
        // and the manage button — and on a 360dp phone that left "Mapping health" as "Mapping …",
        // which reads as a rendering fault rather than as a long name. `DashboardSpec` W14 does say
        // long names ellipsise, but it says so of a header with no icon button in it; giving up type
        // size is the smaller loss, and every shipped name fits well above the floor.
        BasicText(
            text = title,
            modifier = Modifier.weight(1f),
            style = AppTypography.title.copy(color = AppTheme.colors.textPrimary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 15.sp,
                maxFontSize = AppTypography.title.fontSize,
                stepSize = 1.sp
            )
        )
        // Capped, because the chip is unweighted and so is measured first: left uncapped, a custom
        // range's "01 May 2026 – 30 May 2026" would claim the row on one line and leave the title a
        // single letter. Inside the cap it wraps to two lines and keeps every date.
        RangeChip(
            range = range,
            onClick = onRangeClick,
            modifier = Modifier.widthIn(max = RANGE_CHIP_MAX_WIDTH)
        )
        // Search lives in the header rather than the bottom bar. The bar's five destinations are
        // places the app *is*; search is something the user does to what is already there, and it
        // has to be reachable from the screen they land on rather than costing a tab of its own.
        IconButton(onClick = onSearchClick) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.dash_search),
                tint = AppTheme.colors.textSecondary
            )
        }
        // `DashboardSpec` W14's second header control. It is the only route to the manage screen
        // that does not go through Settings, and the one a user reaches for the moment the chip row
        // shows a dashboard they do not want.
        IconButton(onClick = onManageClick) {
            Icon(
                imageVector = Icons.Default.DashboardCustomize,
                contentDescription = stringResource(R.string.dash_manage_title),
                tint = AppTheme.colors.textSecondary
            )
        }
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
    selected: DashboardKey?,
    onSelect: (DashboardKey) -> Unit
) {
    // Follows the selection rather than sitting still. With four dashboards every chip fitted on an
    // artboard-width screen; a fifth does not, and swiping the pager to one that is off the end of
    // the row would leave the chips showing a selection the user cannot see.
    val listState = rememberLazyListState()
    LaunchedEffect(selected, dashboards) {
        val target = dashboards.indexOfFirst { it.key == selected }
        if (target >= 0) listState.animateScrollToItem(target)
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = AppDimens.screenHorizontalPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(dashboards, key = { it.key.storageId }) { definition ->
            val isSelected = definition.key == selected
            Text(
                text = dashboardName(definition),
                style = AppTypography.row,
                color = if (isSelected) AppTheme.colors.onAccent else AppTheme.colors.textSecondary,
                modifier = Modifier
                    .background(
                        if (isSelected) AppTheme.colors.accent else AppTheme.colors.surfaceAlt,
                        AppShapes.button
                    )
                    .clickable { onSelect(definition.key) }
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
    onOpenCategories: () -> Unit,
    onOpenCategoryInsight: (categoryId: Long?, categoryName: String?) -> Unit
) {
    val startPage = state.dashboards.indexOfFirst { it.key == state.selected }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = startPage) { state.dashboards.size }

    // The chips and the pager are two views of one selection, so each follows the other. Without
    // the guards this is an infinite loop: settling the pager sets the selection, which scrolls the
    // pager, which settles it again.
    LaunchedEffect(state.selected) {
        val target = state.dashboards.indexOfFirst { it.key == state.selected }
        if (target >= 0 && target != pagerState.currentPage) pagerState.animateScrollToPage(target)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            state.dashboards.getOrNull(page)?.let { settled ->
                if (settled.key != state.selected) onAction(DashboardAction.OnDashboardSelected(settled.key))
            }
        }
    }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        val definition = state.dashboards[page]
        DashboardWidgetList(
            definition = definition,
            state = state,
            rangeWindow = rangeWindow,
            onAction = onAction,
            onOpenPayee = onOpenPayee,
            onOpenCategories = onOpenCategories,
            onOpenCategoryInsight = onOpenCategoryInsight
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
    onAction: (DashboardAction) -> Unit,
    onOpenPayee: (String, String) -> Unit,
    onOpenCategories: () -> Unit,
    onOpenCategoryInsight: (categoryId: Long?, categoryName: String?) -> Unit
) {
    val data = state.data
    val uncategorised = stringResource(R.string.dash_uncategorised)
    val other = stringResource(R.string.dash_other)
    val label = rangeLabel(state.range)
    val caption = rangeCaption(state.range)

    val slices: List<ChartSlice> = remember(data.categoryTotals, uncategorised, other) {
        buildCategorySlices(
            amounts = data.categoryTotals.map { total ->
                CategoryAmount(
                    categoryId = total.categoryId,
                    label = total.categoryName ?: uncategorised,
                    amountPaise = total.totalPaise
                )
            },
            uncategorisedLabel = uncategorised,
            otherLabel = other
        )
    }

    val trend = remember(data.dayTotals, rangeWindow) {
        buildTrendSeries(data.dayTotals, rangeWindow.fromMillis, rangeWindow.toMillisExclusive)
    }

    val anomalyCallouts = data.anomalies.map { it.toCalloutUi() }

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
        // Above the widgets rather than among them, and on every dashboard rather than as a widget
        // the user opts into. A callout is not a view of the period the way a chart is — it is the
        // app saying something happened — so burying it behind a dashboard the user has switched off
        // would mean the one message worth interrupting for is the one they never see.
        items(anomalyCallouts, key = { "anomaly-" + it.transactionId }) { callout ->
            AnomalyCallout(
                callout = callout,
                onDismiss = { id -> onAction(DashboardAction.OnDismissAnomaly(id)) },
                onClick = { onOpenPayee(callout.normalizedName, callout.statementName) }
            )
        }

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

                // A slice and a ranked row are two ways of pointing at the same category, so both
                // open the same screen. "Other" is several categories summed and opens nothing —
                // `openCategory` drops it rather than guessing which of them the user meant.
                DashboardWidgetConfig.CategoryDonut -> CategoryDonutWidget(
                    slices = slices,
                    totalPaise = data.debitPaise,
                    rangeCaption = caption,
                    onSliceClick = { index ->
                        slices.getOrNull(index)?.openCategory(onOpenCategoryInsight)
                    }
                )

                is DashboardWidgetConfig.CategoryRanked -> CategoryRankedWidget(
                    slices = slices,
                    valueFormat = widget.value,
                    onRowClick = { index ->
                        slices.getOrNull(index)?.openCategory(onOpenCategoryInsight)
                    }
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

/**
 * Opens the one category a slice stands for, if it stands for exactly one.
 *
 * Three cases, and only two of them navigate. A named slice carries its own id. The uncategorised
 * slice carries none and is still openable — the unmapped bucket is a real place, and the slice most
 * worth tapping is the one telling the user something needs fixing. "Other" is a fold over several
 * categories with nothing single behind it, so it is inert rather than opening an arbitrary member.
 */
private fun ChartSlice.openCategory(
    onOpen: (categoryId: Long?, categoryName: String?) -> Unit
) {
    when {
        categoryId != null -> onOpen(categoryId, label)
        isUnnamed -> onOpen(null, null)
        else -> Unit
    }
}
