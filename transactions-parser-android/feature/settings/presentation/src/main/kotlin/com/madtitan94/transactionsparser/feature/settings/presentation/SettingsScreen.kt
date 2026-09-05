package com.madtitan94.transactionsparser.feature.settings.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madtitan94.transactionsparser.core.designsystem.components.AppAlertDialog
import com.madtitan94.transactionsparser.core.designsystem.components.ListRow
import com.madtitan94.transactionsparser.core.domain.backup.RestoreReport
import com.madtitan94.transactionsparser.core.presentation.ObserveAsEvents
import com.madtitan94.transactionsparser.core.presentation.formatInstantDate
import com.madtitan94.transactionsparser.feature.settings.domain.ThemePreference
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsRoot(
    appVersion: String,
    appVersionCode: Int,
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

    // A second launcher rather than a shared one: CreateDocument takes its MIME type at
    // construction, and offering a backup under text/csv would have the picker suggest the wrong
    // extension and file apps open it in a spreadsheet.
    val backupPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(JSON_MIME_TYPE)
    ) { uri ->
        viewModel.onAction(
            if (uri == null) {
                SettingsAction.OnBackupCancelled
            } else {
                SettingsAction.OnBackupDestinationChosen(uri.toString())
            }
        )
    }

    // OpenDocument rather than GetContent: it returns a document the app can re-read and does not
    // copy the file, and the picker is again the whole permission story.
    val restorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        // Backing out of the picker is not an error and needs no message; nothing has started yet.
        if (uri != null) viewModel.onAction(SettingsAction.OnRestoreSourceChosen(uri.toString()))
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SettingsEvent.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(event.message.asString(context))
            }
            is SettingsEvent.LaunchExportPicker -> exportPicker.launch(event.suggestedFileName)
            is SettingsEvent.LaunchBackupPicker -> backupPicker.launch(event.suggestedFileName)
            // Not restricted to application/json: a backup that arrived by email or cloud drive is
            // routinely offered as application/octet-stream, and a filter that hides the user's own
            // file is worse than validating whatever they pick.
            SettingsEvent.LaunchRestorePicker -> restorePicker.launch(arrayOf("*/*"))
        }
    }

    SettingsScreen(
        state = state,
        appVersion = appVersion,
        appVersionCode = appVersionCode,
        snackbarHostState = snackbarHostState,
        onOpenProfile = onOpenProfile,
        onOpenRecentlyDeleted = onOpenRecentlyDeleted,
        onAction = viewModel::onAction
    )
}

private const val CSV_MIME_TYPE = "text/csv"
private const val JSON_MIME_TYPE = "application/json"

/** Enough to see the shape of the problem; the rest is a number, not a list worth scrolling. */
private const val MAX_LISTED_CONFLICTS = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: SettingsState,
    appVersion: String,
    appVersionCode: Int,
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

            // The design ships two themes rather than one, so there has to be somewhere to choose
            // between them. Defaulting to the system setting is what makes both halves reachable
            // without anyone having to find this row first.
            SettingsRow(
                icon = Icons.Default.DarkMode,
                title = stringResource(R.string.settings_theme),
                supporting = stringResource(state.theme.labelRes),
                onClick = { onAction(SettingsAction.OnThemeClick) }
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

            // Separate from Export on purpose. Export produces a report to read; this produces a
            // file that can restore the app. Folding them into one row would leave the user
            // guessing which of the two they just got.
            SettingsRow(
                icon = Icons.Default.Backup,
                title = stringResource(R.string.settings_backup),
                supporting = stringResource(R.string.settings_backup_supporting),
                onClick = { onAction(SettingsAction.OnBackupClick) },
                trailing = if (state.isBackingUp) {
                    { CircularProgressIndicator(Modifier.size(20.dp)) }
                } else {
                    null
                }
            )
            HorizontalDivider()

            SettingsRow(
                icon = Icons.Default.Restore,
                title = stringResource(R.string.settings_restore),
                supporting = stringResource(R.string.settings_restore_supporting),
                onClick = { onAction(SettingsAction.OnRestoreClick) },
                // Reading and writing are the two stages with nothing to decide, so they show the
                // same spinner the neighbouring actions use rather than a dialog of their own.
                trailing = if (state.restore.isBusy) {
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
                text = stringResource(R.string.settings_version, appVersion, appVersionCode),
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

        if (state.showThemePicker) {
            ThemePickerDialog(
                selected = state.theme,
                onSelect = { onAction(SettingsAction.OnThemeSelected(it)) },
                onDismiss = { onAction(SettingsAction.OnThemePickerDismissed) }
            )
        }

        RestoreDialogs(
            stage = state.restore,
            signedInEmail = state.email,
            onAction = onAction
        )
    }
}

/** The label shown for a theme choice, both in the row and in the picker. */
private val ThemePreference.labelRes: Int
    get() = when (this) {
        ThemePreference.SYSTEM -> R.string.settings_theme_system
        ThemePreference.LIGHT -> R.string.settings_theme_light
        ThemePreference.DARK -> R.string.settings_theme_dark
    }

/**
 * A single-choice list rather than a switch, because there are three answers and not two — the
 * third, following the device, is the default and cannot be expressed as an on/off.
 *
 * There is no confirm button: picking applies immediately and the theme changes behind the
 * dialog, which is the fastest way to see whether it is the one you wanted.
 */
@Composable
private fun ThemePickerDialog(
    selected: ThemePreference,
    onSelect: (ThemePreference) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme)) },
        text = {
            Column {
                ThemePreference.entries.forEach { preference ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = preference == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(preference) }
                            )
                            .padding(vertical = 12.dp)
                    ) {
                        RadioButton(
                            selected = preference == selected,
                            // The whole row is the target; the button would otherwise be a second,
                            // smaller one sitting inside it.
                            onClick = null
                        )
                        Text(
                            text = stringResource(preference.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_theme_done))
            }
        }
    )
}

/**
 * The three points in a restore where the user decides something.
 *
 * Everything shown here is read from a file that has already been fully validated, so the counts
 * are facts rather than estimates — and nothing has been written to the database yet at either of
 * the first two.
 */
@Composable
private fun RestoreDialogs(
    stage: RestoreStage,
    signedInEmail: String,
    onAction: (SettingsAction) -> Unit
) {
    when (stage) {
        is RestoreStage.AccountMismatch -> AppAlertDialog(
            title = stringResource(R.string.settings_restore_account_title),
            message = stringResource(
                R.string.settings_restore_account_message,
                stage.preview.account?.email.orEmpty(),
                signedInEmail
            ),
            confirmLabel = stringResource(R.string.settings_restore_account_continue),
            dismissLabel = stringResource(R.string.settings_cancel),
            onConfirm = { onAction(SettingsAction.OnRestoreAccountAccepted) },
            onDismiss = { onAction(SettingsAction.OnRestoreDismissed) }
        )

        is RestoreStage.Confirm -> AppAlertDialog(
            title = stringResource(R.string.settings_restore_confirm_title),
            message = stringResource(
                R.string.settings_restore_confirm_message,
                formatInstantDate(stage.preview.exportedAtMillis),
                stage.preview.appVersionName,
                stage.preview.summary.transactions,
                stage.preview.summary.payees,
                stage.preview.summary.payeeIdentifiers,
                stage.preview.summary.categories,
                stage.preview.summary.sessions
            ),
            confirmLabel = stringResource(R.string.settings_restore_confirm_action),
            dismissLabel = stringResource(R.string.settings_cancel),
            onConfirm = { onAction(SettingsAction.OnRestoreConfirmed) },
            onDismiss = { onAction(SettingsAction.OnRestoreDismissed) }
        )

        is RestoreStage.Done -> AppAlertDialog(
            title = stringResource(R.string.settings_restore_done_title),
            message = restoreReportText(stage.report),
            confirmLabel = stringResource(R.string.settings_restore_done_action),
            onConfirm = { onAction(SettingsAction.OnRestoreDismissed) },
            onDismiss = { onAction(SettingsAction.OnRestoreDismissed) }
        )

        RestoreStage.Idle, RestoreStage.Reading, RestoreStage.Writing -> Unit
    }
}

/**
 * The result, including every identifier the restore declined to repoint.
 *
 * The conflicts are listed rather than counted because each one is a mapping the user will find
 * behaving differently from the device the backup came from, and a bare number gives them nothing
 * to go and look at.
 */
@Composable
private fun restoreReportText(report: RestoreReport): String {
    val summary = stringResource(
        R.string.settings_restore_done_message,
        report.transactions,
        report.duplicatesFlagged,
        report.payees,
        report.payeeIdentifiers,
        report.sessions,
        report.categoriesInserted,
        report.categoriesReused
    )
    if (report.identifierConflicts.isEmpty()) return summary

    // getString rather than stringResource: joinToString's lambda is not inline, so a composable
    // call inside it would not compile.
    val context = LocalContext.current
    // Restoring a backup onto the account it came from makes every mapped name a conflict, which on
    // a real account is dozens of them — more than a dialog can show, and past the first few the
    // list stops telling the reader anything the count does not.
    val shown = report.identifierConflicts.take(MAX_LISTED_CONFLICTS)
    val lines = shown.joinToString("\n") { conflict ->
        context.getString(
            R.string.settings_restore_done_conflict_line,
            conflict.normalizedName,
            conflict.keptPayeeAlias,
            conflict.filePayeeAlias
        )
    } + if (report.identifierConflicts.size > shown.size) {
        "\n" + context.getString(
            R.string.settings_restore_done_conflicts_more,
            report.identifierConflicts.size - shown.size
        )
    } else {
        ""
    }
    return summary + stringResource(
        R.string.settings_restore_done_conflicts,
        report.identifierConflicts.size,
        lines
    )
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
