package com.madtitan94.transactionsparser.feature.categories.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val dialog: CategoryDialog? = null
)

sealed interface CategoriesAction {
    data object OnAddClick : CategoriesAction
    data class OnEditClick(val id: Long) : CategoriesAction
    data class OnDeleteClick(val id: Long) : CategoriesAction
    data class OnDialogNameChange(val name: String) : CategoriesAction
    data object OnDialogConfirm : CategoriesAction
    data object OnDialogDismiss : CategoriesAction
}

sealed interface CategoriesEvent {
    data class ShowMessage(val message: UiText) : CategoriesEvent
}

class CategoriesViewModel(
    private val categories: CategoryLocalDataSource
) : ViewModel() {

    private val _state = MutableStateFlow(CategoriesState())
    val state = _state.asStateFlow()

    private val _events = Channel<CategoriesEvent>()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(
                categories.observeAll(),
                categories.observeLinkedPayeeCounts()
            ) { all, counts ->
                all.map { CategoryUi(id = it.id, name = it.name, linkedPayeeCount = counts[it.id] ?: 0) }
            }.collect { list ->
                _state.update { it.copy(isLoading = false, categories = list) }
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
        }
    }

    private fun confirmDialog() {
        when (val dialog = _state.value.dialog) {
            is CategoryDialog.Add -> upsert(dialog.name) { name -> categories.insert(name) }
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
