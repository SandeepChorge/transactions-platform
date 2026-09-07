package com.madtitan94.transactionsparser.core.designsystem.charts

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.madtitan94.transactionsparser.core.designsystem.theme.AppColors
import com.madtitan94.transactionsparser.core.designsystem.theme.AppDimens
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTypography

private const val GROW_MILLIS = 420

/** How a single column is coloured, over and above the series' own fill. */
enum class ColumnAccent {
    /** The default: one series, one colour, no story. */
    PLAIN,

    /** The largest column. Drawn in the spend colour — this is where the money went. */
    PEAK,

    /** The smallest column that is not empty. See `DaySeries.trough` for why zero is excluded. */
    TROUGH
}

/**
 * A single series of vertical columns, with a couple of them allowed to say something.
 *
 * Separate from [PairedBarChart], which exists to compare two series against one shared scale. This
 * one has a different job: draw many buckets of one measure and let two of them be called out.
 * Folding both into one composable would mean a chart taking two series and an accent map that is
 * honest about neither.
 *
 * **Column width is derived, not given.** The spend-by-day card draws a whole month at phone width,
 * where the paired chart's fixed 14dp bar runs off the screen above roughly twenty buckets; the
 * weekday card draws seven and wants them broad. Both come out right from `weight(1f)` and a gap,
 * so no caller has to pick a width that only holds for its own bucket count.
 *
 * [labels] is drawn beneath the plot one per column, except when exactly two are given for more
 * than two columns — then they are the ends of the span. That is what a month of days needs: thirty
 * dates under a 360dp chart is a grey smear, not an axis.
 */
@Composable
fun ColumnChart(
    values: List<Long>,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    accents: Map<Int, ColumnAccent> = emptyMap(),
    plotHeight: Dp = AppChartDimens.columnPlotHeight,
    columnGap: Dp = AppChartDimens.columnGap,
    selectedIndex: Int? = null,
    onColumnClick: ((Int) -> Unit)? = null,
    contentDescription: String? = null
) {
    val colors = AppTheme.colors
    val max = values.maxOrNull() ?: 0L

    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (contentDescription == null) {
                    Modifier
                } else {
                    // The columns carry no text of their own, so the plot is one node to a screen
                    // reader rather than a run of unlabelled boxes.
                    Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
                }
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(plotHeight),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(columnGap)
        ) {
            values.forEachIndexed { index, value ->
                // Every column scales against the largest in the series. An all-zero series has no
                // largest, and dividing by it would leave every column NaN-tall rather than empty.
                val fraction = if (max <= 0L) 0f else (value.toDouble() / max).toFloat()

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(
                            if (onColumnClick == null) {
                                Modifier
                            } else {
                                Modifier.clickable { onColumnClick(index) }
                            }
                        ),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    ColumnBar(
                        fraction = fraction,
                        plotHeight = plotHeight,
                        // An empty bucket keeps its hairline but drops to the border colour. Drawn
                        // in the series colour it reads as a dashed data line running along the
                        // baseline — which is what a month of quiet days looked like — rather than
                        // as thirty days on which nothing happened.
                        color = if (value <= 0L) {
                            colors.border
                        } else {
                            columnColor(
                                accent = accents[index] ?: ColumnAccent.PLAIN,
                                selected = index == selectedIndex,
                                colors = colors
                            )
                        }
                    )
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(AppDimens.hairline)
                .background(colors.border)
        )

        if (labels.isNotEmpty()) {
            Spacer(Modifier.height(AppChartDimens.columnLabelGap))
            AxisLabels(labels = labels, columnCount = values.size, selectedIndex = selectedIndex)
        }
    }
}

/**
 * The axis under the plot, in one of two modes.
 *
 * Two labels for more than two columns means "the ends of the span" — a run too dense to label
 * bucket by bucket. Any other count is one label per column, laid out on the same weights as the
 * columns above so a weekday chart's Mon–Sun sit under their own bars rather than near them.
 */
@Composable
private fun AxisLabels(labels: List<String>, columnCount: Int, selectedIndex: Int?) {
    val colors = AppTheme.colors

    if (labels.size == 2 && columnCount > 2) {
        // Two weighted halves with a gap between them, not `SpaceBetween`. Under SpaceBetween the
        // two ends are laid out at their full width and simply touch once they are long enough —
        // which is how "Tuesday, 05 May 2026" ended up welded to the label beside it. Weighting
        // reserves the gap before either is measured, and each end ellipsises inside its own half.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.labelToAmountGap)
        ) {
            Text(
                text = labels[0],
                style = AppTypography.eyebrow,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = labels[1],
                style = AppTypography.eyebrow,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
        return
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppChartDimens.columnGap)
    ) {
        labels.take(columnCount).forEachIndexed { index, label ->
            Text(
                text = label,
                style = AppTypography.eyebrow,
                color = if (index == selectedIndex) colors.textPrimary else colors.textMuted,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * A peak is drawn in the spend colour and a trough in the success colour, which is what those two
 * already mean everywhere else in the app — no third pair of chart colours is introduced.
 *
 * A selected column outranks both: the user pointed at it, and "what did I just tap" has to have an
 * unambiguous answer even when the tap landed on the peak.
 */
private fun columnColor(accent: ColumnAccent, selected: Boolean, colors: AppColors): Color = when {
    selected -> colors.accentGraphic
    accent == ColumnAccent.PEAK -> colors.danger
    accent == ColumnAccent.TROUGH -> colors.success
    else -> colors.chartSeries.first()
}

/**
 * One column.
 *
 * A zero-height column still gets a hairline, for the reason the paired chart's bars do: a day with
 * no spending and a day with no data look identical once the bar disappears, and they are not the
 * same fact.
 *
 * The column grows into place, so changing the range or the trend toggle animates the comparison
 * rather than replacing it.
 */
@Composable
private fun ColumnBar(fraction: Float, plotHeight: Dp, color: Color) {
    val target = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(GROW_MILLIS, easing = FastOutSlowInEasing),
        label = "columnHeight"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height((plotHeight * animated).coerceAtLeast(AppDimens.hairline))
            .clip(AppChartShapes.column)
            .background(color)
    )
}
