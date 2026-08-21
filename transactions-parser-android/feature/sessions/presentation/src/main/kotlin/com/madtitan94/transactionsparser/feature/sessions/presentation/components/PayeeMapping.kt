package com.madtitan94.transactionsparser.feature.sessions.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.madtitan94.transactionsparser.feature.sessions.presentation.R

/**
 * An existing payee offered while an alias is being typed. Carries the category so picking one
 * can adopt it — the point of picking is to become that payee, category included.
 */
data class AliasSuggestionUi(val payeeId: Long, val alias: String, val categoryId: Long)

/**
 * An alias that is already taken, and the payee holding it.
 *
 * The target id travels with the prompt so answering "link" needs no second lookup, and so the
 * answer cannot land on a different payee if the list changes while the dialog is open.
 */
data class MergePrompt(val alias: String, val targetPayeeId: Long)

/**
 * Existing payees matching what has been typed so far. Renders nothing when there is no match,
 * so the mapping form stays the same height until suggestions actually have something to say.
 *
 * Both mapping surfaces use this — the per-payee form on Session Detail and the one on Payee
 * Detail — so a suggestion looks and behaves the same wherever an alias is entered.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AliasSuggestions(
    suggestions: List<AliasSuggestionUi>,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty()) return

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        suggestions.forEach { suggestion ->
            AssistChip(
                onClick = { onPick(suggestion.payeeId) },
                label = { Text(suggestion.alias) }
            )
        }
    }
}

/**
 * Asks what an alias collision means: the same payee under a second statement name, or a
 * genuinely different one that happens to share a name.
 *
 * Both answers are offered as buttons rather than one being the dismissal, because neither is
 * safe to assume — merging is hard to undo, and silently creating a twin is the bug this whole
 * phase exists to prevent.
 */
@Composable
fun MergePromptDialog(
    prompt: MergePrompt,
    onConfirmMerge: () -> Unit,
    onKeepSeparate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.payee_merge_title, prompt.alias)) },
        text = { Text(stringResource(R.string.payee_merge_message, prompt.alias)) },
        confirmButton = {
            TextButton(onClick = onConfirmMerge) {
                Text(stringResource(R.string.payee_merge_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepSeparate) {
                Text(stringResource(R.string.payee_merge_keep_separate))
            }
        }
    )
}
