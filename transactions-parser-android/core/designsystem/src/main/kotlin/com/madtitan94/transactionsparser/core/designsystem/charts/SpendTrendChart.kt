package com.madtitan94.transactionsparser.core.designsystem.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme

/**
 * The shape of a period's spending — W2.
 *
 * No axes, no gridlines, no labels, and no tooltip: the widget's header row carries the average and
 * the card underneath carries the numbers, so the chart's whole job is the silhouette. Adding a
 * scrubber here would need somewhere to put the read-out, which the design does not have.
 *
 * Buckets are drawn in the order given and never re-sorted; zero-filling gaps is the caller's job,
 * because only the caller knows whether a missing bucket means ₹0 or means the range stops there.
 *
 * The line uses `accentGraphic` rather than `accent` — 2.5dp of plain amber on the light paper is
 * too pale to follow, and the design already carries a darker relative for exactly this. In dark
 * the two are the same colour, so nothing moves.
 */
@Composable
fun SpendTrendChart(
    values: List<Long>,
    modifier: Modifier = Modifier,
    height: Dp = AppChartDimens.trendHeight,
    strokeWidth: Dp = AppChartDimens.trendStroke
) {
    val colors = AppTheme.colors
    val fractions = trendFractions(values)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
    ) {
        if (fractions.size < 2) return@Canvas

        val stroke = strokeWidth.toPx()
        // The stroke straddles the path, so a point at full height would be clipped in half at the
        // top of the plot and a flat zero line would lose its lower half at the bottom.
        val top = stroke / 2f
        val usable = size.height - stroke
        val step = size.width / (fractions.size - 1)

        val points = fractions.mapIndexed { i, fraction ->
            i * step to top + usable * (1f - fraction)
        }

        val line = Path().apply {
            moveTo(points.first().first, points.first().second)
            points.drop(1).forEach { (x, y) -> lineTo(x, y) }
        }

        val area = Path().apply {
            addPath(line)
            lineTo(points.last().first, size.height)
            lineTo(points.first().first, size.height)
            close()
        }

        drawPath(path = area, color = colors.accentSurface)
        drawPath(
            path = line,
            color = colors.accentGraphic,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
