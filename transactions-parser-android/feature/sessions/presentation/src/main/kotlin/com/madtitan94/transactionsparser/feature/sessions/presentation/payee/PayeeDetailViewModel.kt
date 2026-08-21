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
import com.madtitan94.transactionsparser.core.domain.util.onSuccess
import com.madtitan94.transactionsparser.core.presentation.UiText
import com.madtitan94.transactionsparser.core.presentation.formatPaise
import com.madtitan94.transactionsparser.core.presentation.formatStatementDate
import com.madtitan94.transactionsparser.core.presentation.formatStatementTime
import com.madtitan94.transactionsparser.core.presentation.toUiText
import com.madtitan94.transactionsparser.feature.sessions.domain.AliasSuggester
import com.madtitan94.transactionsparser.feature.sessions.domain.MappingDecider
import com.madtitan94.transactionsparser.feature.sessions.domain.MappingDecision
import com.madtitan94.transactionsparser.feature.sessions.presentation.R
import com.madtitan94.transactionsparser.feature.sessions.presentation.components.AliasSuggestionUi
import com.madtitan94.transactionsparser.feature.sessions.presentation.components.MergePrompt
import com.madtitan94.transactionsparser.feature.sessions.presentation.navigation.PayeeDetailRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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

/** One statement name this payee answers to, as the linked-identifiers row shows it. */
data class IdentifierChipUi(val normalizedName: String, val rawName: String)

data class PayeeDetailState(
    val isLoading: Boolean = true,
    val rawPayee: String = "",
    val displayName: String = "",
    val isMapped: Boolean = false,
    /** Null while this statement name is unmapped — there is no payee row yet. */
    val payeeId: Long? = null,
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
    val monthTotals: Map<Long, PeriodTotal> = emptyMap(),
    /** Every statement name this payee answers to. One entry, or none, is the ordinary case. */
    val linkedIdentifiers: List<IdentifierChipUi> = emptyList(),
    /** Normalized name the history is narrowed to, or null for the whole payee. */
    val identifierFilter: String? = null,
    val aliasSuggestions: List<AliasSuggestionUi> = emptyList(),
    /**
     * Set by picking a suggestion: saving then links this name to that payee instead of creating
     * one. Null means the alias was typed freehand and saving creates or updates its own payee.
     */
    val linkTargetId: Long? = null,
    val mergePrompt: MergePrompt? = null
) {
    val canSave: Boolean
        get() = aliasInput.isNotBlank() && selectedCategoryId != null && !isSaving

    /** One name is just this payee's name; the section only earns its space beyond that. */
    val showsLinkedIdentifiers: Boolean
        get() = linkedIdentifiers.size > 1
}

sealed interface PayeeDetailAction {
    data class OnAliasChange(val alias: String) : PayeeDetailAction
    data class OnCategorySelect(val categoryId: Long) : PayeeDetailAction
    data object OnSaveMapping : PayeeDetailAction
    /** Explicit target, not a flip — the row shows which state it is in. */
    data class OnSetExcluded(val id: Long, val isExcluded: Boolean) : PayeeDetailAction
    /** Null narrows nothing: the history goes back to every name the payee owns. */
    data class OnFilterIdentifier(val normalizedName: String?) : PayeeDetailAction
    data class OnSuggestionPick(val payeeId: Long) : PayeeDetailAction
    /** Answers to the same-name prompt. */
    data object OnConfirmMerge : PayeeDetailAction
    data object OnKeepSeparate : PayeeDetailAction
    data object OnDismissMergePrompt : PayeeDetailAction
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
     * The identifier the history is narrowed to, or null for the whole payee. Kept as its own
     * flow rather than read off the state so the paged query and the header subtotals re-run from
     * the same signal — a filtered list under an unfiltered total would be worse than no filter.
     */
    private val identifierFilter = MutableStateFlow<String?>(null)

    /**
     * `cachedIn` keeps loaded pages across configuration changes, so a rotation doesn't refetch
     * from page one and throw away the reader's scroll position.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: kotlinx.coroutines.flow.Flow<PagingData<TransactionRowUi>> =
        identifierFilter
            .flatMapLatest { selected ->
                transactions.observePagedByPayee(
                    normalizedPayee = selected ?: normalizedPayee,
                    includeLinkedNames = selected == null
                )
            }
            .map { paging -> paging.map { it.toRowUi() } }
            .cachedIn(viewModelScope)

    init {
        observeFilter()
        observeHeader()
        observeLinkedIdentifiers()
        observeAliasSuggestions()
    }

    /** The flow drives the queries; the state field is only its reflection for the UI. */
    private fun observeFilter() {
        viewModelScope.launch {
            identifierFilter.collect { selected ->
                _state.update { it.copy(identifierFilter = selected) }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeHeader() {
        viewModelScope.launch {
            identifierFilter.flatMapLatest { selected ->
                val name = selected ?: normalizedPayee
                val includeLinked = selected == null
                combine(
                    payees.observeByNormalizedName(normalizedPayee),
                    transactions.observePayeeTotals(name, includeLinked),
                    transactions.observePayeeDayTotals(name, includeLinked),
                    transactions.observePayeeMonthTotals(name, includeLinked),
                    categories.observeAll().combine(edits) { cats, edit -> cats to edit }
                ) { payee, totals, days, months, (cats, edit) ->
                    Header(payee, totals, days, months, cats, edit)
                }
            }.collect { header -> _state.update { it.merge(header) } }
        }
    }

    private fun observeLinkedIdentifiers() {
        viewModelScope.launch {
            payees.observeLinkedIdentifiers(normalizedPayee).collect { linked ->
                _state.update { current ->
                    current.copy(
                        linkedIdentifiers = linked.map {
                            IdentifierChipUi(it.normalizedName, it.rawName)
                        }
                    )
                }
                // A merge elsewhere can take the filtered name away. Drop a filter that matches
                // nothing rather than leave an empty history with no visible way back out of it.
                identifierFilter.update { selected ->
                    selected?.takeIf { name -> linked.any { it.normalizedName == name } }
                }
            }
        }
    }

    /**
     * Suggestions follow what the user has typed, not the alias already saved — a mapped payee
     * would otherwise open with its own name suggested back at it.
     */
    private fun observeAliasSuggestions() {
        viewModelScope.launch {
            combine(payees.observeAll(), edits) { all, edit -> all to edit.alias.orEmpty() }
                .collect { (all, typed) ->
                    val suggestions = AliasSuggester.suggest(all, typed)
                        .filter { it.id != _state.value.payeeId }
                        .map { AliasSuggestionUi(it.id, it.alias, it.categoryId) }
                    _state.update { it.copy(aliasSuggestions = suggestions) }
                }
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
        payeeId = header.payee?.id,
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
            // Typing after picking a suggestion means the pick no longer stands.
            is PayeeDetailAction.OnAliasChange -> {
                edits.update { it.copy(alias = action.alias) }
                _state.update { it.copy(linkTargetId = null) }
            }
            is PayeeDetailAction.OnCategorySelect -> edits.update { it.copy(categoryId = action.categoryId) }
            PayeeDetailAction.OnSaveMapping -> saveMapping()
            is PayeeDetailAction.OnSetExcluded -> setExcluded(action.id, action.isExcluded)
            is PayeeDetailAction.OnFilterIdentifier -> identifierFilter.value = action.normalizedName
            is PayeeDetailAction.OnSuggestionPick -> pickSuggestion(action.payeeId)
            PayeeDetailAction.OnConfirmMerge -> confirmMerge()
            PayeeDetailAction.OnKeepSeparate -> keepSeparate()
            PayeeDetailAction.OnDismissMergePrompt -> _state.update { it.copy(mergePrompt = null) }
        }
    }

    /** Taking a suggestion adopts that payee's category too — the point is to become them. */
    private fun pickSuggestion(payeeId: Long) {
        val picked = _state.value.aliasSuggestions.find { it.payeeId == payeeId } ?: return
        edits.update { it.copy(alias = picked.alias, categoryId = picked.categoryId) }
        _state.update { it.copy(linkTargetId = payeeId) }
    }

    /**
     * A picked suggestion links straight through — the user chose an existing payee by name, and
     * asking them to confirm the merge they just asked for is a dialog with only one answer.
     * A typed alias that happens to be taken is the ambiguous case, and that is what prompts.
     */
    private fun saveMapping() {
        val current = _state.value
        val alias = current.aliasInput.trim()
        val categoryId = current.selectedCategoryId
        if (alias.isBlank() || categoryId == null || current.isSaving) return

        viewModelScope.launch {
            // Only looked up when it could matter: a picked suggestion decides on its own.
            val aliasOwner = if (current.linkTargetId == null) {
                (payees.findByAlias(alias) as? Result.Success)?.data
            } else {
                null
            }
            val decision = MappingDecider.decide(
                pickedPayeeId = current.linkTargetId,
                aliasOwner = aliasOwner,
                currentPayeeId = current.payeeId
            )
            when (decision) {
                is MappingDecision.LinkTo -> link(decision.payeeId)
                MappingDecision.SaveOwn -> commitSave(alias, categoryId)
                is MappingDecision.AskAboutSameName -> _state.update {
                    it.copy(mergePrompt = MergePrompt(decision.alias, decision.payeeId))
                }
            }
        }
    }

    private fun confirmMerge() {
        val target = _state.value.mergePrompt?.targetPayeeId ?: return
        _state.update { it.copy(mergePrompt = null) }
        viewModelScope.launch { link(target) }
    }

    private fun keepSeparate() {
        val current = _state.value
        val alias = current.aliasInput.trim()
        val categoryId = current.selectedCategoryId
        _state.update { it.copy(mergePrompt = null) }
        if (alias.isBlank() || categoryId == null) return
        viewModelScope.launch { commitSave(alias, categoryId) }
    }

    private suspend fun commitSave(alias: String, categoryId: Long) {
        _state.update { it.copy(isSaving = true) }
        val saved = payees.saveMapping(
            rawName = _state.value.rawPayee,
            normalizedName = normalizedPayee,
            alias = alias,
            categoryId = categoryId
        )
        when (saved) {
            is Result.Error -> _events.send(PayeeDetailEvent.ShowMessage(saved.error.toUiText()))
            is Result.Success -> _events.send(
                PayeeDetailEvent.ShowMessage(UiText.StringResource(R.string.payee_mapping_saved))
            )
        }
        _state.update { it.copy(isSaving = false) }
    }

    private suspend fun link(targetPayeeId: Long) {
        _state.update { it.copy(isSaving = true) }
        payees.linkToPayee(
            rawName = _state.value.rawPayee,
            normalizedName = normalizedPayee,
            targetPayeeId = targetPayeeId
        )
            .onSuccess {
                _events.send(
                    PayeeDetailEvent.ShowMessage(UiText.StringResource(R.string.payee_linked))
                )
            }
            .onFailure { _events.send(PayeeDetailEvent.ShowMessage(it.toUiText())) }
        _state.update { it.copy(isSaving = false, linkTargetId = null) }
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
