package com.madtitan94.transactionsparser.feature.sessions.presentation.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madtitan94.transactionsparser.core.designsystem.components.AppAlertDialog
import com.madtitan94.transactionsparser.core.designsystem.components.EmptyState
import com.madtitan94.transactionsparser.core.designsystem.components.LoadingIndicator
import com.madtitan94.transactionsparser.core.designsystem.theme.TransactionsParserTheme
import com.madtitan94.transactionsparser.core.domain.model.SessionStatus
import com.madtitan94.transactionsparser.core.presentation.ObserveAsEvents
import com.madtitan94.transactionsparser.feature.sessions.presentation.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun SessionsHistoryRoot(
    onOpenSession: (Long) -> Unit,
    onOpenUploadHistory: () -> Unit,
    viewModel: SessionsHistoryViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SessionsHistoryEvent.OpenSession -> onOpenSession(event.sessionId)
            SessionsHistoryEvent.OpenUploadHistory -> onOpenUploadHistory()
        }
    }

    SessionsHistoryScreen(state = state, onAction = viewModel::onAction)
}

private val TABS = listOf(
    SessionStatus.PENDING,
    SessionStatus.COMPLETED,
    SessionStatus.CANCELLED
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsHistoryScreen(
    state: SessionsHistoryState,
    onAction: (SessionsHistoryAction) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sessions_title)) },
                actions = {
                    IconButton(onClick = { onAction(SessionsHistoryAction.OnUploadHistoryClick) }) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = stringResource(R.string.sessions_upload_history)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = TABS.indexOf(state.selectedTab)) {
                TABS.forEach { status ->
                    Tab(
                        selected = state.selectedTab == status,
                        onClick = { onAction(SessionsHistoryAction.OnTabSelect(status)) },
                        text = {
                            Text(
                                when (status) {
                                    SessionStatus.PENDING -> stringResource(R.string.tab_pending)
                                    SessionStatus.COMPLETED -> stringResource(R.string.tab_completed)
                                    SessionStatus.CANCELLED -> stringResource(R.string.tab_cancelled)
                                }
                            )
                        }
                    )
                }
            }

            when {
                state.isLoading -> LoadingIndicator()
                state.sessions.isEmpty() -> EmptyState(
                    title = stringResource(R.string.sessions_empty_title),
                    description = when (state.selectedTab) {
                        SessionStatus.PENDING -> stringResource(R.string.sessions_empty_pending)
                        SessionStatus.COMPLETED -> stringResource(R.string.sessions_empty_completed)
                        SessionStatus.CANCELLED -> stringResource(R.string.sessions_empty_cancelled)
                    }
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items = state.sessions, key = { it.id }) { session ->
                        SessionCard(
                            session = session,
                            onClick = { onAction(SessionsHistoryAction.OnSessionClick(session.id)) },
                            onCancelClick = { onAction(SessionsHistoryAction.OnCancelClick(session.id)) }
                        )
                    }
                }
            }
        }
    }

    if (state.cancelConfirmId != null) {
        AppAlertDialog(
            title = stringResource(R.string.cancel_session_title),
            message = stringResource(R.string.cancel_session_message),
            confirmLabel = stringResource(R.string.cancel_session_confirm),
            dismissLabel = stringResource(R.string.cancel_session_dismiss),
            onConfirm = { onAction(SessionsHistoryAction.OnConfirmCancel) },
            onDismiss = { onAction(SessionsHistoryAction.OnDismissCancel) }
        )
    }
}

@Composable
private fun SessionCard(
    session: SessionSummaryUi,
    onClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = session.fileName,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.session_uploaded_at, session.uploadedLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SuggestionChip(onClick = onClick, label = { Text(session.sourceLabel) })
                if (session.isPending) {
                    IconButton(onClick = onCancelClick) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = stringResource(R.string.cancel_session_title),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            session.periodLabel?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.session_mapping_progress,
                    session.mappedCount,
                    session.transactionCount
                ),
                style = MaterialTheme.typography.bodySmall
            )
            if (session.transactionCount > 0) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { session.mappedCount.toFloat() / session.transactionCount },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionsHistoryScreenPreview() {
    TransactionsParserTheme {
        SessionsHistoryScreen(
            state = SessionsHistoryState(
                isLoading = false,
                sessions = listOf(
                    SessionSummaryUi(
                        id = 1,
                        fileName = "PhonePe_Statement_Jul2026.pdf",
                        sourceLabel = "PhonePe",
                        uploadedLabel = "12 Jul 2026, 2:41 PM",
                        periodLabel = "01 Jul 2026 – 31 Jul 2026",
                        transactionCount = 5,
                        mappedCount = 2,
                        isPending = true
                    )
                )
            ),
            onAction = {}
        )
    }
}
