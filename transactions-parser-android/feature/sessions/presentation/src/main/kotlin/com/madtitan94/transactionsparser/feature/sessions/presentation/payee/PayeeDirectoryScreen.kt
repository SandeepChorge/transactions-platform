package com.madtitan94.transactionsparser.feature.sessions.presentation.payee

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madtitan94.transactionsparser.core.designsystem.components.EmptyState
import com.madtitan94.transactionsparser.core.designsystem.components.ListRow
import com.madtitan94.transactionsparser.core.designsystem.components.LoadingIndicator
import com.madtitan94.transactionsparser.feature.sessions.presentation.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun PayeeDirectoryRoot(
    onOpenPayee: (normalizedPayee: String, rawPayee: String) -> Unit,
    viewModel: PayeeDirectoryViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PayeeDirectoryScreen(
        state = state,
        onAction = viewModel::onAction,
        onOpenPayee = onOpenPayee
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayeeDirectoryScreen(
    state: PayeeDirectoryState,
    onAction: (PayeeDirectoryAction) -> Unit,
    onOpenPayee: (normalizedPayee: String, rawPayee: String) -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.payees_title)) }) }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onAction(PayeeDirectoryAction.OnQueryChange(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.payees_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onAction(PayeeDirectoryAction.OnQueryChange("")) }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.payees_search_clear)
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )

            // The chips carry counts because the count is the reason to tap one: "Unmapped 14" is
            // a job of a known size, while a bare "Unmapped" is a question the user has to open
            // the filter to answer.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChipFor(state, PayeeDirectoryFilter.ALL, R.string.payees_filter_all, state.totalCount, onAction)
                FilterChipFor(state, PayeeDirectoryFilter.UNMAPPED, R.string.payees_filter_unmapped, state.unmappedCount, onAction)
                FilterChipFor(state, PayeeDirectoryFilter.MAPPED, R.string.payees_filter_mapped, state.mappedCount, onAction)
            }

            when {
                state.isLoading -> LoadingIndicator()
                state.isEmptyAccount -> EmptyState(
                    title = stringResource(R.string.payees_empty_title),
                    description = stringResource(R.string.payees_empty_description)
                )
                state.isEmptyResult -> EmptyState(
                    title = stringResource(R.string.payees_no_results_title),
                    description = stringResource(R.string.payees_no_results_description)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(items = state.rows, key = { it.normalizedName }) { row ->
                        PayeeDirectoryListRow(row = row, onOpenPayee = onOpenPayee)
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun PayeeDirectoryListRow(
    row: PayeeDirectoryRowUi,
    onOpenPayee: (normalizedPayee: String, rawPayee: String) -> Unit
) {
    val supporting = when {
        row.isUnmapped -> stringResource(R.string.payees_row_unmapped)
        row.otherNameCount != null -> stringResource(
            R.string.payees_row_category_and_names,
            row.categoryName.orEmpty(),
            pluralStringResource(
                R.plurals.payees_row_other_names,
                row.otherNameCount,
                row.otherNameCount
            )
        )
        else -> row.categoryName
    }

    ListRow(
        title = row.displayName,
        modifier = Modifier.clickable { onOpenPayee(row.normalizedName, row.statementName) },
        value = row.totalLabel,
        supporting = supporting,
        // A payee with nothing countable is still listed, but is drawn as what it is: a name with
        // no spend behind it yet, rather than a payee the user forgot they had.
        dimmed = row.transactionCount == 0
    )
}

@Composable
private fun FilterChipFor(
    state: PayeeDirectoryState,
    filter: PayeeDirectoryFilter,
    labelRes: Int,
    count: Int,
    onAction: (PayeeDirectoryAction) -> Unit
) {
    FilterChip(
        selected = state.filter == filter,
        onClick = { onAction(PayeeDirectoryAction.OnFilterChange(filter)) },
        label = { Text(stringResource(labelRes, count)) }
    )
}
