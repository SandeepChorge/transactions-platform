package com.madtitan94.transactionsparser.core.designsystem.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.madtitan94.transactionsparser.core.designsystem.theme.AppShapes
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTypography
import com.madtitan94.transactionsparser.core.designsystem.theme.TransactionsParserTheme

/**
 * Every primitive on one card, in both themes.
 *
 * This is the only way to look at these before a dashboard exists to put them on, and it is worth
 * keeping afterwards: a palette or radius change shows up here in one glance rather than being
 * discovered on whichever screen happened to use it.
 *
 * The amounts are invented. Nothing real belongs in a file that ships.
 */
private val SampleSlices = buildCategorySlices(
    amounts = listOf(
        CategoryAmount(1L, "Groceries", 10_960_00),
        CategoryAmount(2L, "Rent & bills", 8_010_00),
        CategoryAmount(3L, "Transport", 6_325_00),
        CategoryAmount(4L, "Eating out", 5_060_00),
        CategoryAmount(5L, "Shopping", 2_400_00),
        CategoryAmount(null, "unnamed", 1_690_00)
    ),
    uncategorisedLabel = "Uncategorised"
)

@Composable
private fun ChartGallery() {
    val colors = AppTheme.colors
    val largest = SampleSlices.maxOfOrNull { it.amountPaise } ?: 0L

    Column(
        Modifier
            .background(colors.screen)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(AppShapes.card)
                .background(colors.surface)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Where it went", style = AppTypography.sectionHeader, color = colors.textPrimary)
            StackedShareBar(SampleSlices)

            SampleSlices.forEach { slice ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RankedBarCell(
                        slot = slice.slot,
                        fraction = trackFraction(slice.amountPaise, largest)
                    )
                    Text(
                        text = "  ${slice.label}",
                        style = AppTypography.row,
                        color = if (slice.isUnnamed) colors.textSecondary else colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${slice.sharePercent}%",
                        style = AppTypography.amount,
                        color = colors.textSecondary
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(AppShapes.card)
                .background(colors.surface)
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DonutChart(SampleSlices) {
                Text("₹34.4k", style = AppTypography.title, color = colors.textPrimary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Empty period", style = AppTypography.body, color = colors.textSecondary)
                DonutChart(emptyList(), box = 80.dp, radius = 26.dp, strokeWidth = 10.dp)
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(AppShapes.card)
                .background(colors.surface)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Daily spend", style = AppTypography.sectionHeader, color = colors.textPrimary)
            SpendTrendChart(listOf(320L, 180L, 640L, 90L, 1_200L, 410L, 760L, 240L, 980L))

            Text("In vs. out, by week", style = AppTypography.sectionHeader, color = colors.textPrimary)
            PairedBarChart(
                inValues = listOf(0L, 42_000L, 0L, 3_000L),
                outValues = listOf(12_400L, 8_900L, 15_600L, 6_100L),
                labels = listOf("W1", "W2", "W3", "W4")
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(AppChartDimens.statTileGap)) {
            StatTile("IN", "+₹45.0k", Modifier.weight(1f), colors.success)
            StatTile("OUT", "₹43.0k", Modifier.weight(1f))
            StatTile("NET", "+₹2.0k", Modifier.weight(1f), colors.success)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(AppShapes.card)
                .background(colors.surface)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Tracks", style = AppTypography.sectionHeader, color = colors.textPrimary)
            ChartTrack(0.82f, Modifier.fillMaxWidth(), height = AppChartDimens.trackMapping)
            ChartTrack(0.4f, Modifier.width(AppChartDimens.categoryTrackWidth))
            ChartTrack(0f, Modifier.fillMaxWidth(), height = AppChartDimens.trackInline)
        }
    }
}

@Preview(name = "Charts · light", showBackground = true, heightDp = 1200)
@Composable
private fun ChartGalleryLightPreview() {
    TransactionsParserTheme(darkTheme = false) { ChartGallery() }
}

@Preview(name = "Charts · dark", showBackground = true, heightDp = 1200)
@Composable
private fun ChartGalleryDarkPreview() {
    TransactionsParserTheme(darkTheme = true) { ChartGallery() }
}
