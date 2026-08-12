package com.madtitan94.transactionsparser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madtitan94.transactionsparser.core.database.account.LegacyDataClaimer
import com.madtitan94.transactionsparser.core.domain.datasource.SessionStorage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data object LoggedIn : AuthState
}

class MainViewModel(
    sessionStorage: SessionStorage,
    legacyDataClaimer: LegacyDataClaimer
) : ViewModel() {

    val authState = sessionStorage.observeSession()
        .map { session -> if (session == null) AuthState.LoggedOut else AuthState.LoggedIn }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AuthState.Loading
        )

    init {
        viewModelScope.launch {
            sessionStorage.observeSession()
                .filterNotNull()
                .map { it.googleId }
                .distinctUntilChanged()
                .collect(legacyDataClaimer::claimFor)
        }
    }
}

val appModule = module {
    viewModelOf(::MainViewModel)
}
