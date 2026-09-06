package com.madtitan94.transactionsparser.core.designsystem.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The proportion chart — W7.
 *
 * Drawn on a `Canvas` rather than with a charting library: the app ships four chart shapes in
 * total, all of them simple, and a library would arrive with its own palette, its own type and its
 * own idea of what an axis looks like, none of which match the artboards.
 *
 * [centerContent] is a slot rather than a string so the caller can put the period's total in the
 * hole without this composable needing to know how money is formatted — and so it can swap to the
 * selected slice's own total when one is picked.
 *
 * **Selection is the caller's state, not the chart's.** [selectedIndex] comes in and
 * [onSliceClick] goes out, so tapping a slice and tapping its legend row drive the same one
 * highlight. A chart that owned its own selection would let the two disagree.
 *
 * An empty period draws the ring in `surfaceAlt` and keeps its size. That is deliberate: a donut
 * that vanishes when there is nothing to show makes the card jump, and W7 asks for the centre to
 * keep reading ₹0.
 *
 * [contentDescription] is required for a chart that carries meaning, and it is a parameter rather
 * than something built here because the copy belongs in the feature module's `strings.xml` — this
 * module owns no user-facing English. Without it the whole donut is invisible to TalkBack, since a
 * `Canvas` has nothing to read.
 */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    box: Dp = AppChartDimens.donutBox,
    radius: Dp = AppChartDimens.donutRadius,
    strokeWidth: Dp = AppChartDimens.donutStroke,
    sliceGap: Dp = AppChartDimens.donutSliceGap,
    selectedIndex: Int? = null,
    onSliceClick: ((Int) -> Unit)? = null,
    contentDescription: String? = null,
    animated: Boolean = true,
    centerContent: (@Composable () -> Unit)? = null
) {
    val colors = AppTheme.colors

    // Re-sweeping from zero whenever the data changes is a deliberate choice over morphing one set
    // of slices into the next. A range change is not one month growing into another — it is a
    // different question being asked — and the redraw says so. It also means the enter animation
    // and the update animation are the same code.
    val reveal = remember { Animatable(if (animated) 0f else 1f) }
    LaunchedEffect(slices, animated) {
        if (!animated) {
            reveal.snapTo(1f)
        } else {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(durationMillis = 550, easing = FastOutSlowInEasing))
        }
    }

    // The selected slice lifts out of the ring rather than the others dimming down. Dimming would
    // put the series colours below the contrast they were validated at, and the validation is the
    // reason this palette can be trusted at all.
    val lift by animateDpAsState(
        targetValue = if (selectedIndex != null) AppChartDimens.donutSelectedLift else 0.dp,
        label = "donutLift"
    )

    Box(
        modifier
            .size(box)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            Modifier
                .size(box)
                .then(
                    if (onSliceClick == null) {
                        Modifier
                    } else {
                        Modifier.pointerInput(slices, radius, strokeWidth, sliceGap) {
                            detectTapGestures { tap ->
                                val middle = Offset(size.width / 2f, size.height / 2f)
                                val ringRadius = radius.toPx()
                                val stroke = strokeWidth.toPx()
                                val distance = hypot(tap.x - middle.x, tap.y - middle.y)

                                // The ring is 17dp thick, well under the 48dp minimum target, so
                                // the hit band is widened on both sides. It stops short of
                                // swallowing the hole: the centre holds the period total and is
                                // not a slice.
                                val slack = AppChartDimens.donutHitSlack.toPx()
                                val inner = (ringRadius - stroke / 2f - slack)
                                    .coerceAtLeast(ringRadius - stroke / 2f - stroke)
                                val outer = ringRadius + stroke / 2f + slack
                                if (distance < inner || distance > outer) return@detectTapGestures

                                val angle = Math.toDegrees(
                                    atan2(
                                        (tap.y - middle.y).toDouble(),
                                        (tap.x - middle.x).toDouble()
                                    )
                                ).toFloat()

                                val arcs = donutArcs(
                                    slices.map { it.amountPaise },
                                    gapDegreesFor(sliceGap.toPx(), ringRadius)
                                )
                                sliceIndexAt(arcs, angle)?.let(onSliceClick)
                            }
                        }
                    }
                )
        ) {
            val stroke = strokeWidth.toPx()
            val ringRadius = radius.toPx()
            val middle = center
            val arcs = donutArcs(
                slices.map { it.amountPaise },
                gapDegreesFor(sliceGap.toPx(), ringRadius)
            )

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
                val sweep = arc.sweepDegrees * reveal.value
                if (sweep <= 0f) return@forEachIndexed

                val selected = i == selectedIndex
                val sliceRadius = if (selected) ringRadius + lift.toPx() else ringRadius
                val sliceStroke = if (selected) stroke + lift.toPx() else stroke

                if (slice.slot == null) {
                    // The hatch is a texture, so it cannot ride on a stroke colour the way a solid
                    // slice does. Clipping to the wedge and filling it is the only way to get the
                    // stripe to follow the ring.
                    clipPath(
                        ringSegmentPath(
                            middle = middle,
                            innerRadius = sliceRadius - sliceStroke / 2f,
                            outerRadius = sliceRadius + sliceStroke / 2f,
                            startDegrees = arc.startDegrees,
                            sweepDegrees = sweep
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
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(middle.x - sliceRadius, middle.y - sliceRadius),
                        size = Size(sliceRadius * 2f, sliceRadius * 2f),
                        style = Stroke(width = sliceStroke)
                    )
                }
            }
        }
        centerContent?.invoke()
    }
}

/**
 * The slice gap, specified as a distance along the ring, converted for this donut's own
 * circumference.
 *
 * Hard-coding a degree value would open a visibly wider gap on a smaller donut, and the gallery
 * draws two sizes side by side.
 */
private fun gapDegreesFor(gapPx: Float, ringRadius: Float): Float {
    val circumference = 2f * Math.PI.toFloat() * ringRadius
    return if (circumference <= 0f) 0f else gapPx / circumference * 360f
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
