package com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madtitan94.transactionsparser.core.designsystem.charts.CategorySwatch
import com.madtitan94.transactionsparser.core.designsystem.charts.ChartSlice
import com.madtitan94.transactionsparser.core.designsystem.charts.ChartTrack
import com.madtitan94.transactionsparser.core.designsystem.charts.DonutChart
import com.madtitan94.transactionsparser.core.designsystem.charts.selectableChartRow
import com.madtitan94.transactionsparser.core.designsystem.charts.trackFraction
import com.madtitan94.transactionsparser.core.designsystem.theme.AppDimens
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTypography
import com.madtitan94.transactionsparser.core.presentation.formatPaise
import com.madtitan94.transactionsparser.core.presentation.formatPaiseCompact
import com.madtitan94.transactionsparser.feature.dashboard.domain.RankedValueFormat
import com.madtitan94.transactionsparser.feature.dashboard.presentation.R

/**
 * Categories ranked largest first.
 *
 * Tracks are scaled against the *largest row*, not against the period total, per `DashboardSpec` W3.
 * Against the total, a month split evenly across six categories would draw six stubs and the ranking
 * would be unreadable; against the leader, the top row is always full and every other row states its
 * size relative to it, which is the comparison a reader is actually making.
 *
 * Amount or percentage is a format flag rather than a second composable, because the two differ in
 * nothing else — one layout means the Pulse and Categories versions cannot drift apart.
 */
@Composable
fun CategoryRankedWidget(
    slices: List<ChartSlice>,
    valueFormat: RankedValueFormat,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onRowClick: ((Int) -> Unit)? = null
) {
    DashboardCard(modifier = modifier) {
        CardHeader(title = stringResource(R.string.dash_by_category))

        if (slices.isEmpty()) {
            CardEmptyLine(stringResource(R.string.dash_category_empty))
            return@DashboardCard
        }

        val largest = slices.maxOf { it.amountPaise }

        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.rowGap)) {
            slices.forEachIndexed { index, slice ->
                RankedCategoryRow(
                    slice = slice,
                    fraction = trackFraction(slice.amountPaise, largest),
                    valueFormat = valueFormat,
                    selected = index == selectedIndex,
                    onClick = onRowClick?.let { click -> { click(index) } }
                )
            }
        }
    }
}

@Composable
private fun RankedCategoryRow(
    slice: ChartSlice,
    fraction: Float,
    valueFormat: RankedValueFormat,
    selected: Boolean,
    onClick: (() -> Unit)?
) {
    // The unnamed slice is muted throughout — it is a gap in the data, not a category competing
    // with the others, and giving it the same ink would make it look like one.
    val ink = if (slice.isUnnamed) AppTheme.colors.textSecondary else AppTheme.colors.textPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableChartRow(selected = selected, onClick = onClick, clickLabel = slice.label),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.swatchGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategorySwatch(slot = slice.slot)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppDimens.rowLabelToBarGap)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = slice.label,
                    style = AppTypography.row,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = when (valueFormat) {
                        RankedValueFormat.AMOUNT -> formatPaise(slice.amountPaise)
                        RankedValueFormat.PERCENT -> sharePercentLabel(slice.sharePercent)
                    },
                    style = AppTypography.amount,
                    color = ink
                )
            }
            ChartTrack(fraction = fraction, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * "<1%" rather than "0%" for a slice too small to round up to a whole point.
 *
 * A row that says 0% while showing real money against it reads as a bug. `DashboardSpec` §5 asks
 * for this explicitly, and it is a formatting decision rather than an arithmetic one — the slice's
 * share is genuinely below half a point.
 */
@Composable
private fun sharePercentLabel(percent: Int): String = when (percent) {
    0 -> stringResource(R.string.dash_share_tiny)
    else -> stringResource(R.string.dash_share_percent, percent)
}

/**
 * The donut, with the period's total held in the middle and a legend down the side.
 *
 * The lede above it names the biggest category in a sentence, because a donut answers "how is it
 * split" but makes the reader do the work of finding the largest wedge themselves.
 *
 * An empty period keeps the ring — drawn as a single empty band by the primitive — rather than
 * removing the card, so the layout does not jump between a month with spending and one without.
 */
@Composable
fun CategoryDonutWidget(
    slices: List<ChartSlice>,
    totalPaise: Long,
    rangeLabel: String,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onSliceClick: ((Int) -> Unit)? = null
) {
    val biggest = slices.firstOrNull { !it.isUnnamed }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = if (biggest == null) {
                stringResource(R.string.dash_donut_empty)
            } else {
                stringResource(R.string.dash_cat_lede, biggest.label, biggest.sharePercent)
            },
            style = AppTypography.body,
            color = AppTheme.colors.textSecondary
        )

        DashboardCard {
            CardHeader(title = stringResource(R.string.dash_spend_by_category))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DonutChart(
                    slices = slices,
                    selectedIndex = selectedIndex,
                    onSliceClick = onSliceClick,
                    contentDescription = stringResource(R.string.dash_spend_by_category)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatPaiseCompact(totalPaise),
                            style = AppTypography.amount,
                            color = AppTheme.colors.textPrimary
                        )
                        Text(
                            text = rangeLabel,
                            style = AppTypography.navLabel,
                            color = AppTheme.colors.textMuted
                        )
                    }
                }

                if (slices.isNotEmpty()) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        slices.forEachIndexed { index, slice ->
                            DonutLegendRow(
                                slice = slice,
                                selected = index == selectedIndex,
                                onClick = onSliceClick?.let { click -> { click(index) } }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DonutLegendRow(slice: ChartSlice, selected: Boolean, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableChartRow(selected = selected, onClick = onClick, clickLabel = slice.label),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.swatchGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategorySwatch(slot = slice.slot)
        Text(
            text = slice.label,
            style = AppTypography.row,
            color = if (slice.isUnnamed) AppTheme.colors.textSecondary else AppTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = sharePercentLabel(slice.sharePercent),
            style = AppTypography.amount,
            color = AppTheme.colors.textSecondary,
            modifier = Modifier.width(44.dp)
        )
    }
}
