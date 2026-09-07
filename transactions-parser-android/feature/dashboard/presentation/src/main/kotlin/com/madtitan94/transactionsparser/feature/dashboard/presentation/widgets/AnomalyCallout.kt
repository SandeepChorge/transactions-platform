package com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.madtitan94.transactionsparser.core.designsystem.theme.AppDimens
import com.madtitan94.transactionsparser.core.designsystem.theme.AppShapes
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTypography
import com.madtitan94.transactionsparser.core.domain.model.SpendAnomaly
import com.madtitan94.transactionsparser.core.presentation.formatPaise
import com.madtitan94.transactionsparser.feature.dashboard.presentation.R
import java.util.Locale

/** One callout's text, already formatted. */
data class AnomalyCalloutUi(
    val transactionId: Long,
    val title: String,
    val body: String,
    /** Where tapping the callout goes — the payee whose charge it is. */
    val normalizedName: String,
    val statementName: String
)

/**
 * The claim, in two lines: what was spent and why that is worth mentioning.
 *
 * The baseline is quoted rather than kept in reserve. "3.4× your usual Groceries charge" is an
 * assertion the user cannot check; "3.4× your usual Groceries charge of ₹1,240" is one they can
 * agree or disagree with from memory, which is the difference between a callout that earns trust
 * and one that gets dismissed on sight.
 *
 * The multiple is printed to one decimal place. Rounding "3.04×" up to "3×" would overstate a
 * borderline case, and a second decimal is precision the underlying average has not earned.
 */
@Composable
fun SpendAnomaly.toCalloutUi(): AnomalyCalloutUi = AnomalyCalloutUi(
    transactionId = transactionId,
    title = stringResource(R.string.dash_anomaly_title, formatPaise(amountPaise), label),
    body = stringResource(
        R.string.dash_anomaly_body,
        String.format(Locale.US, "%.1f", multiple),
        categoryName ?: stringResource(R.string.dash_uncategorised),
        formatPaise(baselineMeanPaise)
    ),
    normalizedName = normalizedName,
    statementName = statementName
)

/**
 * An unusually large charge, said in one sentence with a way to disagree with it.
 *
 * Drawn on the danger surface rather than the accent one the unmapped nudge uses. The two banners
 * can appear together and are asking for different things — one is housekeeping the user can do
 * whenever, the other is money that has already left — so they must not read as the same card in
 * two colours.
 *
 * The dismiss control is not optional decoration. This is the only surface in the app that makes a
 * claim rather than reporting a figure, and a claim the user cannot wave off is one they learn to
 * scroll past, which costs the feature its credibility on every future month too.
 */
@Composable
fun AnomalyCallout(
    callout: AnomalyCalloutUi,
    onDismiss: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.dangerSurface, AppShapes.card)
            .border(AppDimens.hairline, AppTheme.colors.dangerBorder, AppShapes.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(AppDimens.alertCardPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(AppDimens.alertDotSize)
                .background(AppTheme.colors.danger, CircleShape)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = callout.title,
                style = AppTypography.sectionHeader,
                color = AppTheme.colors.textPrimary
            )
            Text(
                text = callout.body,
                style = AppTypography.body,
                color = AppTheme.colors.textSecondary
            )
        }
        IconButton(
            onClick = { onDismiss(callout.transactionId) },
            modifier = Modifier.align(Alignment.Top)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.dash_anomaly_dismiss),
                tint = AppTheme.colors.textSecondary
            )
        }
    }
}
