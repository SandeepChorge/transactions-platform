package com.madtitan94.transactionsparser.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Projects [AppColors] onto a Material 3 [ColorScheme], so a Material component that was not
 * written against [AppColors] still lands on the app's palette instead of Material's defaults.
 *
 * The scheme is the narrower of the two — it has no slot for the accent card, the status
 * surfaces, or the chart series — so [AppColors] stays the source of truth and this is a
 * projection of it, never the other way round.
 */
private fun AppColors.toMaterialColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        // `accentInk`, not `accent`. Material spends `primary` on text as often as on fills —
        // TextButton labels, selected tab and navigation labels, icon tints — and plain amber as
        // text on the light paper is about 1.6:1, which is unreadable. `accentInk` is the design's
        // own answer to "amber, but as text", and in dark it *is* the accent, so nothing moves
        // there. The cost is that a Material-default filled button is the darker amber rather than
        // the artboard's `accent`; a screen that wants the artboard exactly reaches for
        // `AppTheme.colors.accent` directly, which is what the restyle does.
        primary = accentInk,
        onPrimary = if (isDark) onAccent else surface,
        primaryContainer = accentSurface,
        onPrimaryContainer = accentHeading,

        secondary = accentInk,
        onSecondary = onAccent,
        secondaryContainer = accentSurface,
        onSecondaryContainer = accentBody,

        tertiary = success,
        onTertiary = if (isDark) screen else surface,
        tertiaryContainer = successSurface,
        onTertiaryContainer = successMutedText,

        background = screen,
        onBackground = textPrimary,

        surface = this.surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceAlt,
        onSurfaceVariant = textSecondary,
        surfaceContainerLowest = screen,
        surfaceContainerLow = surfaceSunken,
        surfaceContainer = this.surface,
        surfaceContainerHigh = surfaceAlt,
        surfaceContainerHighest = surfaceAlt,

        outline = border,
        outlineVariant = borderSubtle,

        error = danger,
        onError = if (isDark) screen else surface,
        errorContainer = dangerSurface,
        onErrorContainer = if (isDark) danger else dangerMutedText
    )
}

/**
 * The app's theme.
 *
 * Dynamic colour is deliberately absent. On Android 12+ it would repaint everything from the
 * user's wallpaper: the amber that carries the app's identity would be gone, `success` and
 * `danger` would stop being distinguishable at a glance, and the chart series — the part that was
 * actually validated for contrast and colour-blindness — would become arbitrary. The design is
 * only true with it off, so there is no flag to turn it back on.
 *
 * @param darkTheme which of the two palettes to use. The caller decides, because the user's
 *   stored preference can override the system setting; [isSystemInDarkTheme] is only the default
 *   for previews and for a caller that has no preference to consult.
 */
@Composable
fun TransactionsParserTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(
        LocalAppColors provides appColors,
        // Material's default content colour is black regardless of scheme, which would leave any
        // Text outside a Surface unreadable on the dark palette.
        LocalContentColor provides appColors.textPrimary
    ) {
        MaterialTheme(
            colorScheme = appColors.toMaterialColorScheme(),
            typography = AppMaterialTypography,
            shapes = AppMaterialShapes,
            content = content
        )
    }
}

/**
 * The app's colours, typography, shapes and spacing at a call site.
 *
 * `AppTheme.colors.textPrimary` reads better than `LocalAppColors.current.textPrimary`, and it
 * keeps every screen pointing at one name.
 */
object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current

    val typography: AppTypography get() = AppTypography

    val shapes: AppShapes get() = AppShapes

    val dimens: AppDimens get() = AppDimens
}
