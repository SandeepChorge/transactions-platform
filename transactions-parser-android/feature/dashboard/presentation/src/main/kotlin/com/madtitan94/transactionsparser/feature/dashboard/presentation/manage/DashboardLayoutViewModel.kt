package com.madtitan94.transactionsparser.feature.dashboard.presentation.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardDefinition
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardKey
import com.madtitan94.transactionsparser.feature.dashboard.domain.DashboardPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One dashboard as the manage screen sees it: what it is, and whether Home currently shows it.
 *
 * Disabled dashboards are in this list rather than filtered out of it — the whole job of the screen
 * is to offer them back, and a switch you cannot find is a setting you cannot undo.
 */
data class DashboardEntry(
    val definition: DashboardDefinition,
    val isEnabled: Boolean
) {
    val key: DashboardKey get() = definition.key

    /** Only a dashboard the user built can be deleted; the four that ship are switched off instead. */
    val isCustom: Boolean get() = definition.key is DashboardKey.Custom
}

data class DashboardLayoutState(
    val isLoading: Boolean = true,
    val dashboards: List<DashboardEntry> = emptyList(),
    val defaultKey: DashboardKey? = null,
    /** Set while the delete confirmation is on screen. */
    val pendingDelete: DashboardEntry? = null
) {
    val enabledCount: Int get() = dashboards.count { it.isEnabled }
}

sealed interface DashboardLayoutAction {
    data class OnEnabledChanged(val key: DashboardKey, val isEnabled: Boolean) : DashboardLayoutAction
    data class OnMoved(val from: Int, val to: Int) : DashboardLayoutAction
    data class OnDefaultSelected(val key: DashboardKey) : DashboardLayoutAction
    data class OnDeleteClick(val entry: DashboardEntry) : DashboardLayoutAction
    data object OnDeleteConfirmed : DashboardLayoutAction
    data object OnDeleteDismissed : DashboardLayoutAction
}

/**
 * Backs both dashboard settings screens — Manage dashboards and Default dashboard.
 *
 * One ViewModel for two screens because there is only one thing here: the saved layout. Splitting it
 * would give the two screens separate reads of the same preference file and a way to disagree about
 * which dashboards exist, which is the disagreement the layout model was written to prevent. Each
 * screen simply uses the part of the state and the actions it needs.
 */
class DashboardLayoutViewModel(
    private val preferences: DashboardPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardLayoutState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.observeLayout().collect { layout ->
                val disabled = layout.disabled
                _state.update { current ->
                    current.copy(
                        isLoading = false,
                        dashboards = layout.all.map { definition ->
                            DashboardEntry(definition, definition.key !in disabled)
                        },
                        defaultKey = layout.defaultDashboard,
                        // A dashboard deleted from under the dialog would leave it confirming
                        // something that no longer exists.
                        pendingDelete = current.pendingDelete
                            ?.takeIf { pending -> layout.all.any { it.key == pending.key } }
                    )
                }
            }
        }
    }

    fun onAction(action: DashboardLayoutAction) {
        when (action) {
            is DashboardLayoutAction.OnEnabledChanged -> viewModelScope.launch {
                preferences.setDashboardEnabled(action.key, action.isEnabled)
            }

            is DashboardLayoutAction.OnMoved -> move(action.from, action.to)

            is DashboardLayoutAction.OnDefaultSelected -> viewModelScope.launch {
                preferences.setDefaultDashboard(action.key)
            }

            is DashboardLayoutAction.OnDeleteClick ->
                _state.update { it.copy(pendingDelete = action.entry) }

            DashboardLayoutAction.OnDeleteDismissed ->
                _state.update { it.copy(pendingDelete = null) }

            DashboardLayoutAction.OnDeleteConfirmed -> {
                val pending = _state.value.pendingDelete ?: return
                val custom = pending.key as? DashboardKey.Custom ?: return
                _state.update { it.copy(pendingDelete = null) }
                viewModelScope.launch { preferences.deleteCustomDashboard(custom.id) }
            }
        }
    }

    /**
     * Applies a drag step to the state before writing it.
     *
     * The optimistic update is the point: a drag fires this on every row it crosses, and waiting for
     * the write to come back through the preference flow would let the finger get ahead of the list
     * and move the same row twice. The other actions on this screen are single taps and can afford
     * to wait for the flow, so they do — one source of truth wherever the frame rate allows it.
     */
    private fun move(from: Int, to: Int) {
        val current = _state.value.dashboards
        if (from !in current.indices || to !in current.indices || from == to) return
        val reordered = current.toMutableList().apply { add(to, removeAt(from)) }
        _state.update { it.copy(dashboards = reordered) }
        viewModelScope.launch { preferences.setDashboardOrder(reordered.map { it.key }) }
    }
}
