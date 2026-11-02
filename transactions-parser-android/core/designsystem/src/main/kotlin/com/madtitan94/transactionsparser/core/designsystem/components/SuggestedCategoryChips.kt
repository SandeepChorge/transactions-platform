package com.madtitan94.transactionsparser.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.madtitan94.transactionsparser.core.designsystem.R

/**
 * Suggested categories the account does not have yet.
 *
 * Assist chips, the same control an alias suggestion uses, so a tap target the user has already
 * met on the mapping form is the one that adopts a category. Colours come from the chip defaults,
 * which follow the theme. Callers pass labels only — this composable does not know catalog keys.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuggestedCategoryChips(
    labels: List<String>,
    onAdopt: (String) -> Unit,
    onAdoptAll: () -> Unit,
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
                AssistChip(
                    onClick = { onAdopt(label) },
                    label = { Text(label) }
                )
            }
        }
        TextButton(onClick = onAdoptAll) {
            Text(stringResource(R.string.suggested_categories_add_all))
        }
    }
}
