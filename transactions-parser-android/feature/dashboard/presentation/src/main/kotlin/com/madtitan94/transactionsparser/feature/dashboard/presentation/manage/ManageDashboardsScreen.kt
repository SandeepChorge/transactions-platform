package com.madtitan94.transactionsparser.feature.dashboard.presentation.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madtitan94.transactionsparser.core.designsystem.components.AppAlertDialog
import com.madtitan94.transactionsparser.core.designsystem.components.AppButton
import com.madtitan94.transactionsparser.core.designsystem.components.LoadingIndicator
import com.madtitan94.transactionsparser.core.designsystem.components.ReorderableColumn
import com.madtitan94.transactionsparser.core.designsystem.theme.AppDimens
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTypography
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardKey
import com.madtitan94.transactionsparser.feature.dashboard.presentation.R
import com.madtitan94.transactionsparser.feature.dashboard.presentation.dashboardName
import org.koin.androidx.compose.koinViewModel

@Composable
fun ManageDashboardsRoot(
    onBack: () -> Unit,
    onBuildDashboard: () -> Unit,
    onEditDashboard: (String) -> Unit,
    viewModel: DashboardLayoutViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ManageDashboardsScreen(
        state = state,
        onBack = onBack,
        onBuildDashboard = onBuildDashboard,
        onEditDashboard = onEditDashboard,
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageDashboardsScreen(
    state: DashboardLayoutState,
    onBack: () -> Unit,
    onBuildDashboard: () -> Unit,
    onEditDashboard: (String) -> Unit,
    onAction: (DashboardLayoutAction) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dash_manage_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.dash_back)
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
            Text(
                text = stringResource(R.string.dash_manage_note),
                style = AppTypography.body,
                color = AppTheme.colors.textSecondary
            )

            // A plain Column, not a LazyColumn: the reorderable list measures every row so a drag
            // can step across rows of different heights, and it is inside a scrolling parent.
            ReorderableColumn(
                items = state.dashboards,
                onMove = { from, to -> onAction(DashboardLayoutAction.OnMoved(from, to)) },
                moveUpLabel = stringResource(R.string.dash_move_up),
                moveDownLabel = stringResource(R.string.dash_move_down),
                modifier = Modifier.fillMaxWidth()
            ) { entry, dragHandle ->
                DashboardManageRow(
                    entry = entry,
                    dragHandle = dragHandle,
                    onEnabledChanged = { enabled ->
                        onAction(DashboardLayoutAction.OnEnabledChanged(entry.key, enabled))
                    },
                    onEdit = { (entry.key as? DashboardKey.Custom)?.let { onEditDashboard(it.id) } },
                    onDelete = { onAction(DashboardLayoutAction.OnDeleteClick(entry)) }
                )
            }

            Text(
                text = pluralStringResource(
                    R.plurals.dash_enabled_count,
                    state.enabledCount,
                    state.enabledCount,
                    state.dashboards.size
                ),
                style = AppTypography.body,
                color = AppTheme.colors.textMuted
            )

            AppButton(
                text = stringResource(R.string.dash_build_action),
                onClick = onBuildDashboard,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
            )
        }

        state.pendingDelete?.let { pending ->
            AppAlertDialog(
                title = stringResource(R.string.dash_delete_title),
                message = stringResource(
                    R.string.dash_delete_message,
                    dashboardName(pending.definition)
                ),
                confirmLabel = stringResource(R.string.dash_delete_action),
                dismissLabel = stringResource(R.string.dash_cancel),
                onConfirm = { onAction(DashboardLayoutAction.OnDeleteConfirmed) },
                onDismiss = { onAction(DashboardLayoutAction.OnDeleteDismissed) }
            )
        }
    }
}

/**
 * One dashboard: grip, name, switch — and for a dashboard the user built, edit and delete.
 *
 * The built-in four carry no delete because they cannot be recreated from this screen; switching one
 * off is the reversible version of the same intent. Custom dashboards get both, since the builder
 * that made them is one tap away.
 */
@Composable
private fun DashboardManageRow(
    entry: DashboardEntry,
    dragHandle: Modifier,
    onEnabledChanged: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = stringResource(R.string.dash_reorder),
            tint = AppTheme.colors.textMuted,
            modifier = dragHandle.size(24.dp)
        )

        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(
                text = dashboardName(entry.definition),
                style = AppTypography.row,
                color = AppTheme.colors.textPrimary
            )
            Text(
                text = pluralStringResource(
                    R.plurals.dash_card_count,
                    entry.definition.widgets.size,
                    entry.definition.widgets.size
                ),
                style = AppTypography.navLabel,
                color = AppTheme.colors.textMuted
            )
        }

        if (entry.isCustom) {
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.dash_edit),
                    tint = AppTheme.colors.textSecondary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.dash_delete_action),
                    tint = AppTheme.colors.textSecondary
                )
            }
        }

        Switch(checked = entry.isEnabled, onCheckedChange = onEnabledChanged)
    }
}
