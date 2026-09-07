package com.madtitan94.transactionsparser.feature.sessions.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madtitan94.transactionsparser.core.domain.datasource.TransactionSearchLocalDataSource
import com.madtitan94.transactionsparser.core.domain.model.SearchQuery
import com.madtitan94.transactionsparser.core.domain.model.TransactionSearchPage
import com.madtitan94.transactionsparser.core.domain.model.TransactionSearchResult
import com.madtitan94.transactionsparser.core.domain.model.TransactionType
import com.madtitan94.transactionsparser.core.presentation.formatPaise
import com.madtitan94.transactionsparser.core.presentation.formatStatementDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * How long the box waits after the last keystroke before it asks the database anything.
 *
 * 250ms is the usual figure for feeling instant while still collapsing a typed word into one query.
 * It matters more here than on the payee directory, which filters a list already in memory: this
 * runs a scan per query, and a seven-letter payee typed at speed would otherwise be seven scans of
 * which six are thrown away.
 */
private const val QUERY_DEBOUNCE_MILLIS = 250L

/** One search hit, formatted. */
data class SearchRowUi(
    val id: Long,
    val displayName: String,
    /** The key `PayeeDetailRoute` opens on, so a hit can be followed to the payee's history. */
    val normalizedName: String,
    val statementName: String,
    val dateLabel: String,
    val amountLabel: String,
    val categoryName: String?,
    val isCredit: Boolean,
    /** Dimmed, not hidden: the search is how the user reaches a row to un-exclude it. */
    val isExcluded: Boolean,
    val isDuplicate: Boolean
)

data class TransactionSearchState(
    val query: String = "",
    /**
     * True only while a query that will actually run is in flight.
     *
     * Kept separate from "results are empty" because the two look identical on screen for a moment
     * and mean opposite things — a spinner that turns into "no matches" reads as an answer, while
     * "no matches" that turns into rows reads as a bug.
     */
    val isSearching: Boolean = false,
    val rows: List<SearchRowUi> = emptyList(),
    val totalCount: Int = 0,
    val isTruncated: Boolean = false
) {
    /** Nothing typed yet — the screen shows what can be searched rather than an empty list. */
    val isIdle: Boolean get() = SearchQuery.parse(query).isBlank

    val isEmptyResult: Boolean get() = !isIdle && !isSearching && rows.isEmpty()
}

sealed interface TransactionSearchAction {
    data class OnQueryChange(val query: String) : TransactionSearchAction
    data object OnClearQuery : TransactionSearchAction
}

/**
 * Global search over every transaction the account holds.
 *
 * The query runs in SQL rather than over a loaded list, which is the opposite of what the payee
 * directory does and for a stated reason: the directory is a roster the app already holds in
 * memory, while this ranges over every row of every statement, and those were never all loaded.
 *
 * Debounced, then `flatMapLatest`, so a query in flight is cancelled the moment the next character
 * arrives. Without the switch, a slow broad query started at "s" could deliver its rows after the
 * fast narrow one started at "swiggy" and leave the screen showing results for a query the user has
 * already typed past.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TransactionSearchViewModel(
    private val search: TransactionSearchLocalDataSource
) : ViewModel() {

    private val query = MutableStateFlow("")

    /**
     * A page together with the text it answers, so a stale answer can be recognised as stale.
     *
     * The text has to travel with the result. Comparing the page itself against an empty one cannot
     * distinguish "this query has not run yet" from "this query ran and matched nothing", and those
     * are the two states the screen most needs to keep apart.
     */
    private val answers = query
        .debounce(QUERY_DEBOUNCE_MILLIS)
        .flatMapLatest { typed ->
            search.observeSearch(SearchQuery.parse(typed)).map { page -> Answer(typed, page) }
        }

    val state: StateFlow<TransactionSearchState> =
        combine(query, answers) { typed, answer ->
            val parsed = SearchQuery.parse(typed)
            // The answer on hand is for an earlier query — the user has typed on since it was asked.
            val isStale = answer.forQuery != typed
            TransactionSearchState(
                query = typed,
                isSearching = !parsed.isBlank && isStale,
                // Stale rows stay on screen rather than blanking. The spinner already says they are
                // being replaced, and clearing the list per keystroke makes a search that is working
                // look like one that keeps failing.
                rows = answer.page.results.map { it.toRowUi() },
                totalCount = answer.page.totalCount,
                isTruncated = answer.page.isTruncated
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TransactionSearchState()
        )

    private data class Answer(val forQuery: String, val page: TransactionSearchPage)

    fun onAction(action: TransactionSearchAction) {
        when (action) {
            is TransactionSearchAction.OnQueryChange -> query.value = action.query
            TransactionSearchAction.OnClearQuery -> query.value = ""
        }
    }

    private fun TransactionSearchResult.toRowUi() = SearchRowUi(
        id = id,
        displayName = label,
        normalizedName = normalizedName,
        statementName = statementName,
        dateLabel = formatStatementDate(dateTimeUtcMillis),
        amountLabel = formatPaise(amountPaise),
        categoryName = categoryName,
        isCredit = type == TransactionType.CREDIT,
        isExcluded = isExcluded,
        isDuplicate = isDuplicate
    )
}
