package com.madtitan94.transactionsparser.feature.sessions.presentation.payee

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madtitan94.transactionsparser.core.domain.datasource.PayeeLocalDataSource
import com.madtitan94.transactionsparser.core.domain.model.PayeeDirectoryEntry
import com.madtitan94.transactionsparser.core.presentation.formatPaise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One payee as the directory lists them. */
data class PayeeDirectoryRowUi(
    val normalizedName: String,
    val statementName: String,
    val displayName: String,
    /** The category when mapped; null is what the row reads to draw itself as unmapped. */
    val categoryName: String?,
    /** Null unless this payee answers to more than one statement name. */
    val otherNameCount: Int?,
    val totalLabel: String,
    val transactionCount: Int
) {
    val isUnmapped: Boolean get() = categoryName == null
}

/** What the chip row narrows the list to. */
enum class PayeeDirectoryFilter { ALL, UNMAPPED, MAPPED }

data class PayeeDirectoryState(
    val isLoading: Boolean = true,
    val query: String = "",
    val filter: PayeeDirectoryFilter = PayeeDirectoryFilter.ALL,
    val rows: List<PayeeDirectoryRowUi> = emptyList(),
    /** Counts for the whole directory, not the filtered view — they label the chips. */
    val totalCount: Int = 0,
    val unmappedCount: Int = 0
) {
    val mappedCount: Int get() = totalCount - unmappedCount

    /** An account with no payees at all is a different message from a search that found none. */
    val isEmptyAccount: Boolean get() = !isLoading && totalCount == 0
    val isEmptyResult: Boolean get() = !isLoading && totalCount > 0 && rows.isEmpty()
}

sealed interface PayeeDirectoryAction {
    data class OnQueryChange(val query: String) : PayeeDirectoryAction
    data class OnFilterChange(val filter: PayeeDirectoryFilter) : PayeeDirectoryAction
}

/**
 * The Payees tab: everyone the account knows about, searchable and filterable.
 *
 * **Search and filtering happen here rather than in SQL, and that is deliberate.** The directory is
 * one query over the whole account — a roster, not a page — so it is already in memory, and
 * re-querying on each keystroke would trade an allocation for a database round trip and a debounce
 * to hide it. The transaction search in a later phase is the opposite case and will be a query:
 * it ranges over tens of thousands of rows that were never all loaded.
 *
 * Matching is on both the alias and the statement name, so a payee found by the name the bank
 * printed is found by the name the user gave them too, and vice versa — searching "swiggy" has to
 * find them when they have been renamed "Dinner", or the search is only useful to someone who
 * already remembers what they called them.
 */
class PayeeDirectoryViewModel(
    payees: PayeeLocalDataSource
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(PayeeDirectoryFilter.ALL)

    val state: kotlinx.coroutines.flow.StateFlow<PayeeDirectoryState> =
        combine(payees.observeDirectory(), query, filter) { entries, query, filter ->
            val term = query.trim()
            val matching = entries
                .filter { entry ->
                    when (filter) {
                        PayeeDirectoryFilter.ALL -> true
                        PayeeDirectoryFilter.UNMAPPED -> entry.isUnmapped
                        PayeeDirectoryFilter.MAPPED -> !entry.isUnmapped
                    }
                }
                .filter { entry -> term.isEmpty() || entry.matches(term) }

            PayeeDirectoryState(
                isLoading = false,
                query = query,
                filter = filter,
                rows = matching.map { it.toRowUi() },
                totalCount = entries.size,
                unmappedCount = entries.count { it.isUnmapped }
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PayeeDirectoryState()
        )

    fun onAction(action: PayeeDirectoryAction) {
        when (action) {
            is PayeeDirectoryAction.OnQueryChange -> query.value = action.query
            is PayeeDirectoryAction.OnFilterChange -> filter.value = action.filter
        }
    }

    private fun PayeeDirectoryEntry.matches(term: String) =
        label.contains(term, ignoreCase = true) || statementName.contains(term, ignoreCase = true)

    private fun PayeeDirectoryEntry.toRowUi() = PayeeDirectoryRowUi(
        normalizedName = normalizedName,
        statementName = statementName,
        displayName = label,
        categoryName = categoryName,
        // One name is just this payee's name; only the extras are worth a line.
        otherNameCount = (identifierCount - 1).takeIf { it > 0 },
        totalLabel = formatPaise(totalPaise),
        transactionCount = transactionCount
    )
}
