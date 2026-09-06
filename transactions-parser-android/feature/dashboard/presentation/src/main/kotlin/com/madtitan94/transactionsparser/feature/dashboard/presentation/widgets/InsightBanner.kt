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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.madtitan94.transactionsparser.core.designsystem.theme.AppDimens
import com.madtitan94.transactionsparser.core.designsystem.theme.AppShapes
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTypography
import com.madtitan94.transactionsparser.core.presentation.formatPaise
import com.madtitan94.transactionsparser.feature.dashboard.presentation.R

/**
 * The unmapped-money nudge.
 *
 * It states an amount before it states a task, because the amount is what makes the task worth
 * doing: "three payees are unnamed" is housekeeping, "₹5,770 has no name on it" is money the user
 * cannot account for.
 *
 * There is deliberately no "you're all done" variant — the caller omits the banner entirely when
 * nothing is unmapped. A congratulation card would take permanent space on the dashboard to say
 * that nothing is wrong.
 */
@Composable
fun InsightBanner(
    unmappedPaise: Long,
    payeeCount: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.accentSurface, AppShapes.card)
            .border(AppDimens.hairline, AppTheme.colors.accentBorder, AppShapes.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(AppDimens.alertCardPadding)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .padding(top = 7.dp)
                    .size(AppDimens.alertDotSize)
                    .background(AppTheme.colors.accentInk, CircleShape)
            )
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = stringResource(R.string.dash_unmapped_title, formatPaise(unmappedPaise)),
                    style = AppTypography.sectionHeader,
                    color = AppTheme.colors.accentHeading
                )
                Text(
                    text = pluralStringResource(R.plurals.dash_unmapped_body, payeeCount, payeeCount),
                    style = AppTypography.body,
                    color = AppTheme.colors.accentBody
                )
            }
        }
    }
}
