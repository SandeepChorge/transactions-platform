package com.madtitan94.transactionsparser.feature.settings.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madtitan94.transactionsparser.core.designsystem.components.AppAlertDialog
import com.madtitan94.transactionsparser.core.designsystem.components.ListRow
import com.madtitan94.transactionsparser.core.presentation.ObserveAsEvents
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsRoot(
    appVersion: String,
    onOpenProfile: () -> Unit,
    onOpenRecentlyDeleted: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // CreateDocument is the whole permission story for export: the picker the user interacts with
    // *is* the grant, so there is no runtime permission to request or to be denied.
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(CSV_MIME_TYPE)
    ) { uri ->
        viewModel.onAction(
            if (uri == null) {
                SettingsAction.OnExportCancelled
            } else {
                SettingsAction.OnExportDestinationChosen(uri.toString())
            }
        )
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SettingsEvent.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(event.message.asString(context))
            }
            is SettingsEvent.LaunchExportPicker -> exportPicker.launch(event.suggestedFileName)
        }
    }

    SettingsScreen(
        state = state,
        appVersion = appVersion,
        snackbarHostState = snackbarHostState,
        onOpenProfile = onOpenProfile,
        onOpenRecentlyDeleted = onOpenRecentlyDeleted,
        onAction = viewModel::onAction
    )
}

private const val CSV_MIME_TYPE = "text/csv"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: SettingsState,
    appVersion: String,
    snackbarHostState: SnackbarHostState,
    onOpenProfile: () -> Unit,
    onOpenRecentlyDeleted: () -> Unit,
    onAction: (SettingsAction) -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsRow(
                icon = Icons.Default.Person,
                title = stringResource(R.string.settings_profile),
                supporting = state.email.takeIf { it.isNotBlank() },
                onClick = onOpenProfile
            )
            HorizontalDivider()

            SettingsRow(
                icon = Icons.Default.FileDownload,
                title = stringResource(R.string.settings_export),
                supporting = stringResource(R.string.settings_export_supporting),
                onClick = { onAction(SettingsAction.OnExportClick) },
                trailing = if (state.isExporting) {
                    { CircularProgressIndicator(Modifier.size(20.dp)) }
                } else {
                    null
                }
            )
            HorizontalDivider()

            SettingsRow(
                icon = Icons.Default.RestoreFromTrash,
                title = stringResource(R.string.settings_recently_deleted),
                supporting = stringResource(R.string.settings_recently_deleted_supporting),
                onClick = onOpenRecentlyDeleted
            )
            HorizontalDivider()

            // No backend exists yet, so this is deliberately inert rather than hidden — knowing
            // it is coming is more useful than wondering whether the app syncs at all.
            SettingsRow(
                icon = Icons.Default.CloudSync,
                title = stringResource(R.string.settings_syncing),
                supporting = stringResource(R.string.settings_coming_soon),
                onClick = null
            )
            HorizontalDivider()

            SettingsRow(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = stringResource(R.string.settings_logout),
                onClick = { onAction(SettingsAction.OnLogoutClick) }
            )
            HorizontalDivider()

            Text(
                text = stringResource(R.string.settings_version, appVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            )
        }

        if (state.showLogoutConfirm) {
            AppAlertDialog(
                title = stringResource(R.string.settings_logout_confirm_title),
                message = stringResource(R.string.settings_logout_confirm_message),
                confirmLabel = stringResource(R.string.settings_logout),
                dismissLabel = stringResource(R.string.settings_cancel),
                onConfirm = { onAction(SettingsAction.OnConfirmLogout) },
                onDismiss = { onAction(SettingsAction.OnDismissLogoutConfirm) }
            )
        }
    }
}

/**
 * A settings entry. [onClick] is null for entries that exist but do nothing yet, which also
 * removes the ripple so the row doesn't look tappable.
 */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    onClick: (() -> Unit)?,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val enabled = onClick != null
    ListRow(
        title = title,
        supporting = supporting,
        dimmed = !enabled,
        leading = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.size(24.dp)
            )
        },
        trailing = trailing,
        modifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier
    )
}
