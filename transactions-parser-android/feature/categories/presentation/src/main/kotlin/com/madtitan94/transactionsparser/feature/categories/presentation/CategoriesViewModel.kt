package com.madtitan94.transactionsparser.feature.categories.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madtitan94.transactionsparser.core.domain.analytics.AnalyticsEvent
import com.madtitan94.transactionsparser.core.domain.analytics.AnalyticsParams
import com.madtitan94.transactionsparser.core.domain.analytics.AnalyticsTracker
import com.madtitan94.transactionsparser.core.domain.catalog.AdoptSuggestedCategory
import com.madtitan94.transactionsparser.core.domain.catalog.SuggestedCategory
import com.madtitan94.transactionsparser.core.domain.catalog.SuggestedCategorySource
import com.madtitan94.transactionsparser.core.domain.datasource.CategoryLocalDataSource
import com.madtitan94.transactionsparser.core.domain.util.Result
import com.madtitan94.transactionsparser.core.domain.util.onFailure
import com.madtitan94.transactionsparser.core.presentation.UiText
import com.madtitan94.transactionsparser.core.presentation.toUiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoryUi(
    val id: Long,
    val name: String,
    val linkedPayeeCount: Int
) {
    val canDelete: Boolean get() = linkedPayeeCount == 0
}

sealed interface CategoryDialog {
    data class Add(val name: String = "", val error: UiText? = null) : CategoryDialog
    data class Edit(val id: Long, val name: String, val error: UiText? = null) : CategoryDialog
    data class ConfirmDelete(val id: Long, val name: String) : CategoryDialog
}

data class CategoriesState(
    val isLoading: Boolean = true,
    val categories: List<CategoryUi> = emptyList(),
    val suggestions: List<SuggestedCategory> = emptyList(),
    val selectedSuggestionNames: Set<String> = emptySet(),
    val catalogVersion: Int = 0,
    val dialog: CategoryDialog? = null
) {
    /** Suggestions are a stand-in for an empty list, not a second list beside the user's own. */
    val showSuggestions: Boolean get() = categories.isEmpty() && suggestions.isNotEmpty()
}

sealed interface CategoriesAction {
    data object OnAddClick : CategoriesAction
    data class OnEditClick(val id: Long) : CategoriesAction
    data class OnDeleteClick(val id: Long) : CategoriesAction
    data class OnDialogNameChange(val name: String) : CategoriesAction
    data object OnDialogConfirm : CategoriesAction
    data object OnDialogDismiss : CategoriesAction
    data class OnToggleSuggestion(val name: String) : CategoriesAction
    data object OnConfirmSuggestions : CategoriesAction
}

sealed interface CategoriesEvent {
    data class ShowMessage(val message: UiText) : CategoriesEvent
}

class CategoriesViewModel(
    private val categories: CategoryLocalDataSource,
    private val catalog: SuggestedCategorySource,
    private val adopt: AdoptSuggestedCategory,
    private val analytics: AnalyticsTracker
) : ViewModel() {

    private val _state = MutableStateFlow(CategoriesState())
    val state = _state.asStateFlow()

    private val _events = Channel<CategoriesEvent>()
    val events = _events.receiveAsFlow()

    /** Set for the duration of an adopt so a second tap cannot track the same chip twice. */
    private var adopting = false

    init {
        viewModelScope.launch {
            combine(
                categories.observeAll(),
                categories.observeLinkedPayeeCounts(),
                catalog.observe()
            ) { all, counts, suggestions ->
                Triple(all, counts, suggestions)
            }.collect { (all, counts, suggestions) ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        categories = all.map { category ->
                            CategoryUi(
                                id = category.id,
                                name = category.name,
                                linkedPayeeCount = counts[category.id] ?: 0
                            )
                        },
                        suggestions = suggestions.categories,
                        catalogVersion = suggestions.version,
                        selectedSuggestionNames = it.selectedSuggestionNames.intersect(
                            suggestions.categories.map { suggestion -> suggestion.name }.toSet()
                        )
                    )
                }
            }
        }
    }

    fun onAction(action: CategoriesAction) {
        when (action) {
            CategoriesAction.OnAddClick -> _state.update { it.copy(dialog = CategoryDialog.Add()) }
            is CategoriesAction.OnEditClick -> {
                val category = _state.value.categories.find { it.id == action.id } ?: return
                _state.update { it.copy(dialog = CategoryDialog.Edit(category.id, category.name)) }
            }
            is CategoriesAction.OnDeleteClick -> {
                val category = _state.value.categories.find { it.id == action.id } ?: return
                if (!category.canDelete) {
                    viewModelScope.launch {
                        _events.send(
                            CategoriesEvent.ShowMessage(
                                UiText.StringResource(
                                    R.string.category_delete_blocked,
                                    arrayOf(category.linkedPayeeCount)
                                )
                            )
                        )
                    }
                    return
                }
                _state.update { it.copy(dialog = CategoryDialog.ConfirmDelete(category.id, category.name)) }
            }
            is CategoriesAction.OnDialogNameChange -> _state.update { current ->
                current.copy(
                    dialog = when (val dialog = current.dialog) {
                        is CategoryDialog.Add -> dialog.copy(name = action.name, error = null)
                        is CategoryDialog.Edit -> dialog.copy(name = action.name, error = null)
                        else -> dialog
                    }
                )
            }
            CategoriesAction.OnDialogConfirm -> confirmDialog()
            CategoriesAction.OnDialogDismiss -> _state.update { it.copy(dialog = null) }
            is CategoriesAction.OnToggleSuggestion -> toggleSuggestion(action.name)
            CategoriesAction.OnConfirmSuggestions -> confirmSuggestions()
        }
    }

    private fun toggleSuggestion(name: String) {
        if (_state.value.categories.isNotEmpty()) return
        if (_state.value.suggestions.none { it.name == name }) return
        _state.update { current ->
            val selected = current.selectedSuggestionNames
            current.copy(
                selectedSuggestionNames = if (name in selected) selected - name else selected + name
            )
        }
    }

    private fun confirmSuggestions() {
        if (adopting || _state.value.categories.isNotEmpty()) return
        val current = _state.value
        val snapshot = if (current.selectedSuggestionNames.isEmpty()) {
            current.suggestions
        } else {
            current.suggestions.filter { it.name in current.selectedSuggestionNames }
        }
        if (snapshot.isEmpty()) return
        val addedAll = snapshot.size == current.suggestions.size
        val selectedCount = snapshot.size
        adopting = true
        viewModelScope.launch {
            try {
                for (suggestion in snapshot) {
                    if (!adoptAndTrack(suggestion, addedAll, selectedCount)) return@launch
                }
            } finally {
                adopting = false
            }
        }
    }

    /** False when the write failed. The caller stops a bulk adopt there. */
    private suspend fun adoptAndTrack(
        suggestion: SuggestedCategory,
        addedAll: Boolean,
        selectedCount: Int
    ): Boolean {
        return when (val result = adopt.adopt(suggestion.name)) {
            is Result.Error -> {
                _events.send(CategoriesEvent.ShowMessage(result.error.toUiText()))
                false
            }
            is Result.Success -> {
                analytics.track(
                    AnalyticsEvent.SuggestedCategoryAdopted(
                        categoryKey = suggestion.key,
                        screen = AnalyticsParams.SCREEN_CATEGORIES,
                        addedAll = addedAll,
                        catalogVersion = _state.value.catalogVersion,
                        selectedCount = selectedCount
                    )
                )
                true
            }
        }
    }

    private fun confirmDialog() {
        when (val dialog = _state.value.dialog) {
            is CategoryDialog.Add -> upsert(dialog.name) { name -> adopt.adopt(name) }
            is CategoryDialog.Edit -> upsert(dialog.name) { name ->
                when (val renamed = categories.rename(dialog.id, name)) {
                    is Result.Error -> renamed
                    is Result.Success -> Result.Success(dialog.id)
                }
            }
            is CategoryDialog.ConfirmDelete -> {
                viewModelScope.launch {
                    // Re-check the link count right before deleting.
                    val linked = (categories.linkedPayeeCount(dialog.id) as? Result.Success)?.data ?: 0
                    if (linked > 0) {
                        _events.send(
                            CategoriesEvent.ShowMessage(
                                UiText.StringResource(R.string.category_delete_blocked, arrayOf(linked))
                            )
                        )
                    } else {
                        categories.delete(dialog.id).onFailure { error ->
                            _events.send(CategoriesEvent.ShowMessage(error.toUiText()))
                        }
                    }
                    _state.update { it.copy(dialog = null) }
                }
            }
            null -> Unit
        }
    }

    private fun upsert(
        rawName: String,
        operation: suspend (String) -> Result<Long, com.madtitan94.transactionsparser.core.domain.util.DataError.Local>
    ) {
        val name = rawName.trim()
        if (name.isBlank()) {
            setDialogError(UiText.StringResource(R.string.category_name_required))
            return
        }
        viewModelScope.launch {
            when (val result = operation(name)) {
                is Result.Error -> setDialogError(result.error.toUiText())
                is Result.Success -> _state.update { it.copy(dialog = null) }
            }
        }
    }

    private fun setDialogError(error: UiText) {
        _state.update { current ->
            current.copy(
                dialog = when (val dialog = current.dialog) {
                    is CategoryDialog.Add -> dialog.copy(error = error)
                    is CategoryDialog.Edit -> dialog.copy(error = error)
                    else -> dialog
                }
            )
        }
    }
}
