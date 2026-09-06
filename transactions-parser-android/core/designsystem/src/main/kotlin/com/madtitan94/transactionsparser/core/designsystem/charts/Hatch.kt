package com.madtitan94.transactionsparser.core.designsystem.charts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import com.madtitan94.transactionsparser.core.designsystem.theme.AppChartDefaults
import com.madtitan94.transactionsparser.core.designsystem.theme.AppDimens
import com.madtitan94.transactionsparser.core.designsystem.theme.AppShapes
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme
import kotlin.math.hypot

/**
 * The 115° hatch that stands in for unnamed spend.
 *
 * It is a texture rather than an eighth colour on purpose: `design/Charts.dc.html` reserves the
 * seven series slots for things the user has actually named, and unmapped money has to be visibly
 * *not one of them*. It is drawn, never hidden — an unnamed payee is the thing the dashboard most
 * needs to admit.
 *
 * The stripe fills whatever the current [DrawScope] covers, so callers clip first: `clipPath` for a
 * donut slice, a clipped `Modifier` for a swatch or a bar segment.
 */
fun DrawScope.drawHatch(
    stripe: Color,
    ground: Color,
    periodPx: Float,
    stripeWidthPx: Float,
    angleDegrees: Float = AppChartDefaults.HATCH_ANGLE_DEGREES
) {
    drawRect(color = ground)
    if (periodPx <= 0f || stripeWidthPx <= 0f) return

    // The stripes are drawn upright and the frame is rotated under them. CSS measures a gradient
    // angle clockwise from "up" and lays its bands across that axis, so an upright band needs the
    // frame turned by angle - 90 to land where the artboard puts it.
    //
    // Rotating a rect leaves its corners outside the original bounds, so the run is laid out over a
    // square of the diagonal in both directions and the clip takes care of the excess.
    val reach = hypot(size.width, size.height)
    rotate(degrees = angleDegrees - 90f) {
        var x = center.x - reach
        while (x < center.x + reach) {
            drawRect(
                color = stripe,
                topLeft = Offset(x, center.y - reach),
                size = Size(stripeWidthPx, reach * 2f)
            )
            x += periodPx
        }
    }
}

/**
 * Paints the hatch behind whatever this modifies, at the given scale.
 *
 * Clip before calling — the stripe respects the clip and nothing else.
 */
fun Modifier.hatchBackground(
    stripe: Color,
    ground: Color,
    period: Dp = AppChartDimens.hatchPeriod,
    stripeWidth: Dp = AppChartDimens.hatchStripe
): Modifier = drawBehind {
    drawHatch(
        stripe = stripe,
        ground = ground,
        periodPx = period.toPx(),
        stripeWidthPx = stripeWidth.toPx()
    )
}

/**
 * The square beside a legend row or a ranked row.
 *
 * A null [slot] is the unnamed slice, and it gets the hatch rather than a colour — so the legend
 * carries the same distinction the chart does, and a colour-blind reader has the texture to go on
 * as well as the label.
 */
@Composable
fun CategorySwatch(
    slot: Int?,
    modifier: Modifier = Modifier,
    size: Dp = AppDimens.swatchSize
) {
    val colors = AppTheme.colors
    val base = modifier
        .size(size)
        .clip(AppShapes.bar)

    if (slot == null) {
        Box(
            base.hatchBackground(
                stripe = colors.chartHatchStripe,
                ground = colors.chartHatchGround,
                period = AppChartDimens.swatchHatchPeriod,
                stripeWidth = AppChartDimens.swatchHatchStripe
            )
        )
    } else {
        Box(base.drawBehind { drawRect(chartSlotColor(colors.chartSeries, slot)) })
    }
}

/**
 * The colour for a slot, guarded against a palette shorter than the slot index.
 *
 * `buildCategorySlices` cannot produce an out-of-range slot, but a caller assembling slices by hand
 * can, and a crash in a draw pass is a poor way to find out.
 */
internal fun chartSlotColor(series: List<Color>, slot: Int): Color =
    if (series.isEmpty()) Color.Transparent else series[slot.mod(series.size)]
