package com.madtitan94.transactionsparser.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The radius scale, read back out of the Home artboards.
 *
 * It turned out to be four radii rather than one: the difference between a 20dp card and a 14dp
 * button is deliberate and load-bearing, so a single `MaterialTheme.shapes.medium` everywhere
 * would flatten the design rather than simplify it.
 */
object AppShapes {

    /** Cards, sheets, alert panels. */
    val card = RoundedCornerShape(20.dp)

    /** The primary button. */
    val button = RoundedCornerShape(14.dp)

    /**
     * Small chips and colour swatches on a settings-style surface, from `design/Main.dc.html`.
     * Note the Home screen's 10dp category swatch is [bar], not this — it reads as a piece of the
     * chart, not as a control.
     */
    val chip = RoundedCornerShape(6.dp)

    /** Bars, progress tracks, and the category swatch beside a ranked row. */
    val bar = RoundedCornerShape(3.dp)

    /** Avatars. */
    val avatar = CircleShape
}

/**
 * The same scale as a Material 3 [Shapes], for components that read `MaterialTheme.shapes`.
 */
internal val AppMaterialShapes = Shapes(
    extraSmall = AppShapes.bar,
    small = AppShapes.chip,
    medium = AppShapes.button,
    large = AppShapes.card,
    extraLarge = AppShapes.card
)
