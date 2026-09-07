package com.madtitan94.transactionsparser.feature.sessions.presentation.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madtitan94.transactionsparser.core.designsystem.components.EmptyState
import com.madtitan94.transactionsparser.core.designsystem.components.ListRow
import com.madtitan94.transactionsparser.feature.sessions.presentation.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun TransactionSearchRoot(
    onBack: () -> Unit,
    onOpenPayee: (normalizedPayee: String, rawPayee: String) -> Unit,
    viewModel: TransactionSearchViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    TransactionSearchScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        onOpenPayee = onOpenPayee
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionSearchScreen(
    state: TransactionSearchState,
    onAction: (TransactionSearchAction) -> Unit,
    onBack: () -> Unit,
    onOpenPayee: (normalizedPayee: String, rawPayee: String) -> Unit
) {
    // The screen exists only to be typed into, so it takes the caret on arrival rather than making
    // the user tap a field they navigated here specifically to use.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.search_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onAction(TransactionSearchAction.OnQueryChange(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    // The spinner takes the trailing slot while a query is in flight, so the one
                    // place the user is already looking is where they learn the list is being
                    // replaced. Clear returns the moment it settles.
                    if (state.isSearching) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onAction(TransactionSearchAction.OnClearQuery) }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.search_clear)
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )

            when {
                state.isIdle -> EmptyState(
                    title = stringResource(R.string.search_idle_title),
                    description = stringResource(R.string.search_idle_body)
                )

                state.isEmptyResult -> EmptyState(
                    title = stringResource(R.string.search_none_title),
                    description = stringResource(R.string.search_none_body)
                )

                else -> ResultList(state = state, onOpenPayee = onOpenPayee)
            }
        }
    }
}

@Composable
private fun ResultList(
    state: TransactionSearchState,
    onOpenPayee: (normalizedPayee: String, rawPayee: String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item(key = "count") {
            // Stated before the rows, not after them. A cap the user meets at the bottom of a long
            // scroll has already cost them the scroll; stated here it is a prompt to type more.
            Text(
                text = if (state.isTruncated) {
                    stringResource(
                        R.string.search_truncated,
                        state.rows.size,
                        state.totalCount
                    )
                } else {
                    pluralStringResource(
                        R.plurals.search_count,
                        state.totalCount,
                        state.totalCount
                    )
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        items(state.rows, key = { it.id }) { row ->
            SearchResultRow(row = row, onOpenPayee = onOpenPayee)
            HorizontalDivider()
        }
    }
}

@Composable
private fun SearchResultRow(
    row: SearchRowUi,
    onOpenPayee: (normalizedPayee: String, rawPayee: String) -> Unit
) {
    ListRow(
        modifier = Modifier.clickable { onOpenPayee(row.normalizedName, row.statementName) },
        title = row.displayName,
        // Date and category on one supporting line: the date is what identifies the charge among
        // the payee's other ones, and the category is what explains why it turned up in a search
        // the user ran for something else.
        supporting = listOfNotNull(row.dateLabel, row.categoryName).joinToString(" · "),
        value = row.amountLabel,
        dimmed = row.isExcluded,
        badge = if (row.isDuplicate) {
            {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(R.string.payee_row_duplicate),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            null
        }
    )
}
