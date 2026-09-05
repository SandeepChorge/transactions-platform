package com.madtitan94.transactionsparser.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.madtitan94.transactionsparser.core.designsystem.R

/**
 * Both families ship as a single variable font each — Google Fonts publishes no static instances
 * — so every weight is cut from the same file through [FontVariation]. That is why there are two
 * `.ttf` files in `res/font` rather than six, and why this needs API 26 (the module's `minSdk`).
 */
@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

/** Sora — everything that is read as language. */
val Sora = FontFamily(
    variableFont(R.font.sora_variable, FontWeight.Normal),
    variableFont(R.font.sora_variable, FontWeight.Medium),
    variableFont(R.font.sora_variable, FontWeight.SemiBold)
)

/**
 * JetBrains Mono — every amount, and the uppercase eyebrows. Amounts are monospaced so that
 * digits line up column-wise down a list; that is the whole reason for the second family.
 */
val JetBrainsMono = FontFamily(
    variableFont(R.font.jetbrains_mono_variable, FontWeight.Normal),
    variableFont(R.font.jetbrains_mono_variable, FontWeight.Medium),
    variableFont(R.font.jetbrains_mono_variable, FontWeight.SemiBold)
)

// Compose adds font padding above and below a line by default, which throws off any spacing
// measured off the artboards. Switching it off is what makes px-is-dp actually hold.
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

/**
 * The type ramp, read back out of `design/HomeDark.dc.html` and `design/HomeLight.dc.html`.
 *
 * Artboards are 412 × 892 at 1× density, so every `px` there is an `sp`/`dp` here one for one.
 */
object AppTypography {

    /** The one big number on a screen — a month's total. */
    val hero = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.03).em,
        platformStyle = NoFontPadding
    )

    /** Screen title — "June". */
    val title = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.01).em,
        platformStyle = NoFontPadding
    )

    /** Primary button label. */
    val button = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        platformStyle = NoFontPadding
    )

    /** Section header — "Where it went" — and the heading inside an alert card. */
    val sectionHeader = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        platformStyle = NoFontPadding
    )

    /** A list row's label. */
    val row = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        platformStyle = NoFontPadding
    )

    /** An amount inside a list row. Monospaced so the digits align down the column. */
    val amount = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        platformStyle = NoFontPadding
    )

    /** Explanatory copy under a heading. */
    val body = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        platformStyle = NoFontPadding
    )

    /** The uppercase mono label above a card — "STATEMENT READ · 142 ROWS". */
    val eyebrow = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.06.em,
        platformStyle = NoFontPadding
    )

    /** Bottom-bar label. */
    val navLabel = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        platformStyle = NoFontPadding
    )

    /** Bottom-bar label for the destination you are on. */
    val navLabelActive = navLabel.copy(fontWeight = FontWeight.Medium)

    /** Avatar initials. */
    val avatarInitials = TextStyle(
        fontFamily = Sora,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        platformStyle = NoFontPadding
    )
}

/**
 * The same ramp expressed as a Material 3 [Typography], so components that read
 * `MaterialTheme.typography` — rather than [AppTypography] directly — still get Sora instead of
 * the platform default.
 */
internal val AppMaterialTypography = Typography(
    displaySmall = AppTypography.hero,
    headlineSmall = AppTypography.title,
    titleMedium = AppTypography.sectionHeader,
    bodyLarge = AppTypography.row,
    bodyMedium = AppTypography.body,
    labelLarge = AppTypography.button,
    // Material reads `labelMedium` for navigation-bar item labels and small chips. This slot used
    // to hold `eyebrow` — the uppercase mono label meant for "STATEMENT READ · 142 ROWS" — which
    // is why the bottom bar rendered in JetBrains Mono with 0.06em tracking instead of the
    // design's Sora nav label. `eyebrow` has no Material slot on purpose: it is a bespoke style,
    // reached directly as `AppTypography.eyebrow` by the screen that wants it.
    labelMedium = AppTypography.navLabel,
    labelSmall = AppTypography.navLabel
)
