package com.madtitan94.transactionsparser.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val isExporting: Boolean = false
)

sealed interface SettingsAction {
    data object OnLogoutClick : SettingsAction
    data object OnConfirmLogout : SettingsAction
    data object OnDismissLogoutConfirm : SettingsAction
    data object OnExportClick : SettingsAction
    /** The picker returned a destination. */
    data class OnExportDestinationChosen(val destination: String) : SettingsAction
    /** The user backed out of the picker — not an error, so it only clears the busy state. */
    data object OnExportCancelled : SettingsAction
}

sealed interface SettingsEvent {
    data class ShowMessage(val message: UiText) : SettingsEvent
    /** Asks the screen to open the system file picker, since only it can launch one. */
    data class LaunchExportPicker(val suggestedFileName: String) : SettingsEvent
}

class SettingsViewModel(
    private val sessionStorage: SessionStorage,
    private val transactions: TransactionLocalDataSource,
    private val documentWriter: DocumentWriter
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

    /** Dated so repeated exports don't silently overwrite each other in the picker's default spot. */
    private fun suggestedFileName(): String = "transactions-${LocalDate.now()}.csv"
}
