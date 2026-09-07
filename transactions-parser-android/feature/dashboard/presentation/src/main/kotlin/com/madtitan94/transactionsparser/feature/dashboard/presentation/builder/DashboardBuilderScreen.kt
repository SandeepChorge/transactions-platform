package com.madtitan94.transactionsparser.feature.dashboard.presentation.builder

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madtitan94.transactionsparser.core.designsystem.components.AppButton
import com.madtitan94.transactionsparser.core.designsystem.components.LoadingIndicator
import com.madtitan94.transactionsparser.core.designsystem.components.ReorderableColumn
import com.madtitan94.transactionsparser.core.designsystem.theme.AppDimens
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTypography
import com.madtitan94.transactionsparser.core.presentation.ObserveAsEvents
import com.madtitan94.transactionsparser.feature.dashboard.presentation.R
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgetDescriptionRes
import com.madtitan94.transactionsparser.feature.dashboard.presentation.widgetNameRes
import org.koin.androidx.compose.koinViewModel

@Composable
fun DashboardBuilderRoot(
    onClose: () -> Unit,
    viewModel: DashboardBuilderViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Closed on the event rather than in the Save handler: the dashboard is written first, so the
    // manage screen behind this one is never shown without the dashboard that was just saved.
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            DashboardBuilderEvent.Saved -> onClose()
        }
    }

    DashboardBuilderScreen(
        state = state,
        onClose = onClose,
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardBuilderScreen(
    state: DashboardBuilderState,
    onClose: () -> Unit,
    onAction: (DashboardBuilderAction) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.dash_builder_edit_title
                            else R.string.dash_builder_title
                        )
                    )
                },
                navigationIcon = {
                    // A close rather than a back arrow: nothing here is written until Save, so
                    // leaving discards, and the arrow would promise otherwise.
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.dash_cancel)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            LoadingIndicator(Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.screenHorizontalPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { onAction(DashboardBuilderAction.OnNameChanged(it)) },
                label = { Text(stringResource(R.string.dash_builder_name)) },
                placeholder = { Text(stringResource(R.string.dash_builder_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            PreviewStrip(state = state)

            Text(
                text = pluralStringResource(
                    R.plurals.dash_builder_count,
                    state.selected.size,
                    state.selected.size
                ),
                style = AppTypography.body,
                color = AppTheme.colors.textSecondary
            )

            ReorderableColumn(
                items = state.rows,
                onMove = { from, to -> onAction(DashboardBuilderAction.OnMoved(from, to)) },
                moveUpLabel = stringResource(R.string.dash_move_up),
                moveDownLabel = stringResource(R.string.dash_move_down),
                modifier = Modifier.fillMaxWidth()
            ) { row, dragHandle ->
                BuilderWidgetRow(
                    row = row,
                    dragHandle = dragHandle,
                    onToggled = { selected ->
                        onAction(DashboardBuilderAction.OnWidgetToggled(row.widget, selected))
                    }
                )
            }

            AppButton(
                text = stringResource(R.string.dash_builder_save),
                onClick = { onAction(DashboardBuilderAction.OnSave) },
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * The dashboard as it would be read, top to bottom — laid out left to right so it fits one strip.
 *
 * Horizontal because a vertical preview of a scrolling screen would either be too small to read or
 * take the space the checklist needs. The order is the save order, so dragging a row visibly moves
 * its tile, which is the connection the strip exists to make.
 */
/** Matches the silhouette tile, so the strip is the same height full or empty. */
private val PREVIEW_STRIP_HEIGHT = 62.dp

@Composable
private fun PreviewStrip(state: DashboardBuilderState) {
    if (state.selected.isEmpty()) {
        // The strip keeps its height while it is empty so ticking the first card does not shove the
        // checklist down the screen — but the sentence is centred in that space rather than pinned
        // to the top of it, which read as a rendering fault.
        Box(
            modifier = Modifier.fillMaxWidth().height(PREVIEW_STRIP_HEIGHT),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = stringResource(R.string.dash_builder_empty),
                style = AppTypography.body,
                color = AppTheme.colors.textMuted
            )
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.selected.forEach { widget -> WidgetSilhouette(widget) }
    }
}

@Composable
private fun BuilderWidgetRow(
    row: BuilderRow,
    dragHandle: Modifier,
    onToggled: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = stringResource(R.string.dash_reorder),
            tint = AppTheme.colors.textMuted,
            modifier = dragHandle.size(24.dp)
        )
        Checkbox(checked = row.isSelected, onCheckedChange = onToggled)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(widgetNameRes(row.widget)),
                style = AppTypography.row,
                color = if (row.isSelected) {
                    AppTheme.colors.textPrimary
                } else {
                    AppTheme.colors.textSecondary
                }
            )
            Text(
                text = stringResource(widgetDescriptionRes(row.widget)),
                style = AppTypography.navLabel,
                color = AppTheme.colors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
