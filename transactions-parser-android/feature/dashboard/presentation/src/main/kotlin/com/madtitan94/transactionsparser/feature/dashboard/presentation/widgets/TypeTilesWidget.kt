package com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.madtitan94.transactionsparser.core.designsystem.charts.AppChartDimens
import com.madtitan94.transactionsparser.core.designsystem.charts.StatTile
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme
import com.madtitan94.transactionsparser.core.domain.model.TypeTotals
import com.madtitan94.transactionsparser.core.presentation.formatPaiseCompact
import com.madtitan94.transactionsparser.feature.dashboard.presentation.R

/**
 * In, out and net for the period, as three tiles.
 *
 * Abbreviated figures ("₹38.4k"), because three of them share one screen width and the alternative
 * is either a wrapped number or a font size nobody can read. The exact figures are one tap away in
 * the transaction list, and the hero card above already shows the unabbreviated total.
 *
 * The row keeps its shape on an empty period rather than disappearing: three zeroes are a true
 * answer to "what came in and what went out", and a row that vanishes reads as a failure to load.
 *
 * Net takes its colour from its sign — money left over is the good outcome, spending more than came
 * in is the bad one. In and out are labelled, not coloured by sign, so an ordinary month of spending
 * does not paint half the card red.
 */
@Composable
fun TypeTilesWidget(
    totals: TypeTotals,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppChartDimens.statTileGap)
    ) {
        StatTile(
            label = stringResource(R.string.dash_in),
            value = formatPaiseCompact(totals.creditPaise),
            valueColor = AppTheme.colors.moneyIn,
            modifier = Modifier.weight(1f)
        )
        StatTile(
            label = stringResource(R.string.dash_out),
            value = formatPaiseCompact(totals.debitPaise),
            valueColor = AppTheme.colors.moneyOut,
            modifier = Modifier.weight(1f)
        )
        StatTile(
            label = stringResource(R.string.dash_net),
            value = formatPaiseCompact(totals.netPaise),
            valueColor = if (totals.netPaise < 0L) AppTheme.colors.danger else AppTheme.colors.success,
            modifier = Modifier.weight(1f)
        )
    }
}
