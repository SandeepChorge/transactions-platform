package com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madtitan94.transactionsparser.core.designsystem.theme.AppDimens
import com.madtitan94.transactionsparser.core.designsystem.theme.AppShapes
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTypography

/**
 * The surface every widget sits on: a 20dp rounded card with a hairline border.
 *
 * Widgets do not draw their own card. Keeping the surface here is what lets the renderer stack any
 * ordered list of widgets and get a screen that looks composed rather than assembled — including a
 * list the user builds themselves in a later phase, which nobody will have reviewed for consistency.
 */
@Composable
fun DashboardCard(
    modifier: Modifier = Modifier,
    padding: androidx.compose.ui.unit.Dp = AppDimens.cardPadding,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surface, AppShapes.card)
            .border(AppDimens.hairline, AppTheme.colors.border, AppShapes.card)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

/** The mono, letter-spaced, upper-case label above a hero number. */
@Composable
fun CardEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AppTypography.eyebrow,
        color = AppTheme.colors.accentInk,
        modifier = modifier
    )
}

/**
 * A card's title row, with an optional trailing action.
 *
 * The action is a text label rather than an icon because it always names a destination — "See all",
 * "All payees" — and an arrow glyph would make the user guess which list it opens.
 */
@Composable
fun CardHeader(
    title: String,
    modifier: Modifier = Modifier,
    /** A read-out, not a control: muted mono, never tappable. */
    caption: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = AppTypography.sectionHeader,
            color = AppTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (caption != null) {
            Text(
                text = caption,
                style = AppTypography.amount,
                color = AppTheme.colors.textSecondary,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        if (actionLabel != null && onActionClick != null) {
            Text(
                text = actionLabel,
                style = AppTypography.row,
                color = AppTheme.colors.accentInk,
                modifier = Modifier
                    .clickable(onClick = onActionClick)
                    .padding(start = 12.dp)
            )
        }
    }
}

/** A muted single line, for the state a widget shows when the period holds nothing it can draw. */
@Composable
fun CardEmptyLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AppTypography.body,
        color = AppTheme.colors.textMuted,
        modifier = modifier.fillMaxWidth()
    )
}
