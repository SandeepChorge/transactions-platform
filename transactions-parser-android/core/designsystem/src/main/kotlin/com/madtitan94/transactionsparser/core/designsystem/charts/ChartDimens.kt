package com.madtitan94.transactionsparser.core.designsystem.charts

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Chart metrics, read off `design/DashboardSpec.dc.html` §2.
 *
 * They live here rather than in `AppDimens` because that object is measured off the Home artboards
 * and is the shell's vocabulary; these are the dashboard's. Nothing is rounded to a 4/8dp grid —
 * the 17dp donut stroke and the 2.5dp trend line are the design's values, and snapping them changes
 * how the charts read.
 */
object AppChartDimens {

    // Donut — W7
    /** Outer box of the donut, including the space the stroke needs. */
    val donutBox = 150.dp

    /** Radius of the ring's centreline, so the stroke straddles it. */
    val donutRadius = 46.dp
    val donutStroke = 17.dp

    /**
     * Card background left showing between slices. See `donutArcs` for why this is here at all —
     * the spec asks for none.
     */
    val donutSliceGap = 2.dp

    /** How far the selected slice lifts out of the ring, in both radius and thickness. */
    val donutSelectedLift = 4.dp

    /**
     * Taken off the hole's diameter to bound whatever the caller puts in the middle of the donut.
     *
     * The centre is a `Box` centred over the `Canvas`, so nothing about the ring constrains it on
     * its own: a caption wider than the hole lays itself out straight across the ring and over
     * whatever sits beside the chart. A custom range reads "01 Jun 2026 – 30 Jun 2026", which is
     * twice the width of the hole. The inset also stops a clamped line from touching the inner edge
     * of the stroke.
     */
    val donutCenterInset = 10.dp

    /**
     * Added to each side of the ring's hit band. The ring is 17dp thick and the minimum touch
     * target is 48dp, so a tap needs more room than the stroke gives it.
     */
    val donutHitSlack = 10.dp

    /** The scrubber's marker on the trend line, and the ring of card colour around it. */
    val trendMarkerRadius = 5.dp
    val trendMarkerRing = 2.dp

    /** The vertical hairline the scrubber drops through the plot. */
    val crosshair = 1.dp

    /** Backdrop behind a selected bar pair or ranked row. */
    val selectionPadding = 6.dp

    // Trend line — W2
    val trendHeight = 96.dp
    val trendStroke = 2.5.dp

    // Paired bars — W6
    val pairedPlotHeight = 132.dp
    val pairedBarWidth = 14.dp

    /** Between the in and out bar of one bucket. */
    val pairedBarGap = 5.dp

    /** Between the plot's baseline and the bucket labels under it. */
    val pairedLabelGap = 10.dp

    // Stacked share bar — design/Charts.dc.html
    val shareBarHeight = 14.dp

    // Tracks
    /** Under a payee row (W10). */
    val trackInline = 5.dp

    /** Beside a category row (W3). */
    val trackCategory = 6.dp

    /** Under a budget row (W12). */
    val trackBudget = 7.dp

    /** The mapping-health hero (W8) — the one track meant to be read across the room. */
    val trackMapping = 10.dp

    /**
     * Fixed width of a category row's track. Fixed, not proportional, so the amounts on the right
     * stay in one column whatever the longest category name is.
     */
    val categoryTrackWidth = 76.dp

    // Hatch — the 115° stripe over unnamed spend, from design/Charts.dc.html
    val hatchPeriod = 10.dp
    val hatchStripe = 5.dp

    /** The same hatch at swatch scale; the full-size period is invisible in a 10dp square. */
    val swatchHatchPeriod = 6.dp
    val swatchHatchStripe = 3.dp

    // Tiles — W5
    val statTilePadding = 16.dp
    val statTileGap = 10.dp
}

/**
 * Radii the charts need that the shell's [com.madtitan94.transactionsparser.core.designsystem.theme.AppShapes]
 * has no slot for.
 */
object AppChartShapes {

    /** A KPI tile. Smaller than a card, which is what marks it as part of a row rather than one. */
    val statTile = RoundedCornerShape(18.dp)

    /** A vertical bar in the paired plot: rounded at the top, square where it meets the baseline. */
    val pairedBar = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
}
