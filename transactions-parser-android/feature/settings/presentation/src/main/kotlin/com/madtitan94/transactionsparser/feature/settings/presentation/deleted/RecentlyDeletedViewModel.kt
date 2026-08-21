package com.madtitan94.transactionsparser.feature.settings.presentation.deleted

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madtitan94.transactionsparser.core.domain.datasource.CategoryLocalDataSource
import com.madtitan94.transactionsparser.core.domain.model.Category
import com.madtitan94.transactionsparser.core.domain.util.onFailure
import com.madtitan94.transactionsparser.core.domain.util.onSuccess
import com.madtitan94.transactionsparser.core.presentation.UiText
import com.madtitan94.transactionsparser.core.presentation.toUiText
import com.madtitan94.transactionsparser.feature.settings.presentation.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The item the confirmation dialog is currently asking about. */
data class RestorePrompt(val id: Long, val name: String)

data class RecentlyDeletedState(
    val isLoading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val pendingRestore: RestorePrompt? = null
)

sealed interface RecentlyDeletedAction {
    data class OnRestoreClick(val category: Category) : RecentlyDeletedAction
    data object OnConfirmRestore : RecentlyDeletedAction
    data object OnDismissRestore : RecentlyDeletedAction
}

sealed interface RecentlyDeletedEvent {
    data class ShowMessage(val message: UiText) : RecentlyDeletedEvent
}

/**
 * The user-facing half of soft delete, shipped two phases after the data layer that made rows
 * recoverable at all.
 *
 * Categories are the only thing listed because they are the only thing the app deletes today —
 * every other table carries the `isDeleted` column, but nothing writes it yet, so listing them
 * would only ever show an empty section.
 */
class RecentlyDeletedViewModel(
    private val categories: CategoryLocalDataSource
) : ViewModel() {

    private val _state = MutableStateFlow(RecentlyDeletedState())
    val state = _state.asStateFlow()

    private val _events = Channel<RecentlyDeletedEvent>()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            categories.observeDeleted().collect { deleted ->
                _state.update { state ->
                    state.copy(
                        isLoading = false,
                        categories = deleted,
                        // This is a live query, so a switch of account can empty the list while a
                        // dialog is open. Confirming one about a row that is no longer there
                        // would restore nothing and still report success.
                        pendingRestore = state.pendingRestore?.takeIf { prompt ->
                            deleted.any { it.id == prompt.id }
                        }
                    )
                }
            }
        }
    }

    fun onAction(action: RecentlyDeletedAction) {
        when (action) {
            is RecentlyDeletedAction.OnRestoreClick -> _state.update {
                it.copy(pendingRestore = RestorePrompt(action.category.id, action.category.name))
            }

            RecentlyDeletedAction.OnDismissRestore -> _state.update { it.copy(pendingRestore = null) }

            RecentlyDeletedAction.OnConfirmRestore -> {
                // Read before clearing: the prompt is the only record of which row was tapped.
                val pending = _state.value.pendingRestore ?: return
                _state.update { it.copy(pendingRestore = null) }
                restore(pending.id)
            }
        }
    }

    private fun restore(id: Long) {
        viewModelScope.launch {
            categories.restore(id)
                .onSuccess {
                    _events.send(
                        RecentlyDeletedEvent.ShowMessage(
                            UiText.StringResource(R.string.recently_deleted_restored)
                        )
                    )
                }
                .onFailure { _events.send(RecentlyDeletedEvent.ShowMessage(it.toUiText())) }
        }
    }
}
