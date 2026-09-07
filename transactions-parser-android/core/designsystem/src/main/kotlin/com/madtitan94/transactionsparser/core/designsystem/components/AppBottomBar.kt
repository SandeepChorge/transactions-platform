package com.madtitan94.transactionsparser.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.madtitan94.transactionsparser.core.designsystem.theme.AppDimens
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme

/**
 * The app's bottom navigation bar, drawn to the Home artboards rather than to Material's
 * [androidx.compose.material3.NavigationBar].
 *
 * Material's bar is not used here for two reasons found the hard way in #26: it reads
 * `labelMedium` for navigation labels, which in this theme is the uppercase mono eyebrow, and it
 * paints its own indicator pill that the artboards do not have. Both were patched at call sites
 * before; this composable removes the need to.
 *
 * The artboards draw a flat five-column grid. The destination set they show
 * (Home · Upload · Sessions · Categories · Profile) is **not** the set built here — issue #16 and
 * `design/DashboardSpec.dc.html` both specify `Home · Statements · ⊕ Add · Payees · You`, and the
 * artboards predate the dashboard. What is transcribed from them is the metrics and the colours,
 * which is what they are still authoritative for.
 *
 * The bar consumes the navigation-bar inset itself, so callers must not add it again.
 */
@Composable
fun AppBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.screen)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppDimens.hairline)
                .background(colors.borderSubtle)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppDimens.bottomBarHorizontalPadding,
                    end = AppDimens.bottomBarHorizontalPadding,
                    top = AppDimens.bottomBarTopPadding,
                    bottom = AppDimens.bottomBarBottomPadding
                )
                .padding(WindowInsets.navigationBars.asPaddingValues()),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
            content = content
        )
    }
}

/**
 * One destination in [AppBottomBar].
 *
 * The visual column is smaller than the 48dp minimum touch target the design spec requires, so the
 * hit box is expanded around it rather than the icon being grown to meet it — the spec's own
 * instruction ("min touch target 48; visual can be smaller, expand the hit box").
 */
@Composable
fun RowScope.AppBottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    // The artboards give the inactive icon and its label two different greys — the icon sits a
    // step muted from the text. Selecting both from one token would flatten that.
    val iconTint = if (selected) colors.accentInk else colors.textMuted
    val labelColor = if (selected) colors.accentInk else colors.textSecondary
    val labelStyle = if (selected) AppTheme.typography.navLabelActive else AppTheme.typography.navLabel

    Column(
        modifier = modifier
            .weight(1f)
            .sizeIn(minHeight = MinTouchTarget)
            .clip(RoundedCornerShape(AppDimens.bottomBarItemBottomPadding))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                role = Role.Tab,
                onClick = onClick
            )
            .padding(bottom = AppDimens.bottomBarItemBottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            AppDimens.bottomBarIconToLabelGap,
            Alignment.Bottom
        )
    ) {
        Icon(
            imageVector = icon,
            // The label directly beneath says the same thing; announcing both makes every tab
            // read twice.
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(AppDimens.bottomBarIconSize)
        )
        Text(text = label, style = labelStyle, color = labelColor)
    }
}

/**
 * The raised centre action — Add / upload.
 *
 * **This is the one part of the bar no artboard draws.** `design/DashboardSpec.dc.html` names it
 * ("add · bottom nav centre action · Add / upload") but gives it no treatment, and the Home
 * artboards still show Upload as a flat tab. It is built from the design's existing amber roles so
 * it reads as the same affordance as [AppButton] rather than as a new one: `accent` fill with an
 * `onAccent` glyph, at the icon-button radius the spec does give (12).
 *
 * It carries no selected state. It is an action, not a destination — the upload flow is somewhere
 * you go and come back from, and lighting it up like a tab would imply otherwise.
 */
@Composable
fun RowScope.AppBottomBarCentreAction(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier
            .weight(1f)
            .sizeIn(minHeight = MinTouchTarget)
            .padding(bottom = AppDimens.bottomBarItemBottomPadding),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(CentreActionSize)
                .clip(RoundedCornerShape(CentreActionRadius))
                .background(colors.accent)
                .clickable(
                    role = Role.Button,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colors.onAccent,
                modifier = Modifier.size(CentreActionGlyphSize)
            )
        }
    }
}

/** `design/DashboardSpec.dc.html`: "min touch target 48 (visual can be smaller; expand the hit box)". */
private val MinTouchTarget = 48.dp

/** The spec's icon-button metrics, scaled up to carry the bar's primary action. */
private val CentreActionSize = 44.dp
private val CentreActionRadius = 12.dp
private val CentreActionGlyphSize = 24.dp
