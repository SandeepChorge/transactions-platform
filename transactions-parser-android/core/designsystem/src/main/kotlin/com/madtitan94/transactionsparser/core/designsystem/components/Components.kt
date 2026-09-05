package com.madtitan94.transactionsparser.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.madtitan94.transactionsparser.core.designsystem.theme.AppDimens
import com.madtitan94.transactionsparser.core.designsystem.theme.AppShapes
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTypography

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

/**
 * The design's primary button.
 *
 * It exists because Material's `Button` paints itself with `colorScheme.primary`, and `primary` is
 * mapped to `accentInk` — the darker amber — so that amber-as-*text* stays readable on the light
 * paper. A button is the one place `design/README.md` is explicit that the amber is used as-is in
 * both themes and must not be re-stepped, so the two rules pull in opposite directions and a
 * Material default silently lands on the wrong one. Every filled button in the app goes through
 * here rather than inheriting a colour that is right for ink and wrong for a fill.
 *
 * Height, radius and label style come from the artboards, not from Material's defaults.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(AppDimens.buttonHeight),
        enabled = enabled,
        shape = AppShapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppTheme.colors.accent,
            contentColor = AppTheme.colors.onAccent
        )
    ) {
        leadingIcon?.let {
            it()
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, style = AppTypography.button)
    }
}

/**
 * A determinate progress track.
 *
 * The colour is `accentGraphic`, the design's own answer to "amber, but as a bar" — a 6dp sliver of
 * the plain accent is too light to read against the paper, which is why the light theme carries a
 * darker relative for exactly this use. In dark it *is* the accent, so nothing moves there.
 *
 * Material's default would draw `primary`, which is `accentInk` — the ink step, not the graphic
 * one — plus a stop indicator and a track gap that the design does not have.
 */
@Composable
fun AppProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier
            .height(AppDimens.barHeight)
            .clip(AppShapes.bar),
        color = AppTheme.colors.accentGraphic,
        trackColor = AppTheme.colors.surfaceSunken,
        strokeCap = StrokeCap.Butt,
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AppAlertDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = dismissLabel?.let {
            { TextButton(onClick = onDismiss) { Text(it) } }
        }
    )
}

/**
 * Header above a run of list rows — a day or a month, with its subtotal on the right.
 *
 * Opaque by design: it is used as a sticky header, and a transparent one would let rows scroll
 * visibly underneath it.
 */
@Composable
fun SectionHeader(
    title: String,
    trailing: String? = null,
    emphasized: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = if (emphasized) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.labelLarge
            },
            color = if (emphasized) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f)
        )
        trailing?.let {
            Text(
                text = it,
                style = if (emphasized) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.labelLarge
                },
                color = if (emphasized) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * One row of a list: a title, optional supporting line, and an optional trailing value.
 *
 * [value] is null for rows that have nothing to show on the right — a settings entry or a
 * recovery list item — so those don't have to pass an empty string to mean "none".
 *
 * [dimmed] is for rows that are present but don't count toward anything — they stay readable
 * rather than being hidden, because hiding them would leave no way to change that decision.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    supporting: String? = null,
    dimmed: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val contentColor = if (dimmed) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.let {
            it()
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = contentColor)
            supporting?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            badge?.invoke()
        }
        value?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                textDecoration = if (dimmed) TextDecoration.LineThrough else null
            )
        }
        trailing?.let {
            Spacer(Modifier.width(4.dp))
            it()
        }
    }
}
