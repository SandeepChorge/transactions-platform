package com.madtitan94.transactionsparser.core.designsystem.charts

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.madtitan94.transactionsparser.core.designsystem.theme.AppDimens
import com.madtitan94.transactionsparser.core.designsystem.theme.AppShapes
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTypography

/** How long a bar or a track takes to reach a new value. Short enough not to delay a range change. */
private const val GROW_MILLIS = 450

/**
 * A determinate track — the magnitude mark that appears in four sizes across the dashboards: under
 * a payee row, beside a category row, under a budget row, and as the mapping-health hero.
 *
 * It is a pair of boxes rather than Material's `LinearProgressIndicator` because the design's track
 * has no stop indicator, no gap before the fill, and a radius that belongs to the bar scale rather
 * than to Material's. `AppProgressBar` covers the plain amber case; this one takes a colour, since
 * a category row's track is the category's own.
 *
 * The fill animates to its target rather than snapping. It starts *at* the first value, so a card
 * appearing on screen does not replay a fill it never had — the motion is reserved for a number
 * that actually changed, which is the only time it carries information.
 */
@Composable
fun ChartTrack(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = AppChartDimens.trackCategory,
    color: Color = AppTheme.colors.accentGraphic,
    trackColor: Color = AppTheme.colors.surfaceAlt
) {
    val target = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(GROW_MILLIS, easing = FastOutSlowInEasing),
        label = "trackFill"
    )

    Box(
        modifier
            .height(height)
            .clip(AppShapes.bar)
            .background(trackColor)
    ) {
        if (animated > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
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
 * occupies its true sliver instead of collapsing. Those weights animate, so a range change slides
 * the boundaries rather than cutting to a new bar.
 *
 * A tapped segment reports its index, but the bar carries no selected state of its own. Segments
 * are often far narrower than a 48dp target and this is a summary strip rather than a control:
 * every way of marking one selected either dims the validated palette or turns the segment inside
 * out. The ranked rows underneath are where selection is shown, and where it can be reached
 * reliably.
 */
@Composable
fun StackedShareBar(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    height: Dp = AppChartDimens.shareBarHeight,
    onSegmentClick: ((Int) -> Unit)? = null
) {
    val colors = AppTheme.colors
    val drawable = slices.withIndex().filter { it.value.amountPaise > 0L }

    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(AppShapes.bar),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.barSegmentGap)
    ) {
        if (drawable.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(colors.surfaceAlt)
            )
            return@Row
        }

        drawable.forEach { (index, slice) ->
            val weight by animateFloatAsState(
                targetValue = slice.amountPaise.toFloat(),
                animationSpec = tween(GROW_MILLIS, easing = FastOutSlowInEasing),
                label = "segmentWeight"
            )
            // A segment that has animated all the way to zero would crash `weight`, which requires
            // a positive value.
            val segment = Modifier
                .weight(weight.coerceAtLeast(0.0001f))
                .fillMaxHeight()
                .then(
                    if (onSegmentClick == null) {
                        Modifier
                    } else {
                        Modifier.clickable(role = Role.Button) { onSegmentClick(index) }
                    }
                )

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
 *
 * Tapping a bucket reports its index (W6: bucket → transaction list for that sub-period). The whole
 * column is the target, not the bars — a 14dp bar is nowhere near a 48dp touch target, and reaching
 * for the gap between two bars is the natural way to point at a week.
 */
@Composable
fun PairedBarChart(
    inValues: List<Long>,
    outValues: List<Long>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    plotHeight: Dp = AppChartDimens.pairedPlotHeight,
    barWidth: Dp = AppChartDimens.pairedBarWidth,
    selectedIndex: Int? = null,
    onBucketClick: ((Int) -> Unit)? = null,
    contentDescription: String? = null
) {
    val colors = AppTheme.colors
    val credits = if (inValues.isEmpty()) List(outValues.size) { 0L } else inValues
    val fractions = pairedBarFractions(credits, outValues)
    val showCredits = inValues.isNotEmpty()
    val bucketWidth = if (showCredits) barWidth * 2 + AppChartDimens.pairedBarGap else barWidth

    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(plotHeight),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            fractions.forEachIndexed { index, (credit, debit) ->
                // A bucket is marked with an outline, not the surface fill a ranked row uses.
                // `surfaceAlt` against `surface` is about a four per cent step — enough to read
                // across a full-width row, invisible down a 33dp column. The border carries the
                // same meaning at the same weight in both themes.
                val outline by animateColorAsState(
                    targetValue = if (index == selectedIndex) {
                        colors.borderStrong
                    } else {
                        Color.Transparent
                    },
                    label = "bucketSelection"
                )

                Row(
                    Modifier
                        .fillMaxHeight()
                        .clip(AppShapes.bar)
                        .border(AppDimens.hairline, outline, AppShapes.bar)
                        .then(
                            if (onBucketClick == null) {
                                Modifier
                            } else {
                                Modifier.clickable(role = Role.Button) { onBucketClick(index) }
                            }
                        )
                        .padding(horizontal = AppChartDimens.selectionPadding),
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
            labels.take(fractions.size).forEachIndexed { index, label ->
                Box(
                    Modifier.width(bucketWidth + AppChartDimens.selectionPadding * 2)
                ) {
                    Text(
                        text = label,
                        style = AppTypography.eyebrow,
                        color = if (index == selectedIndex) {
                            colors.textPrimary
                        } else {
                            colors.textSecondary
                        },
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
 *
 * The bar grows to its height from whatever it was showing, so switching range animates the
 * comparison rather than replacing it.
 */
@Composable
private fun VerticalBar(fraction: Float, width: Dp, plotHeight: Dp, color: Color) {
    val target = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(GROW_MILLIS, easing = FastOutSlowInEasing),
        label = "barHeight"
    )
    Box(
        Modifier
            .width(width)
            .height((plotHeight * animated).coerceAtLeast(AppDimens.hairline))
            .clip(AppChartShapes.pairedBar)
            .background(color)
    )
}

/**
 * Marks a whole ranked row as selectable, and shows when it is.
 *
 * This is a modifier rather than a parameter on [RankedBarCell] because the cell is only the swatch
 * and the track — the label and the amount belong to the caller, and a highlight that covered just
 * the mark would look like a stray box floating beside the name it belongs to. Applied to the
 * caller's row, the backdrop covers what a person would call "the row".
 *
 * A ranked row is the reliable way to reach a category, so this is where the real touch target
 * lives: `minimumInteractiveComponentSize` reserves 48dp without changing what is drawn. The donut
 * slice and the stacked segment are shortcuts to the same place, and neither can be relied on to be
 * big enough to hit.
 */
@Composable
fun Modifier.selectableChartRow(
    selected: Boolean,
    onClick: (() -> Unit)? = null,
    clickLabel: String? = null
): Modifier {
    val colors = AppTheme.colors
    val backdrop by animateColorAsState(
        targetValue = if (selected) colors.surfaceAlt else Color.Transparent,
        label = "rowSelection"
    )
    return this
        .clip(AppShapes.bar)
        .background(backdrop)
        .then(
            if (onClick == null) {
                Modifier
            } else {
                Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(role = Role.Button, onClickLabel = clickLabel) { onClick() }
            }
        )
}

/**
 * A ranked row's fixed-width track with its category swatch — the shape W3 repeats five times.
 *
 * The label and the amount stay with the caller: this is the part that has to be identical between
 * the Pulse and Categories dashboards, and the text around it is what differs. Selection and the
 * touch target go on the caller's row via [selectableChartRow], for the same reason.
 */
@Composable
fun RankedBarCell(
    slot: Int?,
    fraction: Float,
    modifier: Modifier = Modifier,
    trackWidth: Dp = AppChartDimens.categoryTrackWidth
) {
    val colors = AppTheme.colors
    val animatedFraction by animateFloatAsState(
        targetValue = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f),
        animationSpec = tween(GROW_MILLIS, easing = FastOutSlowInEasing),
        label = "rankedFill"
    )

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        CategorySwatch(slot)
        Spacer(Modifier.width(AppDimens.swatchGap))
        if (slot == null) {
            Box(
                Modifier
                    .width(trackWidth * animatedFraction)
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
    valueColor: Color = AppTheme.colors.textPrimary,
    onClick: (() -> Unit)? = null
) {
    val colors = AppTheme.colors
    Column(
        modifier
            .clip(AppChartShapes.statTile)
            .background(colors.surface)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(role = Role.Button) { onClick() }
                }
            )
            .padding(AppChartDimens.statTilePadding),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = label, style = AppTypography.eyebrow, color = colors.textSecondary)
        Text(text = value, style = AppTypography.amount, color = valueColor)
    }
}
