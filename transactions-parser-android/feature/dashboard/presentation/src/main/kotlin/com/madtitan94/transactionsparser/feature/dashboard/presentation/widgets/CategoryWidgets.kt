package com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
            // spacedBy, not SpaceBetween: the gap has to be reserved before the label is measured,
            // or a name long enough to fill the row ends up touching the amount beside it.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.labelToAmountGap),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = slice.label,
                    style = AppTypography.row,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
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
 * The card interior below which the donut and its legend stop sharing a row.
 *
 * Derived from the parts rather than picked: the row is only worth keeping while the legend can
 * still hold a category name. The donut's 150dp box, the 16dp beside it, then the legend's own 10dp
 * swatch, 12dp gap, a readable 110dp of name, another 12dp and the 44dp percentage column come to
 * 354dp. A 412dp phone — the artboard width — leaves 320dp inside the card and a 360dp phone leaves
 * 268dp, so both stack; a tablet keeps the artboard's side-by-side row.
 *
 * Stacking rather than shrinking the ring, because shrinking does not buy enough: at 360dp the
 * legend had 24dp for a name and drew "Monthly expenses" as "M…", and a donut small enough to fix
 * that would no longer read as the chart the lede is talking about.
 */
private val DONUT_ROW_MIN_WIDTH = 354.dp

/**
 * The donut, with the period's total held in the middle and a legend beside or beneath it.
 *
 * The lede above it names the biggest category in a sentence, because a donut answers "how is it
 * split" but makes the reader do the work of finding the largest wedge themselves.
 *
 * An empty period keeps the ring — drawn as a single empty band by the primitive — rather than
 * removing the card, so the layout does not jump between a month with spending and one without.
 *
 * [rangeCaption] is the short caption for the hole and deliberately not the range's full label: a
 * custom range reads "01 Jun 2026 – 30 Jun 2026", which no 75dp hole can hold.
 */
@Composable
fun CategoryDonutWidget(
    slices: List<ChartSlice>,
    totalPaise: Long,
    rangeCaption: String,
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

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth < DONUT_ROW_MIN_WIDTH) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CategoryDonut(
                            slices = slices,
                            totalPaise = totalPaise,
                            rangeCaption = rangeCaption,
                            selectedIndex = selectedIndex,
                            onSliceClick = onSliceClick
                        )
                        DonutLegend(
                            slices = slices,
                            selectedIndex = selectedIndex,
                            onSliceClick = onSliceClick,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryDonut(
                            slices = slices,
                            totalPaise = totalPaise,
                            rangeCaption = rangeCaption,
                            selectedIndex = selectedIndex,
                            onSliceClick = onSliceClick
                        )
                        DonutLegend(
                            slices = slices,
                            selectedIndex = selectedIndex,
                            onSliceClick = onSliceClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryDonut(
    slices: List<ChartSlice>,
    totalPaise: Long,
    rangeCaption: String,
    selectedIndex: Int?,
    onSliceClick: ((Int) -> Unit)?
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
                color = AppTheme.colors.textPrimary,
                maxLines = 1
            )
            // Two lines rather than one: the primitive clamps this to the hole, and a caption that
            // will not fit on one line should wrap inside the ring rather than lose its second half
            // to an ellipsis.
            Text(
                text = rangeCaption,
                style = AppTypography.navLabel,
                color = AppTheme.colors.textMuted,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DonutLegend(
    slices: List<ChartSlice>,
    selectedIndex: Int?,
    onSliceClick: ((Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (slices.isEmpty()) return

    Column(
        modifier = modifier,
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
