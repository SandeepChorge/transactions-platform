package com.madtitan94.transactionsparser.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madtitan94.transactionsparser.core.domain.datasource.SessionStorage
import com.madtitan94.transactionsparser.core.domain.model.UserSession
import com.madtitan94.transactionsparser.core.domain.util.Result
import com.madtitan94.transactionsparser.core.domain.util.onFailure
import com.madtitan94.transactionsparser.core.presentation.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginState(
    val isSigningIn: Boolean = false,
    val error: UiText? = null
)

sealed interface LoginAction {
    data object OnSignInStarted : LoginAction
    data class OnSignInResult(val result: Result<UserSession, AuthError>) : LoginAction
}

class LoginViewModel(
    private val sessionStorage: SessionStorage
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state = _state.asStateFlow()

    fun onAction(action: LoginAction) {
        when (action) {
            LoginAction.OnSignInStarted -> {
                _state.update { it.copy(isSigningIn = true, error = null) }
            }
            is LoginAction.OnSignInResult -> handleResult(action.result)
        }
    }

    private fun handleResult(result: Result<UserSession, AuthError>) {
        when (result) {
            is Result.Success -> {
                viewModelScope.launch {
                    sessionStorage.save(result.data).onFailure {
                        _state.update {
                            it.copy(
                                isSigningIn = false,
                                error = UiText.StringResource(R.string.auth_error_unknown)
                            )
                        }
                    }
                    // Successful save flips the app-level session flow; this screen is replaced.
                    _state.update { it.copy(isSigningIn = false) }
                }
            }
            is Result.Error -> {
                _state.update { it.copy(isSigningIn = false, error = result.error.toUiText()) }
            }
        }
    }
}

fun AuthError.toUiText(): UiText = when (this) {
    AuthError.CANCELLED -> UiText.StringResource(R.string.auth_error_cancelled)
    AuthError.NO_CREDENTIAL -> UiText.StringResource(R.string.auth_error_no_credential)
    AuthError.INVALID_CREDENTIAL -> UiText.StringResource(R.string.auth_error_invalid_credential)
    AuthError.UNKNOWN -> UiText.StringResource(R.string.auth_error_unknown)
}
