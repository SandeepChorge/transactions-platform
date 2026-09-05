package com.madtitan94.transactionsparser.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing and component metrics measured off the Home artboards.
 *
 * The artboards are 412 × 892 at 1× density, so each value below is the artboard's pixel count
 * unchanged. Nothing here is rounded to a 4/8dp grid — several of the design's values (7dp, 22dp,
 * 58dp) are deliberately off-grid and snapping them would visibly change the layout.
 */
object AppDimens {

    // Screen
    /** Left and right padding of a screen's content column. */
    val screenHorizontalPadding = 24.dp

    /**
     * Distance from the physical top of the frame to the screen title.
     *
     * Measured on the artboard, which draws no status bar, so this **includes** the space the
     * status bar occupies on a device. Apply it to the frame, not on top of a status-bar inset,
     * or the header will sit too low.
     */
    val screenTopPadding = 58.dp

    /** Vertical gap between the major blocks of a screen. */
    val sectionGap = 22.dp

    // Cards
    /** Padding inside a standard card. */
    val cardPadding = 22.dp

    /** Padding inside an alert card, which carries less inside it. */
    val alertCardPadding = 20.dp

    /** Every border in the design is a hairline; nothing is heavier. */
    val hairline = 1.dp

    /** The 1.5dp variant, used only with [AppColors.borderStrong]. */
    val hairlineStrong = 1.5.dp

    // Lists and bars
    /** Gap between ranked rows. */
    val rowGap = 12.dp

    /** Gap between a row's label and its bar. */
    val rowLabelToBarGap = 6.dp

    /** Height of a progress bar or a segmented total bar. */
    val barHeight = 6.dp

    /**
     * Gap between adjacent segments of the segmented total bar. The gap is the card's own
     * background showing through, which is what keeps two adjacent series colours legible.
     */
    val barSegmentGap = 2.dp

    /** The category swatch beside a ranked row's label. */
    val swatchSize = 10.dp

    /** Gap between that swatch and the row it labels. */
    val swatchGap = 12.dp

    // Controls
    /** Height of the primary button. */
    val buttonHeight = 54.dp

    /** Avatar in the screen header. */
    val avatarSize = 38.dp

    /** The dot that opens an alert card. */
    val alertDotSize = 8.dp

    // Bottom bar
    val bottomBarHorizontalPadding = 18.dp
    val bottomBarTopPadding = 10.dp
    val bottomBarBottomPadding = 4.dp
    val bottomBarItemBottomPadding = 8.dp
    val bottomBarIconSize = 22.dp
    val bottomBarIconToLabelGap = 5.dp
}
