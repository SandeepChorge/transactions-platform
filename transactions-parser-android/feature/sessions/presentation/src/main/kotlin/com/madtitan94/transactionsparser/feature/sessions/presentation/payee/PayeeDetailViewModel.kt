package com.madtitan94.transactionsparser.feature.sessions.presentation.payee

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.madtitan94.transactionsparser.core.domain.datasource.CategoryLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.PayeeLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.TransactionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.model.Category
import com.madtitan94.transactionsparser.core.domain.model.Payee
import com.madtitan94.transactionsparser.core.domain.model.PayeeTotals
import com.madtitan94.transactionsparser.core.domain.model.PeriodTotal
import com.madtitan94.transactionsparser.core.domain.util.Result
import com.madtitan94.transactionsparser.core.domain.util.onFailure
import com.madtitan94.transactionsparser.core.presentation.UiText
import com.madtitan94.transactionsparser.core.presentation.formatPaise
import com.madtitan94.transactionsparser.core.presentation.formatStatementDate
import com.madtitan94.transactionsparser.core.presentation.formatStatementTime
import com.madtitan94.transactionsparser.core.presentation.toUiText
import com.madtitan94.transactionsparser.feature.sessions.presentation.R
import com.madtitan94.transactionsparser.feature.sessions.presentation.navigation.PayeeDetailRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One transaction as the list shows it. */
data class TransactionRowUi(
    val id: Long,
    val amountLabel: String,
    val dateLabel: String,
    val timeLabel: String,
    val dayStartMillis: Long,
    val isDuplicate: Boolean,
    val isExcluded: Boolean
)

data class PayeeDetailState(
    val isLoading: Boolean = true,
    val rawPayee: String = "",
    val displayName: String = "",
    val isMapped: Boolean = false,
    val rangeLabel: String? = null,
    val totalLabel: String = "",
    val countedCount: Int = 0,
    val transactionCount: Int = 0,
    val duplicateCount: Int = 0,
    val aliasInput: String = "",
    val selectedCategoryId: Long? = null,
    val categories: List<Category> = emptyList(),
    val isSaving: Boolean = false,
    /** Day-start millis -> that day's counted subtotal, for the sticky headers. */
    val dayTotals: Map<Long, PeriodTotal> = emptyMap(),
    /** Month-start millis -> that month's counted subtotal. */
    val monthTotals: Map<Long, PeriodTotal> = emptyMap()
) {
    val canSave: Boolean
        get() = aliasInput.isNotBlank() && selectedCategoryId != null && !isSaving
}

sealed interface PayeeDetailAction {
    data class OnAliasChange(val alias: String) : PayeeDetailAction
    data class OnCategorySelect(val categoryId: Long) : PayeeDetailAction
    data object OnSaveMapping : PayeeDetailAction
    /** Explicit target, not a flip — the row shows which state it is in. */
    data class OnSetExcluded(val id: Long, val isExcluded: Boolean) : PayeeDetailAction
}

sealed interface PayeeDetailEvent {
    data class ShowMessage(val message: UiText) : PayeeDetailEvent
}

class PayeeDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val transactions: TransactionLocalDataSource,
    private val payees: PayeeLocalDataSource,
    private val categories: CategoryLocalDataSource
) : ViewModel() {

    private val route = savedStateHandle.toRoute<PayeeDetailRoute>()
    private val normalizedPayee = route.normalizedPayee

    private val _state = MutableStateFlow(PayeeDetailState(rawPayee = route.rawPayee))
    val state = _state.asStateFlow()

    private val _events = Channel<PayeeDetailEvent>()
    val events = _events.receiveAsFlow()

    private val edits = MutableStateFlow(PayeeEdit())

    private data class PayeeEdit(val alias: String? = null, val categoryId: Long? = null)

    /**
     * `cachedIn` keeps loaded pages across configuration changes, so a rotation doesn't refetch
     * from page one and throw away the reader's scroll position.
     */
    val rows: kotlinx.coroutines.flow.Flow<PagingData<TransactionRowUi>> =
        transactions.observePagedByPayee(normalizedPayee)
            .map { paging -> paging.map { it.toRowUi() } }
            .cachedIn(viewModelScope)

    init {
        observeHeader()
    }

    private fun observeHeader() {
        viewModelScope.launch {
            combine(
                payees.observeByNormalizedName(normalizedPayee),
                transactions.observePayeeTotals(normalizedPayee),
                transactions.observePayeeDayTotals(normalizedPayee),
                transactions.observePayeeMonthTotals(normalizedPayee),
                categories.observeAll().combine(edits) { cats, edit -> cats to edit }
            ) { payee, totals, days, months, (cats, edit) ->
                Header(payee, totals, days, months, cats, edit)
            }.collect { header -> _state.update { it.merge(header) } }
        }
    }

    private data class Header(
        val payee: Payee?,
        val totals: PayeeTotals,
        val days: List<PeriodTotal>,
        val months: List<PeriodTotal>,
        val categories: List<Category>,
        val edit: PayeeEdit
    )

    private fun PayeeDetailState.merge(header: Header): PayeeDetailState = copy(
        isLoading = false,
        displayName = header.payee?.alias ?: rawPayee,
        isMapped = header.payee != null,
        rangeLabel = rangeLabel(header.totals),
        totalLabel = formatPaise(header.totals.countedTotalPaise),
        countedCount = header.totals.countedCount,
        transactionCount = header.totals.transactionCount,
        duplicateCount = header.totals.duplicateCount,
        aliasInput = header.edit.alias ?: header.payee?.alias.orEmpty(),
        selectedCategoryId = header.edit.categoryId ?: header.payee?.categoryId,
        categories = header.categories,
        dayTotals = header.days.associateBy { it.startMillis },
        monthTotals = header.months.associateBy { it.startMillis }
    )

    /** A single-day history reads better as one date than as "1 Jun – 1 Jun". */
    private fun rangeLabel(totals: PayeeTotals): String? {
        val first = totals.firstMillis ?: return null
        val last = totals.lastMillis ?: return null
        val from = formatStatementDate(first)
        val to = formatStatementDate(last)
        return if (from == to) from else "$from – $to"
    }

    fun onAction(action: PayeeDetailAction) {
        when (action) {
            is PayeeDetailAction.OnAliasChange -> edits.update { it.copy(alias = action.alias) }
            is PayeeDetailAction.OnCategorySelect -> edits.update { it.copy(categoryId = action.categoryId) }
            PayeeDetailAction.OnSaveMapping -> saveMapping()
            is PayeeDetailAction.OnSetExcluded -> setExcluded(action.id, action.isExcluded)
        }
    }

    private fun saveMapping() {
        val current = _state.value
        val alias = current.aliasInput.trim()
        val categoryId = current.selectedCategoryId
        if (alias.isBlank() || categoryId == null || current.isSaving) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val saved = payees.save(
                Payee(
                    rawName = current.rawPayee,
                    normalizedName = normalizedPayee,
                    alias = alias,
                    categoryId = categoryId
                )
            )
            when (saved) {
                is Result.Error -> _events.send(PayeeDetailEvent.ShowMessage(saved.error.toUiText()))
                is Result.Success -> _events.send(
                    PayeeDetailEvent.ShowMessage(UiText.StringResource(R.string.payee_mapping_saved))
                )
            }
            _state.update { it.copy(isSaving = false) }
        }
    }

    private fun setExcluded(id: Long, isExcluded: Boolean) {
        viewModelScope.launch {
            transactions.setExcluded(id, isExcluded)
                .onFailure { _events.send(PayeeDetailEvent.ShowMessage(it.toUiText())) }
        }
    }
}

private const val DAY_MILLIS = 86_400_000L

private fun com.madtitan94.transactionsparser.core.domain.model.Transaction.toRowUi() = TransactionRowUi(
    id = id,
    amountLabel = formatPaise(amountPaise),
    dateLabel = formatStatementDate(dateTimeUtcMillis),
    timeLabel = formatStatementTime(dateTimeUtcMillis),
    // Same floor the day-subtotal query uses, so a row always finds its own header.
    dayStartMillis = dateTimeUtcMillis / DAY_MILLIS * DAY_MILLIS,
    isDuplicate = isDuplicate,
    isExcluded = isExcluded
)
