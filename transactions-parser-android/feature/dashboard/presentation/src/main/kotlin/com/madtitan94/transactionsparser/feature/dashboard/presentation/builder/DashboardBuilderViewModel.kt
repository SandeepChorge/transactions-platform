package com.madtitan94.transactionsparser.feature.dashboard.presentation.builder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madtitan94.transactionsparser.feature.dashboard.domain.CustomDashboard
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardPreferences
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardWidgetId
import com.madtitan94.transactionsparser.feature.dashboard.presentation.navigation.DashboardBuilderRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** One line of the builder's checklist: a card type, and whether this dashboard carries it. */
data class BuilderRow(
    val widget: DashboardWidgetId,
    val isSelected: Boolean
)

data class DashboardBuilderState(
    val isLoading: Boolean = true,
    /** True when an existing dashboard is being edited rather than a new one composed. */
    val isEditing: Boolean = false,
    val name: String = "",
    val rows: List<BuilderRow> = emptyList()
) {
    /** The checked cards, in list order — exactly what Save writes. */
    val selected: List<DashboardWidgetId> get() = rows.filter { it.isSelected }.map { it.widget }

    /**
     * A dashboard needs a name and at least one card.
     *
     * The name because the chip row has to call it something, and an empty dashboard because it
     * would render as a blank page the user could not tell apart from a crash.
     */
    val canSave: Boolean get() = name.isNotBlank() && selected.isNotEmpty()
}

sealed interface DashboardBuilderAction {
    data class OnNameChanged(val name: String) : DashboardBuilderAction
    data class OnWidgetToggled(val widget: DashboardWidgetId, val isSelected: Boolean) : DashboardBuilderAction
    data class OnMoved(val from: Int, val to: Int) : DashboardBuilderAction
    data object OnSave : DashboardBuilderAction
}

sealed interface DashboardBuilderEvent {
    /** Saved and written; the screen closes on this rather than on the button press. */
    data object Saved : DashboardBuilderEvent
}

/**
 * The custom-dashboard builder: a checklist of every card the renderer knows, in the order they
 * will appear.
 *
 * One list rather than two — a "chosen" list and an "available" one — because order and membership
 * are the same decision here. An unchecked row still holds its place, so checking it puts the card
 * exactly where the user was already looking, and a card can be moved before it is turned on.
 *
 * Nothing is written until Save. This is the one screen in the dashboard settings that does not
 * apply as you go: the user is composing something, and a half-built dashboard appearing on Home
 * while they are still deciding is worse than making them press a button.
 */
class DashboardBuilderViewModel(
    savedStateHandle: SavedStateHandle,
    private val preferences: DashboardPreferences
) : ViewModel() {

    private val editingId: String? = savedStateHandle[DashboardBuilderRoute.ID_ARG]

    private val _state = MutableStateFlow(DashboardBuilderState())
    val state = _state.asStateFlow()

    private val _events = Channel<DashboardBuilderEvent>()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            // A single read rather than a subscription: the builder edits a snapshot and writes it
            // back on Save, so re-seeding it from a later emission would discard what the user has
            // typed since.
            val existing = editingId?.let { id ->
                preferences.observeLayout().first().custom.firstOrNull { it.id == id }
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    isEditing = existing != null,
                    name = existing?.name.orEmpty(),
                    rows = rowsFor(existing?.widgets.orEmpty())
                )
            }
        }
    }

    /**
     * The checked cards first in their saved order, then everything else in catalogue order.
     *
     * Chosen cards lead because that is the dashboard as it will be read; the rest follow as the
     * menu of what could be added.
     */
    private fun rowsFor(chosen: List<DashboardWidgetId>): List<BuilderRow> {
        val rest = DashboardWidgetId.entries.filterNot { it in chosen }
        return chosen.map { BuilderRow(it, isSelected = true) } +
            rest.map { BuilderRow(it, isSelected = false) }
    }

    fun onAction(action: DashboardBuilderAction) {
        when (action) {
            is DashboardBuilderAction.OnNameChanged ->
                _state.update { it.copy(name = action.name) }

            is DashboardBuilderAction.OnWidgetToggled -> _state.update { current ->
                current.copy(
                    rows = current.rows.map { row ->
                        if (row.widget == action.widget) row.copy(isSelected = action.isSelected) else row
                    }
                )
            }

            is DashboardBuilderAction.OnMoved -> move(action.from, action.to)

            DashboardBuilderAction.OnSave -> save()
        }
    }

    private fun move(from: Int, to: Int) {
        val rows = _state.value.rows
        if (from !in rows.indices || to !in rows.indices || from == to) return
        val reordered = rows.toMutableList().apply { add(to, removeAt(from)) }
        _state.update { it.copy(rows = reordered) }
    }

    private fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            preferences.saveCustomDashboard(
                CustomDashboard(
                    // A new id only on a new dashboard: reusing the one being edited is what makes
                    // this an edit rather than a second dashboard with the same name.
                    id = editingId ?: UUID.randomUUID().toString(),
                    name = current.name.trim(),
                    widgets = current.selected
                )
            )
            _events.send(DashboardBuilderEvent.Saved)
        }
    }
}
