package com.madtitan94.transactionsparser.feature.sessions.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.madtitan94.transactionsparser.core.domain.datasource.CategoryLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.PayeeLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.SessionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.TransactionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.model.Category
import com.madtitan94.transactionsparser.core.domain.model.Payee
import com.madtitan94.transactionsparser.core.domain.model.SessionStatus
import com.madtitan94.transactionsparser.core.domain.util.Result
import com.madtitan94.transactionsparser.core.domain.util.onFailure
import com.madtitan94.transactionsparser.core.domain.util.onSuccess
import com.madtitan94.transactionsparser.core.presentation.UiText
import com.madtitan94.transactionsparser.core.presentation.formatHourOfDay
import com.madtitan94.transactionsparser.core.presentation.formatPaise
import com.madtitan94.transactionsparser.core.presentation.formatStatementDate
import com.madtitan94.transactionsparser.core.presentation.toUiText
import com.madtitan94.transactionsparser.feature.sessions.domain.AliasSuggester
import com.madtitan94.transactionsparser.feature.sessions.domain.DuplicateSelection
import com.madtitan94.transactionsparser.feature.sessions.domain.MappingDecider
import com.madtitan94.transactionsparser.feature.sessions.domain.MappingDecision
import com.madtitan94.transactionsparser.feature.sessions.domain.PayeeGroup
import com.madtitan94.transactionsparser.feature.sessions.domain.PayeeGrouper
import com.madtitan94.transactionsparser.feature.sessions.presentation.R
import com.madtitan94.transactionsparser.feature.sessions.presentation.components.AliasSuggestionUi
import com.madtitan94.transactionsparser.feature.sessions.presentation.components.MergePrompt
import com.madtitan94.transactionsparser.feature.sessions.presentation.history.toLabel
import com.madtitan94.transactionsparser.feature.sessions.presentation.navigation.SessionDetailRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MappingStatus { UNMAPPED, SUGGESTED, SAVED }

/**
 * [linkTargetId] is set by picking a typeahead suggestion: saving then links this statement name
 * to that existing payee rather than creating a second one under the same alias.
 */
data class PayeeEdit(
    val alias: String? = null,
    val categoryId: Long? = null,
    val linkTargetId: Long? = null
)

data class PayeeGroupUi(
    val key: String,
    val rawPayee: String,
    val displayName: String,
    val amountsLabel: String,
    val totalLabel: String,
    val transactionCount: Int,
    val timesLabel: String,
    val aliasInput: String,
    val selectedCategoryId: Long?,
    val status: MappingStatus,
    val isSaving: Boolean,
    /** Flagged repeats in this group; 0 hides the duplicate badge and its control entirely. */
    val duplicateCount: Int = 0,
    val excludedDuplicateCount: Int = 0,
    val duplicateSelection: DuplicateSelection = DuplicateSelection.NONE,
    /** Existing payees matching the alias being typed. Empty until something is typed. */
    val suggestions: List<AliasSuggestionUi> = emptyList()
) {
    val canSave: Boolean
        get() = aliasInput.isNotBlank() && selectedCategoryId != null && !isSaving && status != MappingStatus.SAVED
}

data class SessionDetailState(
    val isLoading: Boolean = true,
    val fileName: String = "",
    val sourceLabel: String = "",
    val periodLabel: String? = null,
    val isReadOnly: Boolean = false,
    val statusLabel: String = "",
    val groups: List<PayeeGroupUi> = emptyList(),
    val categories: List<Category> = emptyList(),
    val mappedGroups: Int = 0,
    val totalGroups: Int = 0,
    val suggestedCount: Int = 0,
    /** Flagged repeats across the whole session, driving the summary banner. */
    val duplicateCount: Int = 0,
    val transactionCount: Int = 0,
    val newCategoryForKey: String? = null,
    val newCategoryName: String = "",
    val newCategoryError: UiText? = null,
    /** The alias collision awaiting an answer, and which group raised it. */
    val mergePrompt: MergePrompt? = null,
    val mergePromptKey: String? = null
)

sealed interface SessionDetailAction {
    data class OnAliasChange(val key: String, val alias: String) : SessionDetailAction
    data class OnCategorySelect(val key: String, val categoryId: Long) : SessionDetailAction
    data class OnSaveClick(val key: String) : SessionDetailAction
    data object OnConfirmAllSuggested : SessionDetailAction
    /**
     * Puts this payee's flagged repeats into the totals, or takes them out. Carries the target
     * state rather than flipping: from a part-excluded group there is no sensible "opposite".
     */
    data class OnSetDuplicatesExcluded(val key: String, val isExcluded: Boolean) : SessionDetailAction
    data class OnAddCategoryClick(val key: String) : SessionDetailAction
    data class OnNewCategoryNameChange(val name: String) : SessionDetailAction
    data object OnCreateCategory : SessionDetailAction
    data object OnDismissNewCategory : SessionDetailAction
    data class OnSuggestionPick(val key: String, val payeeId: Long) : SessionDetailAction
    /** Answers to the same-name prompt. */
    data object OnConfirmMerge : SessionDetailAction
    data object OnKeepSeparate : SessionDetailAction
    data object OnDismissMergePrompt : SessionDetailAction
}

sealed interface SessionDetailEvent {
    data class ShowMessage(val message: UiText) : SessionDetailEvent
}

/** One pass of the group pipeline — named rather than a Triple now that it carries four values. */
private data class GroupsSnapshot(
    val groups: List<PayeeGroupUi>,
    val categories: List<Category>,
    val suggestedCount: Int,
    val duplicateCount: Int,
    val transactionCount: Int
)

class SessionDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val sessions: SessionLocalDataSource,
    private val transactions: TransactionLocalDataSource,
    private val payees: PayeeLocalDataSource,
    private val categories: CategoryLocalDataSource
) : ViewModel() {

    private val sessionId: Long = savedStateHandle.toRoute<SessionDetailRoute>().sessionId

    private val _state = MutableStateFlow(SessionDetailState())
    val state = _state.asStateFlow()

    private val _events = Channel<SessionDetailEvent>()
    val events = _events.receiveAsFlow()

    private val edits = MutableStateFlow<Map<String, PayeeEdit>>(emptyMap())
    private val savingKeys = MutableStateFlow<Set<String>>(emptySet())

    /**
     * The two views of the account's payees the group list needs at once: which statement names
     * are already mapped, and every payee an alias could be suggested from. Paired up here so
     * `observeGroups` stays within `combine`'s five-flow arity.
     *
     * Declared above `init` deliberately: property initialisers run in declaration order, so a
     * flow the init block collects has to exist by the time it runs.
     */
    private val payeePool = combine(
        payees.observeByIdentifier(),
        payees.observeAll()
    ) { byName, all -> byName to all }

    init {
        loadSessionHeader()
        observeGroups()
    }

    private fun loadSessionHeader() {
        viewModelScope.launch {
            val session = (sessions.getById(sessionId) as? Result.Success)?.data ?: return@launch
            _state.update {
                it.copy(
                    fileName = session.fileName,
                    sourceLabel = session.source.toLabel(),
                    statusLabel = session.status.name.lowercase().replaceFirstChar(Char::uppercase),
                    isReadOnly = session.status != SessionStatus.PENDING,
                    periodLabel = session.periodStartMillis?.let { start ->
                        session.periodEndMillis?.let { end ->
                            "${formatStatementDate(start)} – ${formatStatementDate(end)}"
                        }
                    }
                )
            }
        }
    }

    private fun observeGroups() {
        viewModelScope.launch {
            combine(
                transactions.observeBySession(sessionId),
                payeePool,
                categories.observeAll(),
                edits,
                savingKeys
            ) { txns, (knownByName, allPayees), cats, currentEdits, saving ->
                val groups = PayeeGrouper.group(txns, knownByName)
                GroupsSnapshot(
                    groups = groups.map {
                        it.toUi(currentEdits[it.normalizedPayee], saving, allPayees)
                    },
                    categories = cats,
                    suggestedCount = groups.count { it.knownPayee != null && !it.isAssigned },
                    duplicateCount = txns.count { it.isDuplicate },
                    transactionCount = txns.size
                )
            }.collect { snapshot ->
                _state.update { current ->
                    current.copy(
                        isLoading = false,
                        groups = snapshot.groups,
                        categories = snapshot.categories,
                        totalGroups = snapshot.groups.size,
                        mappedGroups = snapshot.groups.count { it.status == MappingStatus.SAVED },
                        suggestedCount = snapshot.suggestedCount,
                        duplicateCount = snapshot.duplicateCount,
                        transactionCount = snapshot.transactionCount
                    )
                }
            }
        }
    }

    fun onAction(action: SessionDetailAction) {
        when (action) {
            // Typing after picking a suggestion means the pick no longer stands.
            is SessionDetailAction.OnAliasChange -> updateEdit(action.key) {
                it.copy(alias = action.alias, linkTargetId = null)
            }
            is SessionDetailAction.OnCategorySelect -> updateEdit(action.key) { it.copy(categoryId = action.categoryId) }
            is SessionDetailAction.OnSaveClick -> save(action.key)
            SessionDetailAction.OnConfirmAllSuggested -> confirmAllSuggested()
            is SessionDetailAction.OnSetDuplicatesExcluded ->
                setDuplicatesExcluded(action.key, action.isExcluded)
            is SessionDetailAction.OnAddCategoryClick -> _state.update {
                it.copy(newCategoryForKey = action.key, newCategoryName = "", newCategoryError = null)
            }
            is SessionDetailAction.OnNewCategoryNameChange -> _state.update {
                it.copy(newCategoryName = action.name, newCategoryError = null)
            }
            SessionDetailAction.OnCreateCategory -> createCategory()
            SessionDetailAction.OnDismissNewCategory -> _state.update {
                it.copy(newCategoryForKey = null, newCategoryName = "", newCategoryError = null)
            }
            is SessionDetailAction.OnSuggestionPick -> pickSuggestion(action.key, action.payeeId)
            SessionDetailAction.OnConfirmMerge -> confirmMerge()
            SessionDetailAction.OnKeepSeparate -> keepSeparate()
            SessionDetailAction.OnDismissMergePrompt -> _state.update {
                it.copy(mergePrompt = null, mergePromptKey = null)
            }
        }
    }

    /** Taking a suggestion adopts that payee's category too — the point is to become them. */
    private fun pickSuggestion(key: String, payeeId: Long) {
        val group = _state.value.groups.find { it.key == key } ?: return
        val picked = group.suggestions.find { it.payeeId == payeeId } ?: return
        updateEdit(key) {
            it.copy(alias = picked.alias, categoryId = picked.categoryId, linkTargetId = payeeId)
        }
    }

    private fun updateEdit(key: String, transform: (PayeeEdit) -> PayeeEdit) {
        edits.update { current ->
            current + (key to transform(current[key] ?: editSeededFromSuggestion(key)))
        }
    }

    /** First edit for a suggested payee starts from its saved alias/category. */
    private fun editSeededFromSuggestion(key: String): PayeeEdit {
        val group = _state.value.groups.find { it.key == key } ?: return PayeeEdit()
        return PayeeEdit(alias = group.aliasInput.ifBlank { null }, categoryId = group.selectedCategoryId)
    }

    /**
     * A picked suggestion links straight through — the user chose an existing payee by name, and
     * confirming the merge they just asked for would be a dialog with one answer. A typed alias
     * that turns out to be taken is the ambiguous case, and that is what raises the prompt.
     */
    private fun save(key: String) {
        val group = _state.value.groups.find { it.key == key } ?: return
        val alias = group.aliasInput.trim()
        val categoryId = group.selectedCategoryId
        if (alias.isBlank() || categoryId == null) return

        val linkTarget = edits.value[key]?.linkTargetId

        viewModelScope.launch {
            // Only looked up when it could matter: a picked suggestion decides on its own.
            val aliasOwner = if (linkTarget == null) {
                (payees.findByAlias(alias) as? Result.Success)?.data
            } else {
                null
            }
            val currentPayeeId = (payees.findByNormalizedName(key) as? Result.Success)?.data?.id
            val decision = MappingDecider.decide(
                pickedPayeeId = linkTarget,
                aliasOwner = aliasOwner,
                currentPayeeId = currentPayeeId
            )
            when (decision) {
                is MappingDecision.LinkTo -> link(key, group.rawPayee, decision.payeeId)
                MappingDecision.SaveOwn -> commitSave(key, group.rawPayee, alias, categoryId)
                is MappingDecision.AskAboutSameName -> _state.update {
                    it.copy(
                        mergePrompt = MergePrompt(decision.alias, decision.payeeId),
                        mergePromptKey = key
                    )
                }
            }
        }
    }

    private fun confirmMerge() {
        val current = _state.value
        val prompt = current.mergePrompt ?: return
        val key = current.mergePromptKey ?: return
        val group = current.groups.find { it.key == key } ?: return
        _state.update { it.copy(mergePrompt = null, mergePromptKey = null) }
        viewModelScope.launch { link(key, group.rawPayee, prompt.targetPayeeId) }
    }

    private fun keepSeparate() {
        val current = _state.value
        val key = current.mergePromptKey ?: return
        val group = current.groups.find { it.key == key } ?: return
        val alias = group.aliasInput.trim()
        val categoryId = group.selectedCategoryId
        _state.update { it.copy(mergePrompt = null, mergePromptKey = null) }
        if (alias.isBlank() || categoryId == null) return
        viewModelScope.launch { commitSave(key, group.rawPayee, alias, categoryId) }
    }

    private suspend fun commitSave(
        key: String,
        rawPayee: String,
        alias: String,
        categoryId: Long
    ) {
        savingKeys.update { it + key }
        when (val saveResult = payees.saveMapping(rawPayee, key, alias, categoryId)) {
            is Result.Error -> {
                _events.send(SessionDetailEvent.ShowMessage(saveResult.error.toUiText()))
            }
            is Result.Success -> assign(key, saveResult.data)
        }
        savingKeys.update { it - key }
    }

    /**
     * Links the name to an existing payee, then assigns this session's rows to it — the same
     * second step a fresh mapping takes, so a merged group counts as mapped straight away rather
     * than waiting for a re-import to notice.
     */
    private suspend fun link(key: String, rawPayee: String, targetPayeeId: Long) {
        savingKeys.update { it + key }
        payees.linkToPayee(rawPayee, key, targetPayeeId)
            .onFailure { _events.send(SessionDetailEvent.ShowMessage(it.toUiText())) }
            .onSuccess {
                // The pick has been acted on; a later save of this group is an ordinary save.
                updateEdit(key) { it.copy(linkTargetId = null) }
                assign(key, targetPayeeId)
            }
        savingKeys.update { it - key }
    }

    private suspend fun assign(key: String, payeeId: Long) {
        transactions.assignPayee(sessionId, key, payeeId)
            .onFailure { _events.send(SessionDetailEvent.ShowMessage(it.toUiText())) }
            .onSuccess { completeSessionIfFullyMapped() }
    }

    private fun confirmAllSuggested() {
        viewModelScope.launch {
            val suggested = _state.value.groups.filter { it.status == MappingStatus.SUGGESTED }
            savingKeys.update { it + suggested.map(PayeeGroupUi::key) }
            suggested.forEach { group ->
                val known = (payees.findByNormalizedName(group.key) as? Result.Success)?.data
                if (known != null) {
                    transactions.assignPayee(sessionId, group.key, known.id)
                }
            }
            savingKeys.update { it - suggested.map(PayeeGroupUi::key).toSet() }
            completeSessionIfFullyMapped()
        }
    }

    private fun setDuplicatesExcluded(key: String, isExcluded: Boolean) {
        val group = _state.value.groups.find { it.key == key } ?: return
        if (group.duplicateCount == 0) return

        viewModelScope.launch {
            transactions.setDuplicatesExcluded(
                sessionId = sessionId,
                normalizedPayee = key,
                isExcluded = isExcluded
            ).onFailure { _events.send(SessionDetailEvent.ShowMessage(it.toUiText())) }
        }
    }

    private suspend fun completeSessionIfFullyMapped() {
        val unmapped = (transactions.unmappedCount(sessionId) as? Result.Success)?.data ?: return
        if (unmapped == 0) {
            sessions.updateStatus(sessionId, SessionStatus.COMPLETED)
            _state.update { it.copy(isReadOnly = true, statusLabel = "Completed") }
            _events.send(
                SessionDetailEvent.ShowMessage(UiText.StringResource(R.string.session_completed))
            )
        }
    }

    private fun createCategory() {
        val current = _state.value
        val key = current.newCategoryForKey ?: return
        val name = current.newCategoryName.trim()
        if (name.isBlank()) {
            _state.update { it.copy(newCategoryError = UiText.StringResource(R.string.category_name_required)) }
            return
        }

        viewModelScope.launch {
            when (val inserted = categories.insert(name)) {
                is Result.Error -> _state.update {
                    it.copy(newCategoryError = inserted.error.toUiText())
                }
                is Result.Success -> {
                    updateEdit(key) { it.copy(categoryId = inserted.data) }
                    _state.update {
                        it.copy(newCategoryForKey = null, newCategoryName = "", newCategoryError = null)
                    }
                }
            }
        }
    }

    private fun PayeeGroup.toUi(
        edit: PayeeEdit?,
        saving: Set<String>,
        allPayees: List<Payee>
    ): PayeeGroupUi {
        val status = when {
            isAssigned -> MappingStatus.SAVED
            knownPayee != null -> MappingStatus.SUGGESTED
            else -> MappingStatus.UNMAPPED
        }
        val amounts = amountsPaise.take(3).joinToString(", ") { formatPaise(it) } +
            if (amountsPaise.size > 3) "…" else ""
        val times = typicalTimes.joinToString(", ") { time ->
            "≈${formatHourOfDay(time.hourOfDay)}" + if (time.count > 1) " (${time.count}×)" else ""
        }

        return PayeeGroupUi(
            key = normalizedPayee,
            rawPayee = rawPayee,
            displayName = knownPayee?.alias?.takeIf { status == MappingStatus.SAVED } ?: rawPayee,
            amountsLabel = amounts,
            totalLabel = formatPaise(totalPaise),
            transactionCount = transactionCount,
            timesLabel = times,
            aliasInput = edit?.alias ?: knownPayee?.alias.orEmpty(),
            selectedCategoryId = edit?.categoryId ?: knownPayee?.categoryId,
            status = status,
            isSaving = normalizedPayee in saving,
            duplicateCount = duplicateCount,
            excludedDuplicateCount = excludedDuplicateCount,
            duplicateSelection = duplicateSelection,
            // Only what the user has typed suggests anything: a suggested or saved group would
            // otherwise open with its own payee offered back to it as a suggestion.
            suggestions = AliasSuggester.suggest(allPayees, edit?.alias.orEmpty())
                .filter { it.id != knownPayee?.id }
                .map { AliasSuggestionUi(it.id, it.alias, it.categoryId) }
        )
    }
}
