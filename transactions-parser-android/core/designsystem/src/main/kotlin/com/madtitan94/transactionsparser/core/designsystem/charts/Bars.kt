package com.madtitan94.transactionsparser.core.designsystem.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.madtitan94.transactionsparser.core.designsystem.theme.AppDimens
import com.madtitan94.transactionsparser.core.designsystem.theme.AppShapes
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTypography

/**
 * A determinate track — the magnitude mark that appears in four sizes across the dashboards: under
 * a payee row, beside a category row, under a budget row, and as the mapping-health hero.
 *
 * It is a pair of boxes rather than Material's `LinearProgressIndicator` because the design's track
 * has no stop indicator, no gap before the fill, and a radius that belongs to the bar scale rather
 * than to Material's. `AppProgressBar` covers the plain amber case; this one takes a colour, since
 * a category row's track is the category's own.
 */
@Composable
fun ChartTrack(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = AppChartDimens.trackCategory,
    color: Color = AppTheme.colors.accentGraphic,
    trackColor: Color = AppTheme.colors.surfaceAlt
) {
    val safe = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .height(height)
            .clip(AppShapes.bar)
            .background(trackColor)
    ) {
        if (safe > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(safe)
                    .clip(AppShapes.bar)
                    .background(color)
            )
        }
    }
}

/**
 * The whole period as one bar, split by category — the mark `design/Charts.dc.html` uses to
 * introduce the palette.
 *
 * The 2dp gaps are the card's own background showing through, not a colour: that is what keeps two
 * adjacent series colours from reading as one wide segment. The bar is clipped as a whole, so only
 * the outer ends are rounded — the segments inside meet the gap square, exactly as the artboard
 * draws them.
 *
 * Segments are weighted by amount rather than by percentage so a slice that rounds to 0% still
 * occupies its true sliver instead of collapsing.
 */
@Composable
fun StackedShareBar(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    height: Dp = AppChartDimens.shareBarHeight
) {
    val colors = AppTheme.colors
    val drawable = slices.filter { it.amountPaise > 0L }

    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(AppShapes.bar),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.barSegmentGap)
    ) {
        if (drawable.isEmpty()) {
            Box(Modifier.fillMaxWidth().fillMaxHeight().background(colors.surfaceAlt))
            return@Row
        }
        drawable.forEach { slice ->
            val segment = Modifier
                .weight(slice.amountPaise.toFloat())
                .fillMaxHeight()
            if (slice.slot == null) {
                Box(
                    segment.hatchBackground(
                        stripe = colors.chartHatchStripe,
                        ground = colors.chartHatchGround
                    )
                )
            } else {
                Box(segment.background(chartSlotColor(colors.chartSeries, slice.slot)))
            }
        }
    }
}

/**
 * Money in against money out, bucket by bucket — W6.
 *
 * Both series are scaled against the largest single value across the two (see [pairedBarFractions]),
 * which is the comparison the widget exists to make. Bars are rounded at the top and square at the
 * baseline, so the baseline reads as the axis rather than as a shadow under floating shapes.
 *
 * A caller with no credit data passes an empty [inValues] and gets the out bars alone; the design is
 * explicit that a fake zero-filled income series is worse than an absent one.
 */
@Composable
fun PairedBarChart(
    inValues: List<Long>,
    outValues: List<Long>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    plotHeight: Dp = AppChartDimens.pairedPlotHeight,
    barWidth: Dp = AppChartDimens.pairedBarWidth
) {
    val colors = AppTheme.colors
    val credits = if (inValues.isEmpty()) List(outValues.size) { 0L } else inValues
    val fractions = pairedBarFractions(credits, outValues)
    val showCredits = inValues.isNotEmpty()

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(plotHeight),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            fractions.forEach { (credit, debit) ->
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(AppChartDimens.pairedBarGap)
                ) {
                    if (showCredits) {
                        VerticalBar(credit, barWidth, plotHeight, colors.success)
                    }
                    VerticalBar(debit, barWidth, plotHeight, colors.danger)
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(AppDimens.hairline)
                .background(colors.border)
        )

        Spacer(Modifier.height(AppChartDimens.pairedLabelGap))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            labels.take(fractions.size).forEach { label ->
                Box(
                    Modifier.width(
                        if (showCredits) barWidth * 2 + AppChartDimens.pairedBarGap else barWidth
                    )
                ) {
                    Text(
                        text = label,
                        style = AppTypography.eyebrow,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * One bar of a paired bucket.
 *
 * A zero-height bar is still given a hairline so an empty bucket is visibly empty rather than
 * missing — the difference between "no money moved" and "no data" is one the dashboard has to keep.
 */
@Composable
private fun VerticalBar(fraction: Float, width: Dp, plotHeight: Dp, color: Color) {
    val safe = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
    val height = (plotHeight * safe).coerceAtLeast(AppDimens.hairline)
    Box(
        Modifier
            .width(width)
            .height(height)
            .clip(AppChartShapes.pairedBar)
            .background(color)
    )
}

/**
 * A ranked row's fixed-width track with its category swatch — the shape W3 repeats five times.
 *
 * The label and the amount stay with the caller: this is the part that has to be identical between
 * the Pulse and Categories dashboards, and the text around it is what differs.
 */
@Composable
fun RankedBarCell(
    slot: Int?,
    fraction: Float,
    modifier: Modifier = Modifier,
    trackWidth: Dp = AppChartDimens.categoryTrackWidth
) {
    val colors = AppTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        CategorySwatch(slot)
        Spacer(Modifier.width(AppDimens.swatchGap))
        if (slot == null) {
            Box(
                Modifier
                    .width(trackWidth * (if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)))
                    .height(AppChartDimens.trackCategory)
                    .clip(AppShapes.bar)
                    .hatchBackground(
                        stripe = colors.chartHatchStripe,
                        ground = colors.chartHatchGround,
                        period = AppChartDimens.swatchHatchPeriod,
                        stripeWidth = AppChartDimens.swatchHatchStripe
                    )
            )
        } else {
            ChartTrack(
                fraction = fraction,
                modifier = Modifier.width(trackWidth),
                color = chartSlotColor(colors.chartSeries, slot)
            )
        }
    }
}

/**
 * One of the three numbers in the cash-position row — W5.
 *
 * [value] is pre-formatted: the abbreviation rules ("+₹13.5k") live in `core:presentation`'s
 * formatters, and duplicating them here would give the dashboards a second opinion about money.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = AppTheme.colors.textPrimary
) {
    val colors = AppTheme.colors
    Column(
        modifier
            .clip(AppChartShapes.statTile)
            .background(colors.surface)
            .padding(AppChartDimens.statTilePadding),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = AppTypography.eyebrow,
            color = colors.textSecondary
        )
        Text(
            text = value,
            style = AppTypography.amount,
            color = valueColor
        )
    }
}
