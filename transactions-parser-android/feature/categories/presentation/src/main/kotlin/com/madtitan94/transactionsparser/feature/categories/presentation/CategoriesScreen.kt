package com.madtitan94.transactionsparser.feature.categories.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madtitan94.transactionsparser.core.designsystem.components.AppAlertDialog
import com.madtitan94.transactionsparser.core.designsystem.components.EmptyState
import com.madtitan94.transactionsparser.core.designsystem.components.LoadingIndicator
import com.madtitan94.transactionsparser.core.presentation.ObserveAsEvents
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun CategoriesRoot(
    viewModel: CategoriesViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is CategoriesEvent.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(event.message.asString(context))
            }
        }
    }

    CategoriesScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    state: CategoriesState,
    snackbarHostState: SnackbarHostState,
    onAction: (CategoriesAction) -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.categories_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAction(CategoriesAction.OnAddClick) }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.category_add)
                )
            }
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingIndicator(Modifier.padding(padding))
            state.categories.isEmpty() -> EmptyState(
                title = stringResource(R.string.categories_empty_title),
                description = stringResource(R.string.categories_empty_description),
                modifier = Modifier.padding(padding)
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = state.categories, key = { it.id }) { category ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(category.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = if (category.linkedPayeeCount == 0) {
                                        stringResource(R.string.category_unused)
                                    } else {
                                        stringResource(R.string.category_linked_payees, category.linkedPayeeCount)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onAction(CategoriesAction.OnEditClick(category.id)) }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.category_edit)
                                )
                            }
                            IconButton(
                                onClick = { onAction(CategoriesAction.OnDeleteClick(category.id)) },
                                enabled = category.canDelete
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.category_delete),
                                    tint = if (category.canDelete) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    when (val dialog = state.dialog) {
        is CategoryDialog.Add -> NameDialog(
            title = stringResource(R.string.category_add),
            name = dialog.name,
            error = dialog.error?.asString(),
            onAction = onAction
        )
        is CategoryDialog.Edit -> NameDialog(
            title = stringResource(R.string.category_edit),
            name = dialog.name,
            error = dialog.error?.asString(),
            onAction = onAction
        )
        is CategoryDialog.ConfirmDelete -> AppAlertDialog(
            title = stringResource(R.string.category_delete),
            message = stringResource(R.string.category_delete_confirm, dialog.name),
            confirmLabel = stringResource(R.string.category_delete),
            dismissLabel = stringResource(R.string.category_cancel),
            onConfirm = { onAction(CategoriesAction.OnDialogConfirm) },
            onDismiss = { onAction(CategoriesAction.OnDialogDismiss) }
        )
        null -> Unit
    }
}

@Composable
private fun NameDialog(
    title: String,
    name: String,
    error: String?,
    onAction: (CategoriesAction) -> Unit
) {
    Dialog(onDismissRequest = { onAction(CategoriesAction.OnDialogDismiss) }) {
        Card {
            Column(Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { onAction(CategoriesAction.OnDialogNameChange(it)) },
                    label = { Text(stringResource(R.string.category_name_label)) },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onAction(CategoriesAction.OnDialogDismiss) }) {
                        Text(stringResource(R.string.category_cancel))
                    }
                    TextButton(onClick = { onAction(CategoriesAction.OnDialogConfirm) }) {
                        Text(stringResource(R.string.category_save))
                    }
                }
            }
        }
    }
}
