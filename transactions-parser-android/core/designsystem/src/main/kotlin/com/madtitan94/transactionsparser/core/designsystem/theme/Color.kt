package com.madtitan94.transactionsparser.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The app's colour tokens, transcribed from `design/Main.dc.html` and `design/Charts.dc.html`.
 *
 * Material 3's [androidx.compose.material3.ColorScheme] has no honest home for the status
 * colours, the accent card or the chart series, so those live here instead and travel through
 * [LocalAppColors]. `MaterialTheme` is still populated alongside it for the components that read
 * it directly.
 *
 * If a value here and the artboard ever disagree, the artboard wins — that is stated in
 * `design/README.md` and is the reason each token below names the file it came from rather than
 * being re-derived.
 */
@Immutable
data class AppColors(
    /** True for the dark theme. Only for the rare branch that cannot be expressed as a token. */
    val isDark: Boolean,

    // Surfaces
    /** Screen background, bottom bar. */
    val screen: Color,
    /** Cards, sheets, fields, unselected chips. */
    val surface: Color,
    /** Avatars, progress track, icon wells. */
    val surfaceAlt: Color,
    /** Read-only or disabled field. */
    val surfaceSunken: Color,

    // Borders
    val border: Color,
    val borderSubtle: Color,
    val borderStrong: Color,

    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,

    // Accent — amber
    /** Buttons, active nav, selected chips, focus rings. Identical in both themes. */
    val accent: Color,
    /** Text and icons drawn on top of [accent]. Identical in both themes. */
    val onAccent: Color,
    /** Amber where it has to be *read* as text. Collapses back to [accent] in dark. */
    val accentInk: Color,
    /** Amber where it has to hold up as a bar or fill. Collapses back to [accent] in dark. */
    val accentGraphic: Color,
    val accentSurface: Color,
    val accentBorder: Color,
    val accentHeading: Color,
    val accentBody: Color,

    // Success — mint
    val success: Color,
    val successMutedText: Color,
    val successSurface: Color,
    val successBorder: Color,

    // Danger — coral
    val danger: Color,
    val dangerMutedText: Color,
    val dangerSurface: Color,
    val dangerBorder: Color,

    // Charts
    /**
     * The seven category slots, in fixed order. Assign by category id, never by rank — filtering
     * to a shorter list must not repaint the survivors. An eighth category folds into
     * Uncategorised rather than getting a generated eighth hue.
     *
     * These values were computed and validated (lightness band, chroma floor, colour-blind and
     * normal-vision separation on every adjacent pair, contrast against this theme's own surface),
     * so substituting one by eye will quietly break a check.
     */
    val chartSeries: List<Color>,
    /** The 115° hatch drawn over unmapped spend — the stripe. */
    val chartHatchStripe: Color,
    /** The 115° hatch drawn over unmapped spend — the ground behind the stripe. */
    val chartHatchGround: Color
) {
    /** Money received. Mint, because it is genuinely a different kind of event. */
    val moneyIn: Color get() = success

    /**
     * Money spent. Plain primary text, **not** [danger] — spending is this app's ordinary case,
     * and painting it red would leave nothing to say when something has actually failed.
     */
    val moneyOut: Color get() = textPrimary
}

/**
 * Light — a paper ground stepped from the same ramps as dark, not dark run through an inversion.
 */
val LightAppColors = AppColors(
    isDark = false,

    screen = Color(0xFFF1F6F3),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFE5EBE7),
    surfaceSunken = Color(0xFFEBF1ED),

    border = Color(0xFFD3DBD7),
    borderSubtle = Color(0xFFE2E8E4),
    borderStrong = Color(0xFFAFBBB4),

    textPrimary = Color(0xFF161C19),
    textSecondary = Color(0xFF626C66),
    textMuted = Color(0xFF828C86),

    accent = Color(0xFFFFB020),
    onAccent = Color(0xFF0C1210),
    accentInk = Color(0xFF995C00),
    accentGraphic = Color(0xFFC98A12),
    accentSurface = Color(0xFFFFEFD1),
    accentBorder = Color(0xFFF5D6A3),
    accentHeading = Color(0xFF8C5500),
    accentBody = Color(0xFF705129),

    success = Color(0xFF007B48),
    successMutedText = Color(0xFF3E6F54),
    successSurface = Color(0xFFDFF9E9),
    successBorder = Color(0xFFB7E4C9),

    danger = Color(0xFFA52B1E),
    dangerMutedText = Color(0xFF8A5548),
    dangerSurface = Color(0xFFFFEDEA),
    dangerBorder = Color(0xFFF9C6BD),

    chartSeries = listOf(
        Color(0xFFCD8300),
        Color(0xFF5B7800),
        Color(0xFF00B290),
        Color(0xFF007AAD),
        Color(0xFF8089EF),
        Color(0xFF994695),
        Color(0xFFD96265)
    ),
    chartHatchStripe = Color(0xFFAFBBB4),
    chartHatchGround = Color(0xFFE5EBE7)
)

/**
 * Dark — the shipped design, transcribed unchanged.
 */
val DarkAppColors = AppColors(
    isDark = true,

    screen = Color(0xFF0E1512),
    surface = Color(0xFF141C18),
    surfaceAlt = Color(0xFF1A2420),
    surfaceSunken = Color(0xFF111813),

    border = Color(0xFF24312B),
    borderSubtle = Color(0xFF1F2B25),
    borderStrong = Color(0xFF3A4A42),

    textPrimary = Color(0xFFE9F1EB),
    textSecondary = Color(0xFF8B9B92),
    textMuted = Color(0xFF5F6F67),

    // The amber is the one byte the two themes share, and buttons use it as-is either way.
    // accentInk and accentGraphic exist only because amber on paper is too pale to read as text
    // or to hold its own as a bar; in dark they collapse back to the accent.
    accent = Color(0xFFFFB020),
    onAccent = Color(0xFF0C1210),
    accentInk = Color(0xFFFFB020),
    accentGraphic = Color(0xFFFFB020),
    accentSurface = Color(0xFF1D1A10),
    accentBorder = Color(0xFF4A3A14),
    accentHeading = Color(0xFFFFD98A),
    accentBody = Color(0xFFC7B389),

    success = Color(0xFF6EE7A8),
    successMutedText = Color(0xFFA8C9B8),
    successSurface = Color(0xFF1A2E24),
    successBorder = Color(0xFF235140),

    danger = Color(0xFFFF9C8C),
    dangerMutedText = Color(0xFFC79086),
    dangerSurface = Color(0xFF2A1714),
    dangerBorder = Color(0xFF4A2420),

    chartSeries = listOf(
        Color(0xFFC47B00),
        Color(0xFF547100),
        Color(0xFF00AA88),
        Color(0xFF0072A5),
        Color(0xFF7982E7),
        Color(0xFF913E8E),
        Color(0xFFD05A5E)
    ),
    chartHatchStripe = Color(0xFF3A4A42),
    chartHatchGround = Color(0xFF1A2420)
)

/**
 * Chart geometry that belongs with the palette rather than with a screen.
 */
object AppChartDefaults {
    /** The 115° angle the unmapped-spend hatch is drawn at, carried over from the app design. */
    const val HATCH_ANGLE_DEGREES = 115f

    /**
     * Ranked bars are alpha-stepped [AppColors.accentGraphic] rather than the category series:
     * the swatch beside the label carries identity, the bar only carries magnitude. Ranks past
     * the third reuse the last step.
     */
    val rankedBarAlphas = listOf(1f, 0.82f, 0.64f)

    fun rankedBarAlpha(rank: Int): Float =
        rankedBarAlphas[rank.coerceIn(0, rankedBarAlphas.lastIndex)]
}

/**
 * Defaults to dark so a composable previewed outside [TransactionsParserTheme] still renders the
 * app's own colours rather than Material's.
 */
val LocalAppColors = staticCompositionLocalOf { DarkAppColors }
