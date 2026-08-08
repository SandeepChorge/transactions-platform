package com.madtitan94.transactionsparser.feature.sessions.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madtitan94.transactionsparser.core.domain.datasource.SessionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.model.SessionStatus
import com.madtitan94.transactionsparser.core.domain.model.SessionSummary
import com.madtitan94.transactionsparser.core.domain.model.StatementSource
import com.madtitan94.transactionsparser.core.presentation.formatStatementDate
import com.madtitan94.transactionsparser.core.presentation.formatStatementDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionSummaryUi(
    val id: Long,
    val fileName: String,
    val sourceLabel: String,
    val uploadedLabel: String,
    val periodLabel: String?,
    val transactionCount: Int,
    val mappedCount: Int,
    val isPending: Boolean
)

data class SessionsHistoryState(
    val selectedTab: SessionStatus = SessionStatus.PENDING,
    val sessions: List<SessionSummaryUi> = emptyList(),
    val isLoading: Boolean = true,
    val cancelConfirmId: Long? = null
)

sealed interface SessionsHistoryAction {
    data class OnTabSelect(val status: SessionStatus) : SessionsHistoryAction
    data class OnSessionClick(val sessionId: Long) : SessionsHistoryAction
    data class OnCancelClick(val sessionId: Long) : SessionsHistoryAction
    data object OnConfirmCancel : SessionsHistoryAction
    data object OnDismissCancel : SessionsHistoryAction
    data object OnUploadHistoryClick : SessionsHistoryAction
}

sealed interface SessionsHistoryEvent {
    data class OpenSession(val sessionId: Long) : SessionsHistoryEvent
    data object OpenUploadHistory : SessionsHistoryEvent
}

class SessionsHistoryViewModel(
    private val sessions: SessionLocalDataSource
) : ViewModel() {

    private val _state = MutableStateFlow(SessionsHistoryState())
    val state = _state.asStateFlow()

    private val _events = Channel<SessionsHistoryEvent>()
    val events = _events.receiveAsFlow()

    private val selectedTab = MutableStateFlow(SessionStatus.PENDING)

    init {
        observeSessions()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSessions() {
        viewModelScope.launch {
            selectedTab
                .flatMapLatest { status -> sessions.observeSummaries(status) }
                .collect { summaries ->
                    _state.update { current ->
                        current.copy(
                            isLoading = false,
                            sessions = summaries.map { it.toUi() }
                        )
                    }
                }
        }
    }

    fun onAction(action: SessionsHistoryAction) {
        when (action) {
            is SessionsHistoryAction.OnTabSelect -> {
                selectedTab.value = action.status
                _state.update { it.copy(selectedTab = action.status, isLoading = true) }
            }
            is SessionsHistoryAction.OnSessionClick -> {
                viewModelScope.launch { _events.send(SessionsHistoryEvent.OpenSession(action.sessionId)) }
            }
            is SessionsHistoryAction.OnCancelClick -> {
                _state.update { it.copy(cancelConfirmId = action.sessionId) }
            }
            SessionsHistoryAction.OnConfirmCancel -> {
                val id = _state.value.cancelConfirmId ?: return
                _state.update { it.copy(cancelConfirmId = null) }
                viewModelScope.launch { sessions.updateStatus(id, SessionStatus.CANCELLED) }
            }
            SessionsHistoryAction.OnDismissCancel -> {
                _state.update { it.copy(cancelConfirmId = null) }
            }
            SessionsHistoryAction.OnUploadHistoryClick -> {
                viewModelScope.launch { _events.send(SessionsHistoryEvent.OpenUploadHistory) }
            }
        }
    }
}

internal fun SessionSummary.toUi() = SessionSummaryUi(
    id = session.id,
    fileName = session.fileName,
    sourceLabel = session.source.toLabel(),
    uploadedLabel = formatStatementDateTime(session.uploadedAtMillis),
    periodLabel = session.periodStartMillis?.let { start ->
        session.periodEndMillis?.let { end ->
            "${formatStatementDate(start)} – ${formatStatementDate(end)}"
        }
    },
    transactionCount = transactionCount,
    mappedCount = mappedCount,
    isPending = session.status == SessionStatus.PENDING
)

internal fun StatementSource.toLabel(): String = when (this) {
    StatementSource.PHONEPE -> "PhonePe"
    StatementSource.GOOGLE_PAY -> "Google Pay"
}
