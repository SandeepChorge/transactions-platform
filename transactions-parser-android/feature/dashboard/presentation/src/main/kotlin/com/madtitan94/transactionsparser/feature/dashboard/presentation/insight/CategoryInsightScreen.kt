package com.madtitan94.transactionsparser.feature.dashboard.presentation.insight

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madtitan94.transactionsparser.core.designsystem.charts.ColumnAccent
import com.madtitan94.transactionsparser.core.designsystem.charts.ColumnChart
import com.madtitan94.transactionsparser.core.designsystem.charts.SpendTrendChart
import com.madtitan94.transactionsparser.core.designsystem.components.LoadingIndicator
import com.madtitan94.transactionsparser.core.designsystem.theme.AppDimens
import com.madtitan94.transactionsparser.core.designsystem.theme.AppShapes
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTypography
import com.madtitan94.transactionsparser.core.domain.model.DateRange
import com.madtitan94.transactionsparser.core.presentation.formatAxisDay
import com.madtitan94.transactionsparser.core.presentation.formatAxisMonth
import com.madtitan94.transactionsparser.core.presentation.formatPaise
import com.madtitan94.transactionsparser.core.presentation.formatStatementDayHeader
import com.madtitan94.transactionsparser.feature.dashboard.domain.PayeeScope
import com.madtitan94.transactionsparser.feature.dashboard.presentation.CustomRangePickerDialog
import com.madtitan94.transactionsparser.feature.dashboard.presentation.R
import com.madtitan94.transactionsparser.feature.dashboard.presentation.RangeSheet
import com.madtitan94.transactionsparser.feature.dashboard.presentation.rangeLabel
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.AnomalyCallout
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.CardEmptyLine
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.CardEyebrow
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.CardHeader
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.DashboardCard
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.PayeeRankedWidget
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets.toCalloutUi
import java.time.LocalDate
import org.koin.androidx.compose.koinViewModel

/** Trend, spend by day, by weekday, top payees — the design's four angles, in its order. */
private const val CARD_COUNT = 4

@Composable
fun CategoryInsightRoot(
    onBack: () -> Unit,
    onOpenPayee: (normalizedPayee: String, rawPayee: String) -> Unit,
    viewModel: CategoryInsightViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CategoryInsightScreen(
        state = state,
        rangeWindow = viewModel.resolvedRange(),
        today = viewModel.currentDate(),
        onAction = viewModel::onAction,
        onBack = onBack,
        onOpenPayee = onOpenPayee
    )
}

@Composable
fun CategoryInsightScreen(
    state: CategoryInsightState,
    rangeWindow: DateRange,
    today: LocalDate,
    onAction: (CategoryInsightAction) -> Unit,
    onBack: () -> Unit,
    onOpenPayee: (String, String) -> Unit
) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val uncategorised = stringResource(R.string.dash_uncategorised)
    val title = state.categoryName ?: uncategorised

    Column(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.screen)
            .verticalScroll(rememberScrollState())
    ) {
        InsightHeader(
            title = title,
            payeeCount = state.data.share.payeeCount,
            onBack = onBack
        )

        RangeRow(
            label = rangeLabel(state.range),
            onClick = { sheetOpen = true }
        )

        if (state.isLoading) {
            LoadingIndicator()
            return@Column
        }

        InsightHero(state = state)

        // Between the hero and the carousel, not above the header. The hero states what this
        // category did over the period, and the callout is a caveat on that figure — read the other
        // way round, an unusual charge appears before the user knows what they are looking at.
        state.anomalies.forEach { anomaly ->
            Spacer(Modifier.height(12.dp))
            AnomalyCallout(
                callout = anomaly.toCalloutUi(),
                onDismiss = { id -> onAction(CategoryInsightAction.OnDismissAnomaly(id)) },
                onClick = { onOpenPayee(anomaly.normalizedName, anomaly.statementName) },
                modifier = Modifier.padding(horizontal = AppDimens.screenHorizontalPadding)
            )
        }

        Spacer(Modifier.height(16.dp))

        InsightCarousel(
            state = state,
            rangeWindow = rangeWindow,
            today = today,
            onAction = onAction,
            onOpenPayee = onOpenPayee
        )

        Spacer(Modifier.height(32.dp))
    }

    if (sheetOpen) {
        RangeSheet(
            current = state.range,
            onSelect = {
                onAction(CategoryInsightAction.OnRangeSelected(it))
                sheetOpen = false
            },
            onPickCustom = {
                sheetOpen = false
                onAction(CategoryInsightAction.OnCustomRangeClick)
            },
            onDismiss = { sheetOpen = false }
        )
    }

    if (state.isPickingCustomRange) {
        CustomRangePickerDialog(
            initial = state.range,
            onPicked = { from, to ->
                onAction(CategoryInsightAction.OnCustomRangePicked(from, to))
            },
            onDismiss = { onAction(CategoryInsightAction.OnCustomRangeDismiss) }
        )
    }
}

/**
 * Back, the category's name, and how many payees its spend reached in the period.
 *
 * The subtitle is a *range-scoped* count rather than the design's "14 payees mapped", which reads as
 * a property of the category. A count of every payee ever mapped to it would sit directly above a
 * ranked list of the ones with spending this month and disagree with it in plain sight.
 */
@Composable
private fun InsightHeader(title: String, payeeCount: Int, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = 4.dp,
                end = AppDimens.screenHorizontalPadding,
                top = 12.dp,
                bottom = 4.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.dash_back),
                tint = AppTheme.colors.textSecondary
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTypography.title,
                color = AppTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (payeeCount > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.insight_payee_count,
                        payeeCount,
                        payeeCount
                    ),
                    style = AppTypography.body,
                    color = AppTheme.colors.textMuted
                )
            }
        }
    }
}

/** The period this screen is reading, and the way to change it — the same one Home uses. */
@Composable
private fun RangeRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.screenHorizontalPadding, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.insight_showing, label),
            style = AppTypography.body,
            color = AppTheme.colors.textMuted,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = stringResource(R.string.insight_change_range),
            style = AppTypography.row,
            color = AppTheme.colors.accentInk,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
        )
    }
}

/** The total, and the one line that says what it means against the period before. */
@Composable
private fun InsightHero(state: CategoryInsightState) {
    val data = state.data
    val delta = data.deltaPercent
    val share = data.share.sharePercent

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.screenHorizontalPadding)
            .background(AppTheme.colors.surfaceSunken, AppShapes.card)
            .padding(AppDimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CardEyebrow(stringResource(R.string.insight_spent_label, rangeLabel(state.range)))
        Text(
            text = formatPaise(data.share.totalPaise),
            style = AppTypography.hero,
            color = AppTheme.colors.textPrimary,
            maxLines = 1
        )
        // Two independent clauses, each dropped on its own when it cannot be stated: All time has no
        // period before it, and an empty account has no total to take a share of. Printing "0%" or
        // "level with the period before" in those cases would invent a comparison.
        val clauses = buildList {
            when {
                delta == null -> Unit
                delta > 0 -> add(stringResource(R.string.insight_delta_more, delta))
                delta < 0 -> add(stringResource(R.string.insight_delta_less, -delta))
                else -> add(stringResource(R.string.insight_delta_flat))
            }
            if (share != null) add(stringResource(R.string.insight_share_of_spend, share))
        }
        if (clauses.isNotEmpty()) {
            Text(
                text = clauses.joinToString(separator = " · "),
                style = AppTypography.body,
                color = AppTheme.colors.textSecondary
            )
        }
    }
}

/**
 * The four cards, swipeable, with a page indicator underneath.
 *
 * A pager rather than a stacked column because the four answer four different questions about the
 * same category, and stacking them makes the screen a scroll of charts where the design makes it a
 * choice of angle. The page count is fixed: a card with nothing to draw shows its own empty line
 * rather than disappearing, so the dots do not change count as the range does.
 */
@Composable
private fun InsightCarousel(
    state: CategoryInsightState,
    rangeWindow: DateRange,
    today: LocalDate,
    onAction: (CategoryInsightAction) -> Unit,
    onOpenPayee: (String, String) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { CARD_COUNT })

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.screenHorizontalPadding, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CardEyebrow(stringResource(R.string.insight_swipe_hint))
            Text(
                text = stringResource(
                    R.string.insight_card_position,
                    pagerState.currentPage + 1,
                    CARD_COUNT
                ),
                style = AppTypography.body,
                color = AppTheme.colors.textMuted
            )
        }

        HorizontalPager(
            state = pagerState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = AppDimens.screenHorizontalPadding
            ),
            pageSpacing = 12.dp,
            // Every page is as tall as the tallest, and the pager centres a shorter one in that
            // space by default — which left the payee card, the shortest of the four, floating a
            // visible gap below where the other three start. Top-aligned, all four share an edge.
            verticalAlignment = Alignment.Top
        ) { page ->
            when (page) {
                0 -> TrendCard(state = state, today = today, onAction = onAction)
                1 -> DayCard(state = state, rangeWindow = rangeWindow)
                2 -> WeekdayCard(state = state)
                else -> PayeesCard(state = state, onOpenPayee = onOpenPayee)
            }
        }

        PagerDots(current = pagerState.currentPage, count = CARD_COUNT)
    }
}

/**
 * The one card that ignores the shared filter.
 *
 * "How is this going over time" is a longer question than any single period answers, so this looks
 * at whole months regardless of what the range chip says — the deviation is deliberate and is
 * called out in the design's own copy.
 */
@Composable
private fun TrendCard(
    state: CategoryInsightState,
    today: LocalDate,
    onAction: (CategoryInsightAction) -> Unit
) {
    val series = remember(state.data.monthTotals, state.trendWindow, today) {
        buildMonthSeries(state.data.monthTotals, today, state.trendWindow.months)
    }

    DashboardCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.insight_trend_title),
                style = AppTypography.sectionHeader,
                color = AppTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TrendWindow.entries.forEach { window ->
                    WindowChip(
                        label = stringResource(windowLabel(window)),
                        selected = window == state.trendWindow,
                        onClick = {
                            onAction(CategoryInsightAction.OnTrendWindowSelected(window))
                        }
                    )
                }
            }
        }

        if (!series.isDrawable) {
            CardEmptyLine(stringResource(R.string.insight_trend_empty))
            return@DashboardCard
        }

        SpendTrendChart(
            values = series.values,
            contentDescription = stringResource(R.string.insight_trend_title)
        )

        AxisEnds(
            start = formatAxisMonth(series.points.first().startMillis),
            end = formatAxisMonth(series.points.last().startMillis)
        )

        // The callout states a streak rather than describing the slope. "Trending up for three
        // straight months" is something a reader can check against the chart; "trending up" is a
        // restatement of the line they are already looking at.
        val callout = when {
            series.risingStreak >= 2 ->
                stringResource(R.string.insight_trend_rising, series.risingStreak + 1)
            series.fallingStreak >= 2 ->
                stringResource(R.string.insight_trend_falling, series.fallingStreak + 1)
            else -> null
        }
        if (callout != null) {
            Text(
                text = callout,
                style = AppTypography.body,
                color = AppTheme.colors.textSecondary
            )
        }
    }
}

/** Every day of the range, with the biggest and the smallest spending day named. */
@Composable
private fun DayCard(state: CategoryInsightState, rangeWindow: DateRange) {
    val series = remember(state.data.dayTotals, rangeWindow) {
        buildDaySeries(
            state.data.dayTotals,
            rangeWindow.fromMillis,
            rangeWindow.toMillisExclusive
        )
    }

    DashboardCard {
        CardHeader(title = stringResource(R.string.insight_day_title))

        if (!series.isDrawable || series.peak == null) {
            CardEmptyLine(stringResource(R.string.insight_day_empty))
            return@DashboardCard
        }

        val accents = buildMap {
            series.peakIndex?.let { put(it, ColumnAccent.PEAK) }
            // Only mark a trough when it is a different day from the peak. A range with one
            // spending day in it is its own largest and smallest, and colouring one column twice
            // would claim two facts from one bar.
            series.troughIndex?.takeIf { it != series.peakIndex }
                ?.let { put(it, ColumnAccent.TROUGH) }
        }

        ColumnChart(
            values = series.values,
            accents = accents,
            labels = listOf(
                formatAxisDay(series.points.first().startMillis),
                formatAxisDay(series.points.last().startMillis)
            ),
            contentDescription = stringResource(R.string.insight_day_title)
        )

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Callout(
                label = stringResource(R.string.insight_day_most),
                value = stringResource(
                    R.string.insight_day_value,
                    formatStatementDayHeader(series.peak.startMillis),
                    formatPaise(series.peak.totalPaise)
                ),
                color = AppTheme.colors.dangerMutedText
            )
            val trough = series.trough
            if (trough != null && trough.startMillis != series.peak.startMillis) {
                Callout(
                    label = stringResource(R.string.insight_day_least),
                    value = stringResource(
                        R.string.insight_day_value,
                        formatStatementDayHeader(trough.startMillis),
                        formatPaise(trough.totalPaise)
                    ),
                    color = AppTheme.colors.successMutedText
                )
            }
        }
    }
}

/** The same days, re-bucketed Mon–Sun, so a pattern shows where a run of dates cannot. */
@Composable
private fun WeekdayCard(state: CategoryInsightState) {
    val series = remember(state.data.dayTotals) { buildWeekdaySeries(state.data.dayTotals) }
    val labels = weekdayLabels()

    DashboardCard {
        CardHeader(title = stringResource(R.string.insight_weekday_title))

        if (!series.isDrawable) {
            CardEmptyLine(stringResource(R.string.insight_weekday_empty))
            return@DashboardCard
        }

        ColumnChart(
            values = series.values,
            accents = series.peakIndex?.let { mapOf(it to ColumnAccent.PEAK) } ?: emptyMap(),
            labels = labels,
            contentDescription = stringResource(R.string.insight_weekday_title)
        )

        // The design's copy asserts "you spend the most on weekends". It is only printed when it is
        // actually true: two of seven days are 29% of the week by count, so a weekend share has to
        // clear a real majority before the sentence earns its place.
        val weekend = series.weekendPercent
        val peak = series.peakIndex
        val callout = when {
            weekend != null && weekend >= WEEKEND_MAJORITY ->
                stringResource(R.string.insight_weekday_weekend, weekend)
            peak != null -> stringResource(R.string.insight_weekday_peak, labels[peak])
            else -> null
        }
        if (callout != null) {
            Text(
                text = callout,
                style = AppTypography.body,
                color = AppTheme.colors.textSecondary
            )
        }
    }
}

/**
 * Where the money in this category actually went.
 *
 * Reuses the dashboard's own ranked widget rather than a copy of it, so a merged payee totals as one
 * row here for the same reason it does on Home, and the row grammar is the one the user already
 * knows. Its own empty line — "no spending to anyone in this period" — is right here too.
 */
@Composable
private fun PayeesCard(state: CategoryInsightState, onOpenPayee: (String, String) -> Unit) {
    PayeeRankedWidget(
        payees = state.data.payees,
        scope = PayeeScope.ALL,
        onPayeeClick = { payee -> onOpenPayee(payee.normalizedName, payee.statementName) }
    )
}

/** Above half the week's spend, which two of seven days have to clear to be worth a sentence. */
private const val WEEKEND_MAJORITY = 50

@Composable
private fun Callout(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = AppTypography.body,
            color = AppTheme.colors.textMuted
        )
        Text(text = value, style = AppTypography.body, color = color, maxLines = 1)
    }
}

/** The two ends of a span, with the gap between them reserved rather than left to chance. */
@Composable
private fun AxisEnds(start: String, end: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.labelToAmountGap)
    ) {
        Text(
            text = start,
            style = AppTypography.eyebrow,
            color = AppTheme.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = end,
            style = AppTypography.eyebrow,
            color = AppTheme.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun WindowChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = AppTypography.eyebrow,
        color = if (selected) AppTheme.colors.textPrimary else AppTheme.colors.textMuted,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) AppTheme.colors.surfaceAlt else androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun PagerDots(current: Int, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val selected = index == current
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = if (selected) 14.dp else 6.dp, height = 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) AppTheme.colors.accentGraphic else AppTheme.colors.borderStrong
                    )
            )
        }
    }
}

private fun windowLabel(window: TrendWindow): Int = when (window) {
    TrendWindow.THREE_MONTHS -> R.string.insight_window_3m
    TrendWindow.SIX_MONTHS -> R.string.insight_window_6m
    TrendWindow.ONE_YEAR -> R.string.insight_window_1y
}

/** Mon-first, matching the order `buildWeekdaySeries` produces. */
@Composable
private fun weekdayLabels(): List<String> = listOf(
    stringResource(R.string.insight_mon),
    stringResource(R.string.insight_tue),
    stringResource(R.string.insight_wed),
    stringResource(R.string.insight_thu),
    stringResource(R.string.insight_fri),
    stringResource(R.string.insight_sat),
    stringResource(R.string.insight_sun)
)
