package com.madtitan94.transactionsparser.feature.dashboard.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madtitan94.transactionsparser.core.designsystem.theme.AppShapes
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTypography
import com.madtitan94.transactionsparser.core.presentation.formatStatementDate
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardRange
import java.time.ZoneOffset

/**
 * The short form of the range, for the one place too small to hold its full label: the caption in
 * the donut's 75dp hole.
 *
 * Only a custom range differs. The five fixed periods already name themselves in two words, while a
 * custom one expands to "01 Jun 2026 – 30 Jun 2026" — three times the width of the hole, which it
 * used to lay straight across the ring and out over the legend. Shortening it loses nothing: the
 * range chip at the top of the same screen carries the dates in full.
 */
@Composable
fun rangeCaption(range: DashboardRange): String = when (range) {
    is DashboardRange.Custom -> stringResource(R.string.range_custom)
    else -> rangeLabel(range)
}

/** The chip's own label, and the one the hero card's eyebrow borrows. */
@Composable
fun rangeLabel(range: DashboardRange): String = when (range) {
    DashboardRange.Today -> stringResource(R.string.range_today)
    DashboardRange.ThisWeek -> stringResource(R.string.range_this_week)
    DashboardRange.ThisMonth -> stringResource(R.string.range_this_month)
    DashboardRange.LastMonth -> stringResource(R.string.range_last_month)
    DashboardRange.AllTime -> stringResource(R.string.range_all_time)
    is DashboardRange.Custom -> stringResource(
        R.string.range_custom_span,
        formatStatementDate(range.fromDate.toEpochDay() * 86_400_000L),
        formatStatementDate(range.toDateInclusive.toEpochDay() * 86_400_000L)
    )
}

/** The header control that opens the range sheet. */
@Composable
fun RangeChip(range: DashboardRange, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(AppTheme.colors.surfaceAlt, AppShapes.button)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = rangeLabel(range),
            style = AppTypography.row,
            color = AppTheme.colors.textPrimary,
            // Two lines is what a custom range's pair of dates needs once the header caps this
            // chip's width; a third would push the chip taller than the title beside it.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = stringResource(R.string.dash_open_range),
            tint = AppTheme.colors.textSecondary
        )
    }
}

/**
 * The range sheet: the five fixed periods, then a way into the date picker.
 *
 * Custom sits at the bottom and opens a picker rather than appearing as a sixth chip, because it is
 * the only option that cannot state what it means until the user has answered a question.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangeSheet(
    current: DashboardRange,
    onSelect: (DashboardRange) -> Unit,
    onPickCustom: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = AppTheme.colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.range_sheet_title),
                style = AppTypography.sectionHeader,
                color = AppTheme.colors.textPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            DashboardRange.Selectable.forEach { option ->
                RangeSheetRow(
                    label = rangeLabel(option),
                    selected = option.key == current.key,
                    onClick = { onSelect(option) }
                )
            }

            RangeSheetRow(
                label = stringResource(R.string.range_custom_pick),
                selected = current is DashboardRange.Custom,
                onClick = onPickCustom
            )
        }
    }
}

@Composable
private fun RangeSheetRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = AppTypography.row,
            color = if (selected) AppTheme.colors.accentInk else AppTheme.colors.textPrimary
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = AppTheme.colors.accentInk
            )
        }
    }
}

/**
 * The custom-range picker.
 *
 * Material hands back UTC midnight for each date, which is already the clock statement rows are
 * stored in — so the millis pass through untouched. Converting them "to local time" here is the
 * exact mistake the domain rule warns about and would move the boundary by hours.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRangePickerDialog(
    initial: DashboardRange,
    onPicked: (fromMillis: Long, toMillisInclusive: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val start = (initial as? DashboardRange.Custom)?.fromDate
        ?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli()
    val end = (initial as? DashboardRange.Custom)?.toDateInclusive
        ?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli()

    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = start,
        initialSelectedEndDateMillis = end
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val from = state.selectedStartDateMillis
                    // A single tapped date is a one-day range, not an incomplete answer — the
                    // picker allows it and the user plainly meant that day.
                    val to = state.selectedEndDateMillis ?: from
                    if (from != null && to != null) onPicked(from, to)
                },
                enabled = state.selectedStartDateMillis != null
            ) {
                Text(stringResource(R.string.range_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.range_picker_cancel))
            }
        }
    ) {
        DateRangePicker(state = state, modifier = Modifier.heightIn(max = 520.dp))
    }
}
