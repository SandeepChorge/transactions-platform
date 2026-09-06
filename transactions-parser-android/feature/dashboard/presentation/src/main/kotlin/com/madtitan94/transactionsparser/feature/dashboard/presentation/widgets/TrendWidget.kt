package com.madtitan94.transactionsparser.feature.dashboard.presentation.widgets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.madtitan94.transactionsparser.core.designsystem.charts.SpendTrendChart
import com.madtitan94.transactionsparser.core.presentation.formatPaise
import com.madtitan94.transactionsparser.core.presentation.formatPaiseCompact
import com.madtitan94.transactionsparser.core.presentation.formatStatementDate
import com.madtitan94.transactionsparser.feature.dashboard.presentation.R

/**
 * Spend across the period, with the average in the header and a scrubber under the finger.
 *
 * `DashboardSpec` W2 says no scrubber "unless the label row can show the read-out". That is a
 * condition rather than a ban and it is met here: dragging replaces the average with the bucket the
 * finger is on, so the number the user is pointing at is always the number in the header. Releasing
 * puts the average back.
 */
@Composable
fun TrendWidget(
    series: TrendSeries,
    modifier: Modifier = Modifier
) {
    var scrubbed by remember(series) { mutableStateOf<Int?>(null) }

    DashboardCard(modifier = modifier) {
        val readOut = scrubbed?.let { index ->
            series.values.getOrNull(index)?.let { value ->
                stringResource(
                    R.string.dash_daily_on,
                    formatPaise(value),
                    formatStatementDate(series.startMillis[index])
                )
            }
        }

        CardHeader(
            title = stringResource(R.string.dash_daily_title),
            // The average gives way to whatever the finger is on, which is the condition W2 sets
            // for a scrubber being allowed at all: the read-out has somewhere to go.
            caption = readOut ?: stringResource(
                when (series.bucketSize) {
                    TrendBucketSize.DAY -> R.string.dash_daily_avg
                    else -> R.string.dash_daily_avg_week
                },
                formatPaiseCompact(series.averagePaise)
            )
        )

        SpendTrendChart(
            values = series.values,
            scrubbedIndex = scrubbed,
            onScrub = { scrubbed = it },
            contentDescription = stringResource(R.string.dash_daily_title),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
