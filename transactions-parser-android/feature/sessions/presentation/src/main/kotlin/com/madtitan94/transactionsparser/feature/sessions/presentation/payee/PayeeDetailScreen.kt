package com.madtitan94.transactionsparser.feature.sessions.presentation.payee

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.madtitan94.transactionsparser.core.designsystem.components.ListRow
import com.madtitan94.transactionsparser.core.designsystem.components.LoadingIndicator
import com.madtitan94.transactionsparser.core.designsystem.components.SectionHeader
import com.madtitan94.transactionsparser.core.presentation.ObserveAsEvents
import com.madtitan94.transactionsparser.core.presentation.formatPaise
import com.madtitan94.transactionsparser.core.presentation.formatStatementDayHeader
import com.madtitan94.transactionsparser.core.presentation.formatStatementMonth
import com.madtitan94.transactionsparser.feature.sessions.presentation.R
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun PayeeDetailRoot(
    onBack: () -> Unit,
    viewModel: PayeeDetailViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rows = viewModel.rows.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is PayeeDetailEvent.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(event.message.asString(context))
            }
        }
    }

    PayeeDetailScreen(
        state = state,
        rows = rows,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayeeDetailScreen(
    state: PayeeDetailState,
    rows: LazyPagingItems<TransactionRowUi>,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAction: (PayeeDetailAction) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.displayName.ifBlank { state.rawPayee }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.payee_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.isLoading) {
            LoadingIndicator(Modifier.padding(padding))
            return@Scaffold
        }

        val listState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            state = listState
        ) {
            item(key = "header") {
                PayeeHeader(state)
            }

            if (!state.isMapped) {
                item(key = "mapping") {
                    MappingEditor(state = state, onAction = onAction)
                }
            }

            transactionRows(state = state, rows = rows, onAction = onAction)
        }
    }
}

/** A run of loaded rows that share a day, as a half-open index range into the paged list. */
private data class DayBlock(val dayStartMillis: Long, val from: Int, val until: Int)

/**
 * Splits the loaded rows into day runs so each day's header can be a list item of its own —
 * which is what `stickyHeader` requires, and therefore what lets the day header pin while its
 * rows scroll past it.
 *
 * Uses `peek` rather than indexing so building the structure never triggers a page load: the
 * shape of the list is decided from what is already there, and loading stays driven by what the
 * reader actually scrolls to. Placeholders are off for this pager, so every index below
 * `itemCount` is a real row.
 */
private fun dayBlocks(rows: LazyPagingItems<TransactionRowUi>): List<DayBlock> = buildList {
    var start = 0
    while (start < rows.itemCount) {
        val day = rows.peek(start)?.dayStartMillis ?: break
        var end = start + 1
        while (end < rows.itemCount && rows.peek(end)?.dayStartMillis == day) end++
        add(DayBlock(day, start, end))
        start = end
    }
}

/**
 * Emits the paged rows with a pinned day header over each run, and a month header wherever the
 * run of days crosses into a new month as the reader scrolls into older data.
 *
 * Only the day header is sticky. Pinning both would put them in competition for the same slot,
 * and the month is the coarser context — it reads correctly as a divider between sections rather
 * than as something that follows you down the list.
 *
 * Subtotals come from [PayeeDetailState.dayTotals] / [PayeeDetailState.monthTotals] rather than
 * from the rows on screen: a day can straddle a page boundary, and a header that summed only the
 * loaded part would show a figure that changes while you scroll.
 */
private fun LazyListScope.transactionRows(
    state: PayeeDetailState,
    rows: LazyPagingItems<TransactionRowUi>,
    onAction: (PayeeDetailAction) -> Unit
) {
    val blocks = dayBlocks(rows)

    blocks.forEachIndexed { index, block ->
        val monthStart = monthStartOf(block.dayStartMillis)
        val previousMonth = blocks.getOrNull(index - 1)?.let { monthStartOf(it.dayStartMillis) }

        if (previousMonth != monthStart) {
            state.monthTotals[monthStart]?.let { month ->
                item(key = "month-$monthStart") {
                    SectionHeader(
                        title = formatStatementMonth(block.dayStartMillis),
                        trailing = formatPaise(month.countedTotalPaise),
                        emphasized = true
                    )
                }
            }
        }

        state.dayTotals[block.dayStartMillis]?.let { day ->
            stickyHeader(key = "day-${block.dayStartMillis}") {
                SectionHeader(
                    title = formatStatementDayHeader(block.dayStartMillis),
                    trailing = formatPaise(day.countedTotalPaise)
                )
            }
        }

        items(
            count = block.until - block.from,
            key = { offset -> rows.peek(block.from + offset)?.id ?: (block.from + offset) }
        ) { offset ->
            // Indexing here, not peeking: this is the access that tells Paging the reader has
            // reached this row and that the next page should be fetched.
            val row = rows[block.from + offset] ?: return@items
            TransactionListRow(row = row, onAction = onAction)
            HorizontalDivider()
        }
    }
}

@Composable
private fun TransactionListRow(
    row: TransactionRowUi,
    onAction: (PayeeDetailAction) -> Unit
) {
    ListRow(
        title = row.timeLabel,
        supporting = row.dateLabel,
        value = row.amountLabel,
        dimmed = row.isExcluded,
        badge = if (row.isDuplicate) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.payee_row_duplicate),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            null
        },
        trailing = {
            TextButton(
                onClick = { onAction(PayeeDetailAction.OnSetExcluded(row.id, !row.isExcluded)) }
            ) {
                Text(
                    stringResource(
                        if (row.isExcluded) R.string.payee_row_count else R.string.payee_row_skip
                    )
                )
            }
        }
    )
}

@Composable
private fun PayeeHeader(state: PayeeDetailState) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(text = state.displayName, style = MaterialTheme.typography.titleMedium)
            if (state.isMapped && state.displayName != state.rawPayee) {
                Text(
                    text = state.rawPayee,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.rangeLabel?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(text = state.totalLabel, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = pluralStringResource(
                    R.plurals.payee_counted_transactions,
                    state.countedCount,
                    state.countedCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Only worth saying when some rows are left out; otherwise the counts agree already.
            if (state.transactionCount > state.countedCount) {
                Text(
                    text = stringResource(
                        R.string.payee_not_counted,
                        state.transactionCount - state.countedCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MappingEditor(
    state: PayeeDetailState,
    onAction: (PayeeDetailAction) -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.payee_unmapped_title),
                style = MaterialTheme.typography.titleSmall
            )
            OutlinedTextField(
                value = state.aliasInput,
                onValueChange = { onAction(PayeeDetailAction.OnAliasChange(it)) },
                label = { Text(stringResource(R.string.session_alias_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            CategoryPicker(state = state, onAction = onAction)
            TextButton(
                onClick = { onAction(PayeeDetailAction.OnSaveMapping) },
                enabled = state.canSave,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.session_save_mapping))
            }
        }
    }
}

@Composable
private fun CategoryPicker(
    state: PayeeDetailState,
    onAction: (PayeeDetailAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.categories.find { it.id == state.selectedCategoryId }

    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected?.name ?: stringResource(R.string.session_category_placeholder))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onAction(PayeeDetailAction.OnCategorySelect(category.id))
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Month bucket for a day, matching the `start of month` grouping the subtotal query uses. */
private fun monthStartOf(dayStartMillis: Long): Long {
    val date = com.madtitan94.transactionsparser.core.presentation.statementDateTime(dayStartMillis)
    return java.time.LocalDateTime.of(date.year, date.month, 1, 0, 0)
        .toInstant(java.time.ZoneOffset.UTC)
        .toEpochMilli()
}
