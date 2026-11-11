package com.madtitan94.transactionsparser.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.madtitan94.transactionsparser.core.designsystem.R

/**
 * Suggested categories the account does not have yet.
 *
 * Filter chips so a tap selects without writing anything. Colours and the selected check mark
 * come from the chip defaults, which follow the theme. Callers pass labels only — this
 * composable does not know catalog keys. The button below adopts the selection, or every
 * suggestion when nothing is selected.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuggestedCategoryChips(
    labels: List<String>,
    selectedLabels: Set<String>,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (labels.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            labels.forEach { label ->
                val selected = label in selectedLabels
                FilterChip(
                    selected = selected,
                    onClick = { onToggle(label) },
                    label = { Text(label) },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Done,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        }
        val selectedCount = selectedLabels.size
        TextButton(onClick = onConfirm) {
            Text(
                text = if (selectedCount == 0) {
                    stringResource(R.string.suggested_categories_add_all)
                } else {
                    pluralStringResource(
                        R.plurals.suggested_categories_add_selected,
                        selectedCount,
                        selectedCount
                    )
                }
            )
        }
    }
}
