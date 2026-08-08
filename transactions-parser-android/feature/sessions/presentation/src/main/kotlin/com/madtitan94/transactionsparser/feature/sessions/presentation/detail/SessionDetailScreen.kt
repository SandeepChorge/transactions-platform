package com.madtitan94.transactionsparser.feature.sessions.presentation.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madtitan94.transactionsparser.core.designsystem.components.EmptyState
import com.madtitan94.transactionsparser.core.designsystem.components.LoadingIndicator
import com.madtitan94.transactionsparser.core.domain.model.Category
import com.madtitan94.transactionsparser.core.presentation.ObserveAsEvents
import com.madtitan94.transactionsparser.feature.sessions.presentation.R
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun SessionDetailRoot(
    onBack: () -> Unit,
    viewModel: SessionDetailViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SessionDetailEvent.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(event.message.asString(context))
            }
        }
    }

    SessionDetailScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    state: SessionDetailState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAction: (SessionDetailAction) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.fileName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            state.isLoading -> LoadingIndicator(Modifier.padding(padding))
            state.groups.isEmpty() -> EmptyState(
                title = stringResource(R.string.session_no_transactions),
                description = "",
                modifier = Modifier.padding(padding)
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "header") {
                    SessionHeader(state)
                }

                if (state.suggestedCount > 0 && !state.isReadOnly) {
                    item(key = "suggestions") {
                        SuggestionsBanner(
                            count = state.suggestedCount,
                            onConfirmAll = { onAction(SessionDetailAction.OnConfirmAllSuggested) }
                        )
                    }
                }

                items(items = state.groups, key = { it.key }) { group ->
                    PayeeGroupCard(
                        group = group,
                        categories = state.categories,
                        readOnly = state.isReadOnly,
                        onAction = onAction
                    )
                }
            }
        }
    }

    if (state.newCategoryForKey != null) {
        NewCategoryDialog(state = state, onAction = onAction)
    }
}

@Composable
private fun SessionHeader(state: SessionDetailState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "${state.sourceLabel} • ${state.statusLabel}",
                style = MaterialTheme.typography.titleSmall
            )
            state.periodLabel?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.session_payees_mapped, state.mappedGroups, state.totalGroups),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SuggestionsBanner(count: Int, onConfirmAll: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.session_suggested_banner, count),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onConfirmAll) {
                Text(stringResource(R.string.session_confirm_all))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayeeGroupCard(
    group: PayeeGroupUi,
    categories: List<Category>,
    readOnly: Boolean,
    onAction: (SessionDetailAction) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(group.displayName, style = MaterialTheme.typography.titleSmall)
                    if (group.displayName != group.rawPayee) {
                        Text(
                            text = group.rawPayee,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (group.status == MappingStatus.SAVED) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.session_payee_saved),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.session_amounts, group.amountsLabel),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(
                    R.string.session_total_and_count,
                    group.totalLabel,
                    group.transactionCount
                ),
                style = MaterialTheme.typography.bodySmall
            )
            if (group.timesLabel.isNotBlank()) {
                Text(
                    text = stringResource(R.string.session_usual_times, group.timesLabel),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (!readOnly && group.status != MappingStatus.SAVED) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = group.aliasInput,
                    onValueChange = { onAction(SessionDetailAction.OnAliasChange(group.key, it)) },
                    label = { Text(stringResource(R.string.session_alias_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                CategoryDropdown(
                    categories = categories,
                    selectedCategoryId = group.selectedCategoryId,
                    onSelect = { onAction(SessionDetailAction.OnCategorySelect(group.key, it)) },
                    onAddNew = { onAction(SessionDetailAction.OnAddCategoryClick(group.key)) }
                )

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onAction(SessionDetailAction.OnSaveClick(group.key)) },
                    enabled = group.canSave,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            if (group.status == MappingStatus.SUGGESTED) {
                                R.string.session_confirm_mapping
                            } else {
                                R.string.session_save_mapping
                            }
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    categories: List<Category>,
    selectedCategoryId: Long?,
    onSelect: (Long) -> Unit,
    onAddNew: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = categories.find { it.id == selectedCategoryId }?.name
        ?: stringResource(R.string.session_category_placeholder)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.session_category_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onSelect(category.id)
                        expanded = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.session_new_category)) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = {
                    expanded = false
                    onAddNew()
                }
            )
        }
    }
}

@Composable
private fun NewCategoryDialog(
    state: SessionDetailState,
    onAction: (SessionDetailAction) -> Unit
) {
    Dialog(onDismissRequest = { onAction(SessionDetailAction.OnDismissNewCategory) }) {
        Card {
            Column(Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.session_new_category_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.newCategoryName,
                    onValueChange = { onAction(SessionDetailAction.OnNewCategoryNameChange(it)) },
                    label = { Text(stringResource(R.string.session_category_name_label)) },
                    isError = state.newCategoryError != null,
                    supportingText = state.newCategoryError?.let { { Text(it.asString()) } },
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onAction(SessionDetailAction.OnDismissNewCategory) }) {
                        Text(stringResource(R.string.session_cancel))
                    }
                    TextButton(onClick = { onAction(SessionDetailAction.OnCreateCategory) }) {
                        Text(stringResource(R.string.session_create))
                    }
                }
            }
        }
    }
}
