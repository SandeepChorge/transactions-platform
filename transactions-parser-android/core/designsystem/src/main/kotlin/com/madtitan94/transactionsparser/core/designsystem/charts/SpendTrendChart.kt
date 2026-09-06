package com.madtitan94.transactionsparser.core.designsystem.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme

/**
 * The shape of a period's spending — W2.
 *
 * No axes, no gridlines and no labels: the widget's header row carries the average and the card
 * underneath carries the numbers, so the chart's whole job is the silhouette.
 *
 * **The scrubber is conditional on there being somewhere to put the read-out.** W2 says not to add
 * one "unless the label row can show the read-out", which is a constraint on the widget rather than
 * a ban on the interaction. Pass [onScrub] and the chart reports which bucket the finger is on; the
 * widget puts that day's total in its header where the average normally sits. Leave [onScrub] null
 * and the chart stays inert, exactly as before.
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
    strokeWidth: Dp = AppChartDimens.trendStroke,
    scrubbedIndex: Int? = null,
    onScrub: ((Int?) -> Unit)? = null,
    contentDescription: String? = null,
    animated: Boolean = true
) {
    val colors = AppTheme.colors
    val fractions = trendFractions(values)

    // The line draws itself on from the left rather than fading in. A trend has a direction, and
    // the reveal is the one piece of motion that says which way to read it.
    val reveal = remember { Animatable(if (animated) 0f else 1f) }
    LaunchedEffect(values, animated) {
        if (!animated) {
            reveal.snapTo(1f)
        } else {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(durationMillis = 650, easing = FastOutSlowInEasing))
        }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
            .then(
                if (onScrub == null) {
                    Modifier
                } else {
                    Modifier
                        .pointerInput(values) {
                            detectTapGestures { tap ->
                                onScrub(bucketIndexAt(tap.x, size.width.toFloat(), fractions.size))
                            }
                        }
                        .pointerInput(values) {
                            // Dragging is the gesture people actually reach for on a line, and the
                            // read-out follows the finger rather than waiting for it to lift.
                            detectHorizontalDragGestures(
                                onDragEnd = { },
                                onDragCancel = { onScrub(null) }
                            ) { change, _ ->
                                onScrub(
                                    bucketIndexAt(
                                        change.position.x,
                                        size.width.toFloat(),
                                        fractions.size
                                    )
                                )
                            }
                        }
                }
            )
    ) {
        if (fractions.size < 2) return@Canvas

        val stroke = strokeWidth.toPx()
        // The stroke straddles the path, so a point at full height would be clipped in half at the
        // top of the plot and a flat zero line would lose its lower half at the bottom.
        val top = stroke / 2f
        val usable = size.height - stroke
        val step = size.width / (fractions.size - 1)

        val points = fractions.mapIndexed { i, fraction ->
            Offset(i * step, top + usable * (1f - fraction))
        }

        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }

        val area = Path().apply {
            addPath(line)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }

        clipRect(right = size.width * reveal.value) {
            drawPath(path = area, color = colors.accentSurface)
            drawPath(
                path = line,
                color = colors.accentGraphic,
                style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        val marked = scrubbedIndex?.takeIf { it in points.indices } ?: return@Canvas
        val point = points[marked]

        drawLine(
            color = colors.borderStrong,
            start = Offset(point.x, 0f),
            end = Offset(point.x, size.height),
            strokeWidth = AppChartDimens.crosshair.toPx()
        )
        // A ring of the card's own colour under the marker keeps it legible where it sits on top of
        // the line it is reading.
        drawCircle(
            color = colors.surface,
            radius = (AppChartDimens.trendMarkerRadius + AppChartDimens.trendMarkerRing).toPx(),
            center = point
        )
        drawCircle(
            color = colors.accentGraphic,
            radius = AppChartDimens.trendMarkerRadius.toPx(),
            center = point
        )
    }
}
