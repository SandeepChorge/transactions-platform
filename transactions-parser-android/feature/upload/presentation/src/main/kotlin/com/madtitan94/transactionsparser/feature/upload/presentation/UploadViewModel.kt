package com.madtitan94.transactionsparser.feature.upload.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madtitan94.transactionsparser.core.domain.datasource.UploadLogLocalDataSource
import com.madtitan94.transactionsparser.core.domain.model.UploadLog
import com.madtitan94.transactionsparser.core.domain.parsing.ParseError
import com.madtitan94.transactionsparser.core.domain.util.Result
import com.madtitan94.transactionsparser.core.presentation.UiText
import com.madtitan94.transactionsparser.core.presentation.toUiText
import com.madtitan94.transactionsparser.feature.upload.domain.ImportResult
import com.madtitan94.transactionsparser.feature.upload.domain.ImportStatementUseCase
import com.madtitan94.transactionsparser.feature.upload.domain.StatementFileDataSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportSuccessUi(
    val sessionId: Long,
    val totalTransactions: Int,
    val autoMappedPayees: Int,
    /** The session needed no mapping and is already completed — don't call it pending. */
    val completedOnImport: Boolean = false
)

data class UploadState(
    val pickedUri: String? = null,
    val pickedFileName: String? = null,
    val pickedFileSizeBytes: Long? = null,
    val isImporting: Boolean = false,
    val success: ImportSuccessUi? = null,
    val error: UiText? = null,
    val tempFileDeleted: Boolean = false
)

sealed interface UploadAction {
    data class OnFilePicked(val uriString: String) : UploadAction
    data object OnClearSelection : UploadAction
    data object OnImportClick : UploadAction
    data object OnErrorDismiss : UploadAction
    data object OnSuccessDismiss : UploadAction
    data object OnReviewClick : UploadAction
}

sealed interface UploadEvent {
    data class NavigateToSession(val sessionId: Long) : UploadEvent
    data class ShowMessage(val message: UiText) : UploadEvent
}

class UploadViewModel(
    private val importStatement: ImportStatementUseCase,
    private val fileDataSource: StatementFileDataSource,
    private val uploadLogs: UploadLogLocalDataSource
) : ViewModel() {

    private val _state = MutableStateFlow(UploadState())
    val state = _state.asStateFlow()

    private val _events = Channel<UploadEvent>()
    val events = _events.receiveAsFlow()

    fun onAction(action: UploadAction) {
        when (action) {
            is UploadAction.OnFilePicked -> onFilePicked(action.uriString)
            UploadAction.OnClearSelection -> _state.update { UploadState() }
            UploadAction.OnImportClick -> import()
            UploadAction.OnErrorDismiss -> _state.update { it.copy(error = null) }
            UploadAction.OnSuccessDismiss -> _state.update { UploadState() }
            UploadAction.OnReviewClick -> {
                val sessionId = _state.value.success?.sessionId ?: return
                _state.update { UploadState() }
                viewModelScope.launch { _events.send(UploadEvent.NavigateToSession(sessionId)) }
            }
        }
    }

    private fun onFilePicked(uriString: String) {
        viewModelScope.launch {
            when (val metadata = fileDataSource.metadata(uriString)) {
                is Result.Error -> {
                    _state.update { it.copy(error = metadata.error.toUiText()) }
                }
                is Result.Success -> {
                    val meta = metadata.data
                    when {
                        !meta.displayName.endsWith(".pdf", ignoreCase = true) -> {
                            rejectPickedFile(meta.displayName, ParseError.NOT_A_PDF)
                        }
                        meta.sizeBytes > MAX_FILE_SIZE_BYTES -> {
                            rejectPickedFile(meta.displayName, ParseError.FILE_TOO_LARGE)
                        }
                        else -> _state.update {
                            it.copy(
                                pickedUri = uriString,
                                pickedFileName = meta.displayName,
                                pickedFileSizeBytes = meta.sizeBytes,
                                error = null,
                                success = null,
                                tempFileDeleted = false
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun rejectPickedFile(fileName: String, error: ParseError) {
        uploadLogs.log(
            UploadLog(
                fileName = fileName,
                uploadedAtMillis = System.currentTimeMillis(),
                success = false,
                source = null,
                failureReason = error.name,
                sessionId = null
            )
        )
        _state.update { it.copy(error = error.toUiText(), pickedUri = null, pickedFileName = null) }
    }

    private fun import() {
        val current = _state.value
        val uri = current.pickedUri ?: return
        val fileName = current.pickedFileName ?: return

        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, error = null) }

            val tempPath = when (val copy = fileDataSource.copyToCache(uri)) {
                is Result.Error -> {
                    uploadLogs.log(
                        UploadLog(
                            fileName = fileName,
                            uploadedAtMillis = System.currentTimeMillis(),
                            success = false,
                            source = null,
                            failureReason = copy.error.name,
                            sessionId = null
                        )
                    )
                    _state.update { it.copy(isImporting = false, error = copy.error.toUiText()) }
                    return@launch
                }
                is Result.Success -> copy.data
            }

            val result = try {
                importStatement(tempFilePath = tempPath, fileName = fileName)
            } finally {
                // The statement copy never outlives parsing — privacy guarantee.
                fileDataSource.deleteTempFile(tempPath)
            }

            when (result) {
                is Result.Error -> _state.update {
                    it.copy(isImporting = false, error = result.error.toUiText(), tempFileDeleted = true)
                }
                is Result.Success -> _state.update {
                    it.copy(
                        isImporting = false,
                        pickedUri = null,
                        pickedFileName = null,
                        pickedFileSizeBytes = null,
                        tempFileDeleted = true,
                        success = ImportSuccessUi(
                            sessionId = result.data.sessionId,
                            totalTransactions = result.data.totalTransactions,
                            autoMappedPayees = result.data.autoMappedPayees,
                            completedOnImport = result.data.completedOnImport
                        )
                    )
                }
            }
        }
    }

    companion object {
        const val MAX_FILE_SIZE_BYTES = 80L * 1024 * 1024
    }
}
