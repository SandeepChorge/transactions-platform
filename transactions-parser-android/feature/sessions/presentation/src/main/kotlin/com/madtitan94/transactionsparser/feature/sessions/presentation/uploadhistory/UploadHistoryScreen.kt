package com.madtitan94.transactionsparser.feature.sessions.presentation.uploadhistory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.madtitan94.transactionsparser.core.designsystem.components.EmptyState
import com.madtitan94.transactionsparser.core.designsystem.components.LoadingIndicator
import com.madtitan94.transactionsparser.core.domain.datasource.UploadLogLocalDataSource
import com.madtitan94.transactionsparser.core.presentation.formatStatementDateTime
import com.madtitan94.transactionsparser.feature.sessions.presentation.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class UploadLogUi(
    val id: Long,
    val fileName: String,
    val timeLabel: String,
    val success: Boolean,
    val failureReason: String?
)

data class UploadHistoryState(
    val isLoading: Boolean = true,
    val logs: List<UploadLogUi> = emptyList()
)

class UploadHistoryViewModel(
    private val uploadLogs: UploadLogLocalDataSource
) : ViewModel() {

    private val _state = MutableStateFlow(UploadHistoryState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            uploadLogs.observeAll().collect { logs ->
                _state.update { current ->
                    current.copy(
                        isLoading = false,
                        logs = logs.map { log ->
                            UploadLogUi(
                                id = log.id,
                                fileName = log.fileName,
                                timeLabel = formatStatementDateTime(log.uploadedAtMillis),
                                success = log.success,
                                failureReason = log.failureReason
                                    ?.lowercase()
                                    ?.replace('_', ' ')
                                    ?.replaceFirstChar(Char::uppercase)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun UploadHistoryRoot(
    onBack: () -> Unit,
    viewModel: UploadHistoryViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UploadHistoryScreen(state = state, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadHistoryScreen(
    state: UploadHistoryState,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.upload_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingIndicator(Modifier.padding(padding))
            state.logs.isEmpty() -> EmptyState(
                title = stringResource(R.string.upload_history_empty_title),
                description = stringResource(R.string.upload_history_empty_description),
                modifier = Modifier.padding(padding)
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = state.logs, key = { it.id }) { log ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (log.success) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (log.success) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(log.fileName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = log.timeLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                log.failureReason?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
