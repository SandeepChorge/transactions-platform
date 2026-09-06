package com.madtitan94.transactionsparser.core.designsystem.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme

/**
 * The proportion chart — W7.
 *
 * Drawn on a `Canvas` rather than with a charting library: the app ships four chart shapes in
 * total, all of them simple, and a library would arrive with its own palette, its own type and its
 * own idea of what an axis looks like, none of which match the artboards.
 *
 * [centerContent] is a slot rather than a string so the caller can put the period's total in the
 * hole without this composable needing to know how money is formatted.
 *
 * An empty period draws the ring in `surfaceAlt` and keeps its size. That is deliberate: a donut
 * that vanishes when there is nothing to show makes the card jump, and W7 asks for the centre to
 * keep reading ₹0.
 */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    box: Dp = AppChartDimens.donutBox,
    radius: Dp = AppChartDimens.donutRadius,
    strokeWidth: Dp = AppChartDimens.donutStroke,
    sliceGap: Dp = AppChartDimens.donutSliceGap,
    centerContent: (@Composable () -> Unit)? = null
) {
    val colors = AppTheme.colors

    Box(modifier.size(box), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(box)) {
            val stroke = strokeWidth.toPx()
            val ringRadius = radius.toPx()
            val middle = center

            // The gap is specified as a distance along the ring, so it has to be converted against
            // this donut's own circumference. Hard-coding a degree value would open a visibly
            // wider gap on a smaller donut.
            val circumference = 2f * Math.PI.toFloat() * ringRadius
            val gapDegrees =
                if (circumference <= 0f) 0f else sliceGap.toPx() / circumference * 360f

            val arcs = donutArcs(slices.map { it.amountPaise }, gapDegrees)

            if (arcs.isEmpty()) {
                drawCircle(
                    color = colors.surfaceAlt,
                    radius = ringRadius,
                    style = Stroke(width = stroke)
                )
                return@Canvas
            }

            slices.forEachIndexed { i, slice ->
                val arc = arcs[i]
                if (arc.sweepDegrees <= 0f) return@forEachIndexed

                if (slice.slot == null) {
                    // The hatch is a texture, so it cannot ride on a stroke colour the way a solid
                    // slice does. Clipping to the wedge and filling it is the only way to get the
                    // stripe to follow the ring.
                    clipPath(
                        ringSegmentPath(
                            middle = middle,
                            innerRadius = ringRadius - stroke / 2f,
                            outerRadius = ringRadius + stroke / 2f,
                            startDegrees = arc.startDegrees,
                            sweepDegrees = arc.sweepDegrees
                        )
                    ) {
                        drawHatch(
                            stripe = colors.chartHatchStripe,
                            ground = colors.chartHatchGround,
                            periodPx = AppChartDimens.hatchPeriod.toPx(),
                            stripeWidthPx = AppChartDimens.hatchStripe.toPx()
                        )
                    }
                } else {
                    drawArc(
                        color = chartSlotColor(colors.chartSeries, slice.slot),
                        startAngle = arc.startDegrees,
                        sweepAngle = arc.sweepDegrees,
                        useCenter = false,
                        topLeft = Offset(middle.x - ringRadius, middle.y - ringRadius),
                        size = Size(ringRadius * 2f, ringRadius * 2f),
                        style = Stroke(width = stroke)
                    )
                }
            }
        }
        centerContent?.invoke()
    }
}

/**
 * The wedge between two radii — the shape a stroked arc covers, as a fillable path.
 *
 * Compose can stroke an arc but cannot clip to one, so the ring segment is rebuilt here: out along
 * the outer edge, back along the inner one.
 */
private fun ringSegmentPath(
    middle: Offset,
    innerRadius: Float,
    outerRadius: Float,
    startDegrees: Float,
    sweepDegrees: Float
): Path = Path().apply {
    val outer = Rect(
        left = middle.x - outerRadius,
        top = middle.y - outerRadius,
        right = middle.x + outerRadius,
        bottom = middle.y + outerRadius
    )
    val inner = Rect(
        left = middle.x - innerRadius,
        top = middle.y - innerRadius,
        right = middle.x + innerRadius,
        bottom = middle.y + innerRadius
    )
    arcTo(outer, startDegrees, sweepDegrees, forceMoveTo = true)
    arcTo(inner, startDegrees + sweepDegrees, -sweepDegrees, forceMoveTo = false)
    close()
}
