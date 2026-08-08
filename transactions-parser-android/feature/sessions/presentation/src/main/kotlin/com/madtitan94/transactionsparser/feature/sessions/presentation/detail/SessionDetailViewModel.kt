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
import com.madtitan94.transactionsparser.feature.sessions.domain.PayeeGroup
import com.madtitan94.transactionsparser.feature.sessions.domain.PayeeGrouper
import com.madtitan94.transactionsparser.feature.sessions.presentation.R
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

data class PayeeEdit(val alias: String? = null, val categoryId: Long? = null)

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
    val isSaving: Boolean
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
    val newCategoryForKey: String? = null,
    val newCategoryName: String = "",
    val newCategoryError: UiText? = null
)

sealed interface SessionDetailAction {
    data class OnAliasChange(val key: String, val alias: String) : SessionDetailAction
    data class OnCategorySelect(val key: String, val categoryId: Long) : SessionDetailAction
    data class OnSaveClick(val key: String) : SessionDetailAction
    data object OnConfirmAllSuggested : SessionDetailAction
    data class OnAddCategoryClick(val key: String) : SessionDetailAction
    data class OnNewCategoryNameChange(val name: String) : SessionDetailAction
    data object OnCreateCategory : SessionDetailAction
    data object OnDismissNewCategory : SessionDetailAction
}

sealed interface SessionDetailEvent {
    data class ShowMessage(val message: UiText) : SessionDetailEvent
}

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
                payees.observeAll(),
                categories.observeAll(),
                edits,
                savingKeys
            ) { txns, allPayees, cats, currentEdits, saving ->
                val knownByName = allPayees.associateBy(Payee::normalizedName)
                val groups = PayeeGrouper.group(txns, knownByName)
                val groupUis = groups.map { it.toUi(currentEdits[it.normalizedPayee], saving) }
                Triple(groupUis, cats, groups.count { it.knownPayee != null && !it.isAssigned })
            }.collect { (groupUis, cats, suggested) ->
                _state.update { current ->
                    current.copy(
                        isLoading = false,
                        groups = groupUis,
                        categories = cats,
                        totalGroups = groupUis.size,
                        mappedGroups = groupUis.count { it.status == MappingStatus.SAVED },
                        suggestedCount = suggested
                    )
                }
            }
        }
    }

    fun onAction(action: SessionDetailAction) {
        when (action) {
            is SessionDetailAction.OnAliasChange -> updateEdit(action.key) { it.copy(alias = action.alias) }
            is SessionDetailAction.OnCategorySelect -> updateEdit(action.key) { it.copy(categoryId = action.categoryId) }
            is SessionDetailAction.OnSaveClick -> save(action.key)
            SessionDetailAction.OnConfirmAllSuggested -> confirmAllSuggested()
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

    private fun save(key: String) {
        val group = _state.value.groups.find { it.key == key } ?: return
        val alias = group.aliasInput.trim()
        val categoryId = group.selectedCategoryId
        if (alias.isBlank() || categoryId == null) return

        viewModelScope.launch {
            savingKeys.update { it + key }
            val saveResult = payees.save(
                Payee(
                    rawName = group.rawPayee,
                    normalizedName = key,
                    alias = alias,
                    categoryId = categoryId
                )
            )
            when (saveResult) {
                is Result.Error -> {
                    _events.send(SessionDetailEvent.ShowMessage(saveResult.error.toUiText()))
                }
                is Result.Success -> {
                    transactions.assignPayee(sessionId, key, saveResult.data)
                        .onFailure { _events.send(SessionDetailEvent.ShowMessage(it.toUiText())) }
                        .onSuccess { completeSessionIfFullyMapped() }
                }
            }
            savingKeys.update { it - key }
        }
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

    private fun PayeeGroup.toUi(edit: PayeeEdit?, saving: Set<String>): PayeeGroupUi {
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
            isSaving = normalizedPayee in saving
        )
    }
}
