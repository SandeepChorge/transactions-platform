package com.madtitan94.transactionsparser.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madtitan94.transactionsparser.core.domain.backup.BackupPreview
import com.madtitan94.transactionsparser.core.domain.backup.CreateBackupUseCase
import com.madtitan94.transactionsparser.core.domain.backup.ReadBackupUseCase
import com.madtitan94.transactionsparser.core.domain.backup.RestoreBackupUseCase
import com.madtitan94.transactionsparser.core.domain.backup.RestoreReport
import com.madtitan94.transactionsparser.core.domain.datasource.DocumentWriter
import com.madtitan94.transactionsparser.core.domain.datasource.SessionStorage
import com.madtitan94.transactionsparser.core.domain.datasource.TransactionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.export.TransactionCsv
import com.madtitan94.transactionsparser.core.domain.util.Result
import com.madtitan94.transactionsparser.core.presentation.UiText
import com.madtitan94.transactionsparser.core.presentation.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SettingsState(
    val email: String = "",
    val name: String = "",
    val photoUrl: String? = null,
    val showLogoutConfirm: Boolean = false,
    val isExporting: Boolean = false,
    val isBackingUp: Boolean = false,
    val restore: RestoreStage = RestoreStage.Idle
)

/**
 * Where a restore has got to.
 *
 * Modelled as a sequence rather than as a handful of booleans because the order is the safety
 * property: the file is read and fully validated before the user is asked anything, the account
 * warning comes before the summary, and nothing is written until [Confirm] is answered.
 */
sealed interface RestoreStage {
    data object Idle : RestoreStage

    /** Reading and validating the picked file. Still nothing written. */
    data object Reading : RestoreStage

    /** The file belongs to another account, which the user has to acknowledge before seeing more. */
    data class AccountMismatch(val preview: BackupPreview) : RestoreStage

    /** What the file contains, for the user to accept or reject. Still nothing written. */
    data class Confirm(val preview: BackupPreview) : RestoreStage

    data object Writing : RestoreStage

    data class Done(val report: RestoreReport) : RestoreStage
}

/** The stages with nothing for the user to do but wait. */
val RestoreStage.isBusy: Boolean
    get() = this is RestoreStage.Reading || this is RestoreStage.Writing

sealed interface SettingsAction {
    data object OnLogoutClick : SettingsAction
    data object OnConfirmLogout : SettingsAction
    data object OnDismissLogoutConfirm : SettingsAction
    data object OnExportClick : SettingsAction
    /** The picker returned a destination. */
    data class OnExportDestinationChosen(val destination: String) : SettingsAction
    /** The user backed out of the picker — not an error, so it only clears the busy state. */
    data object OnExportCancelled : SettingsAction
    data object OnBackupClick : SettingsAction
    data class OnBackupDestinationChosen(val destination: String) : SettingsAction
    data object OnBackupCancelled : SettingsAction
    data object OnRestoreClick : SettingsAction
    /** The picker returned a file to read. Nothing is written until [OnRestoreConfirmed]. */
    data class OnRestoreSourceChosen(val source: String) : SettingsAction
    /** The user accepted that the file belongs to a different account. */
    data object OnRestoreAccountAccepted : SettingsAction
    data object OnRestoreConfirmed : SettingsAction
    /** Backing out at any point before the write, and dismissing the report after it. */
    data object OnRestoreDismissed : SettingsAction
}

sealed interface SettingsEvent {
    data class ShowMessage(val message: UiText) : SettingsEvent
    /** Asks the screen to open the system file picker, since only it can launch one. */
    data class LaunchExportPicker(val suggestedFileName: String) : SettingsEvent
    data class LaunchBackupPicker(val suggestedFileName: String) : SettingsEvent
    data object LaunchRestorePicker : SettingsEvent
}

class SettingsViewModel(
    private val sessionStorage: SessionStorage,
    private val transactions: TransactionLocalDataSource,
    private val documentWriter: DocumentWriter,
    private val createBackup: CreateBackupUseCase,
    private val readBackup: ReadBackupUseCase,
    private val restoreBackup: RestoreBackupUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state = _state.asStateFlow()

    private val _events = Channel<SettingsEvent>()
    val events = _events.receiveAsFlow()

    init {
        observeSession()
    }

    private fun observeSession() {
        viewModelScope.launch {
            sessionStorage.observeSession().collect { session ->
                _state.update {
                    it.copy(
                        email = session?.email.orEmpty(),
                        name = session?.name.orEmpty(),
                        photoUrl = session?.photoUrl
                    )
                }
            }
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.OnLogoutClick -> _state.update { it.copy(showLogoutConfirm = true) }
            SettingsAction.OnDismissLogoutConfirm -> _state.update { it.copy(showLogoutConfirm = false) }
            SettingsAction.OnConfirmLogout -> {
                _state.update { it.copy(showLogoutConfirm = false) }
                viewModelScope.launch { sessionStorage.clear() }
            }
            SettingsAction.OnExportClick -> startExport()
            is SettingsAction.OnExportDestinationChosen -> export(action.destination)
            SettingsAction.OnExportCancelled -> _state.update { it.copy(isExporting = false) }
            SettingsAction.OnBackupClick -> startBackup()
            is SettingsAction.OnBackupDestinationChosen -> backup(action.destination)
            SettingsAction.OnBackupCancelled -> _state.update { it.copy(isBackingUp = false) }
            SettingsAction.OnRestoreClick -> startRestore()
            is SettingsAction.OnRestoreSourceChosen -> readRestore(action.source)
            SettingsAction.OnRestoreAccountAccepted -> acceptRestoreAccount()
            SettingsAction.OnRestoreConfirmed -> writeRestore()
            SettingsAction.OnRestoreDismissed -> _state.update { it.copy(restore = RestoreStage.Idle) }
        }
    }

    private fun startExport() {
        if (_state.value.isExporting) return
        _state.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            _events.send(SettingsEvent.LaunchExportPicker(suggestedFileName()))
        }
    }

    private fun export(destination: String) {
        viewModelScope.launch {
            val rows = when (val result = transactions.exportRows()) {
                is Result.Error -> {
                    finishExport(result.error.toUiText())
                    return@launch
                }
                is Result.Success -> result.data
            }

            // An empty export is still written rather than refused: a file with only a header row
            // is an honest answer to "export my data" when there is none, and refusing would look
            // the same as a failure.
            val message = when (val written = documentWriter.write(destination, TransactionCsv.build(rows))) {
                is Result.Error -> written.error.toUiText()
                is Result.Success -> UiText.StringResource(R.string.settings_export_done, arrayOf(rows.size))
            }
            finishExport(message)
        }
    }

    private suspend fun finishExport(message: UiText) {
        _state.update { it.copy(isExporting = false) }
        _events.send(SettingsEvent.ShowMessage(message))
    }

    private fun startBackup() {
        if (_state.value.isBackingUp) return
        _state.update { it.copy(isBackingUp = true) }
        viewModelScope.launch {
            _events.send(SettingsEvent.LaunchBackupPicker(suggestedBackupFileName()))
        }
    }

    private fun backup(destination: String) {
        viewModelScope.launch {
            // The transaction count is the number the user can check against the app, so it is
            // what the confirmation reports — the other five tables are along for the ride and
            // saying "1,247 rows" would be a number with nothing to compare it to.
            val message = when (val result = createBackup(destination)) {
                is Result.Error -> result.error.toUiText()
                is Result.Success -> UiText.StringResource(
                    R.string.settings_backup_done,
                    arrayOf(result.data.transactions)
                )
            }
            _state.update { it.copy(isBackingUp = false) }
            _events.send(SettingsEvent.ShowMessage(message))
        }
    }

    private fun startRestore() {
        if (_state.value.restore != RestoreStage.Idle) return
        viewModelScope.launch { _events.send(SettingsEvent.LaunchRestorePicker) }
    }

    private fun readRestore(source: String) {
        _state.update { it.copy(restore = RestoreStage.Reading) }
        viewModelScope.launch {
            when (val preview = readBackup(source)) {
                is Result.Error -> {
                    _state.update { it.copy(restore = RestoreStage.Idle) }
                    _events.send(SettingsEvent.ShowMessage(preview.error.toUiText()))
                }
                // Two stops rather than one: a file from another account is a different decision
                // from "is this the right backup", and folding them together would let someone
                // agree to the counts while missing whose data they are.
                is Result.Success -> _state.update {
                    it.copy(
                        restore = if (preview.data.isDifferentAccount) {
                            RestoreStage.AccountMismatch(preview.data)
                        } else {
                            RestoreStage.Confirm(preview.data)
                        }
                    )
                }
            }
        }
    }

    private fun acceptRestoreAccount() {
        val stage = _state.value.restore as? RestoreStage.AccountMismatch ?: return
        _state.update { it.copy(restore = RestoreStage.Confirm(stage.preview)) }
    }

    private fun writeRestore() {
        val stage = _state.value.restore as? RestoreStage.Confirm ?: return
        _state.update { it.copy(restore = RestoreStage.Writing) }
        viewModelScope.launch {
            when (val result = restoreBackup(stage.preview.file)) {
                is Result.Error -> {
                    // Nothing was written — the whole restore is one transaction — so dropping
                    // back to Idle leaves the account exactly as it was.
                    _state.update { it.copy(restore = RestoreStage.Idle) }
                    _events.send(SettingsEvent.ShowMessage(result.error.toUiText()))
                }
                is Result.Success -> _state.update {
                    it.copy(restore = RestoreStage.Done(result.data))
                }
            }
        }
    }

    /** Dated so repeated exports don't silently overwrite each other in the picker's default spot. */
    private fun suggestedFileName(): String = "transactions-${LocalDate.now()}.csv"

    private fun suggestedBackupFileName(): String = "transactions-backup-${LocalDate.now()}.json"
}
