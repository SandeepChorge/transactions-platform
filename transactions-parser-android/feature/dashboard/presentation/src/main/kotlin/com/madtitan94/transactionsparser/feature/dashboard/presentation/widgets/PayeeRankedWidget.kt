package com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madtitan94.transactionsparser.core.designsystem.charts.AppChartDimens
import com.madtitan94.transactionsparser.core.designsystem.charts.ChartTrack
import com.madtitan94.transactionsparser.core.designsystem.charts.selectableChartRow
import com.madtitan94.transactionsparser.core.designsystem.charts.trackFraction
import com.madtitan94.transactionsparser.core.designsystem.theme.AppDimens
import com.madtitan94.transactionsparser.core.designsystem.theme.AppShapes
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTypography
import com.madtitan94.transactionsparser.core.domain.model.PayeeTotal
import com.madtitan94.transactionsparser.core.presentation.formatPaise
import com.madtitan94.transactionsparser.feature.dashboard.domain.PayeeScope
import com.madtitan94.transactionsparser.feature.dashboard.presentation.R

/**
 * Payees ranked by spend — the merged payee, never the statement spelling.
 *
 * The rows come from the aggregate already grouped, so three spellings of one merchant arrive as one
 * row under the user's alias. Names with no payee behind them keep their own row and are shown
 * exactly as the bank printed them, uppercase and unaltered: the user recognises a raw UPI string by
 * its exact form, and title-casing it would make it harder to place, not easier.
 *
 * [scope] switches the same card between "who you pay" and Mapping health's work queue. It is a
 * filter rather than a different widget because the row grammar is identical and the queue is just
 * the ranking with the named payees taken out.
 */
@Composable
fun PayeeRankedWidget(
    payees: List<PayeeTotal>,
    scope: PayeeScope,
    modifier: Modifier = Modifier,
    onPayeeClick: ((PayeeTotal) -> Unit)? = null
) {
    val rows = when (scope) {
        PayeeScope.ALL -> payees
        PayeeScope.UNMAPPED_ONLY -> payees.filter { it.isUnmapped }
    }

    DashboardCard(modifier = modifier) {
        CardHeader(
            title = stringResource(
                when (scope) {
                    PayeeScope.ALL -> R.string.dash_payees_title
                    PayeeScope.UNMAPPED_ONLY -> R.string.dash_waiting_title
                }
            )
        )

        if (rows.isEmpty()) {
            CardEmptyLine(
                stringResource(
                    when (scope) {
                        PayeeScope.ALL -> R.string.dash_payees_empty
                        // Not "nothing to show": on this dashboard an empty queue is the goal, and
                        // saying so is the one place a success state earns its space.
                        PayeeScope.UNMAPPED_ONLY -> R.string.dash_waiting_empty
                    }
                )
            )
            return@DashboardCard
        }

        val largest = rows.maxOf { it.totalPaise }

        Column(verticalArrangement = Arrangement.spacedBy(AppDimens.rowGap)) {
            rows.forEach { payee ->
                PayeeRow(
                    payee = payee,
                    fraction = trackFraction(payee.totalPaise, largest),
                    onClick = onPayeeClick?.let { click -> { click(payee) } }
                )
            }
        }
    }
}

@Composable
private fun PayeeRow(payee: PayeeTotal, fraction: Float, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableChartRow(selected = false, onClick = onClick, clickLabel = payee.label),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.swatchGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PayeeAvatar(payee.label, isUnmapped = payee.isUnmapped)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppDimens.rowLabelToBarGap)
        ) {
            // spacedBy, not SpaceBetween: statement names are long and uppercase, so without a gap
            // reserved ahead of the label an ellipsised name runs straight into the amount —
            // "SHAILAJA PATIL - SWAM…₹42,000".
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.labelToAmountGap),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = payee.label,
                    style = AppTypography.row,
                    color = AppTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatPaise(payee.totalPaise),
                    style = AppTypography.amount,
                    color = AppTheme.colors.textPrimary
                )
            }
            ChartTrack(
                fraction = fraction,
                height = AppChartDimens.trackInline,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = pluralStringResource(
                    R.plurals.dash_payee_count,
                    payee.transactionCount,
                    payee.transactionCount
                ),
                style = AppTypography.navLabel,
                color = AppTheme.colors.textMuted
            )
        }
    }
}

/**
 * Two initials on a rounded square — never a logo and never a fetched image.
 *
 * `DashboardSpec` §2 is explicit about this and the reason is worth keeping: the app parses
 * statements offline, and a payee avatar that reaches the network would make a spending dashboard
 * leak the list of merchants a user pays to whoever hosts the icons.
 */
@Composable
private fun PayeeAvatar(label: String, isUnmapped: Boolean) {
    val initials = label.split(' ', '*', '-', '/')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }

    Box(
        modifier = Modifier
            .size(AppDimens.avatarSize)
            .background(AppTheme.colors.surfaceAlt, AppShapes.chip),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = AppTypography.avatarInitials,
            color = if (isUnmapped) AppTheme.colors.textMuted else AppTheme.colors.textSecondary
        )
    }
}
