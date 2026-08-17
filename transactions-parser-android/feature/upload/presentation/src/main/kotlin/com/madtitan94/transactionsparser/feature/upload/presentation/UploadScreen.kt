package com.madtitan94.transactionsparser.feature.upload.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madtitan94.transactionsparser.core.designsystem.components.AppAlertDialog
import com.madtitan94.transactionsparser.core.designsystem.theme.TransactionsParserTheme
import com.madtitan94.transactionsparser.core.presentation.ObserveAsEvents
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun UploadRoot(
    onOpenSession: (Long) -> Unit,
    viewModel: UploadViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is UploadEvent.NavigateToSession -> onOpenSession(event.sessionId)
            is UploadEvent.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(event.message.asString(context))
            }
        }
    }

    UploadScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    state: UploadState,
    snackbarHostState: SnackbarHostState,
    onAction: (UploadAction) -> Unit
) {
    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onAction(UploadAction.OnFilePicked(it.toString())) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.upload_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.upload_privacy_note),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text(
                text = stringResource(R.string.upload_instructions),
                style = MaterialTheme.typography.bodyMedium
            )

            if (state.pickedFileName == null) {
                Button(
                    onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                    enabled = !state.isImporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Text(
                        text = stringResource(R.string.upload_select_pdf),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = state.pickedFileName,
                                style = MaterialTheme.typography.titleSmall
                            )
                            state.pickedFileSizeBytes?.let {
                                Text(
                                    text = formatFileSize(it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(
                            onClick = { onAction(UploadAction.OnClearSelection) },
                            enabled = !state.isImporting
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.upload_clear_selection)
                            )
                        }
                    }
                }

                if (state.isImporting) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.upload_parsing),
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = { onAction(UploadAction.OnImportClick) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.upload_parse_button))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.upload_supported_sources),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    state.error?.let { error ->
        AppAlertDialog(
            title = stringResource(R.string.upload_error_title),
            message = error.asString() + if (state.tempFileDeleted) {
                "\n\n" + stringResource(R.string.upload_temp_deleted)
            } else {
                ""
            },
            confirmLabel = stringResource(R.string.upload_ok),
            onConfirm = { onAction(UploadAction.OnErrorDismiss) },
            onDismiss = { onAction(UploadAction.OnErrorDismiss) }
        )
    }

    state.success?.let { success ->
        AppAlertDialog(
            title = stringResource(R.string.upload_success_title),
            message = stringResource(
                if (success.completedOnImport) {
                    R.string.upload_success_message_complete
                } else {
                    R.string.upload_success_message
                },
                success.totalTransactions,
                success.autoMappedPayees
            ) + "\n\n" + stringResource(R.string.upload_temp_deleted),
            confirmLabel = stringResource(R.string.upload_review_now),
            dismissLabel = stringResource(R.string.upload_later),
            onConfirm = { onAction(UploadAction.OnReviewClick) },
            onDismiss = { onAction(UploadAction.OnSuccessDismiss) }
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) "%.1f MB".format(mb) else "${bytes / 1024} KB"
}

@Preview(showBackground = true)
@Composable
private fun UploadScreenPreview() {
    TransactionsParserTheme {
        UploadScreen(
            state = UploadState(
                pickedFileName = "PhonePe_Statement_Jul2026.pdf",
                pickedFileSizeBytes = 1_248_576
            ),
            snackbarHostState = SnackbarHostState(),
            onAction = {}
        )
    }
}
