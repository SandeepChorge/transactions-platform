package com.madtitan94.transactionsparser.feature.profile.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madtitan94.transactionsparser.core.domain.datasource.SessionStorage
import com.madtitan94.transactionsparser.core.domain.util.Result
import com.madtitan94.transactionsparser.core.domain.util.onFailure
import com.madtitan94.transactionsparser.core.domain.util.onSuccess
import com.madtitan94.transactionsparser.core.presentation.UiText
import com.madtitan94.transactionsparser.feature.profile.domain.Gender
import com.madtitan94.transactionsparser.feature.profile.domain.ProfileStorage
import com.madtitan94.transactionsparser.feature.profile.domain.ProfileValidator
import com.madtitan94.transactionsparser.feature.profile.domain.UserProfile
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileState(
    val isLoading: Boolean = true,
    val email: String = "",
    val photoUrl: String? = null,
    val name: String = "",
    val mobile: String = "",
    val gender: Gender? = null,
    val isEditing: Boolean = false,
    val nameError: UiText? = null,
    val mobileError: UiText? = null,
    val isSaving: Boolean = false
)

sealed interface ProfileAction {
    data object OnEditClick : ProfileAction
    data object OnCancelEdit : ProfileAction
    data class OnNameChange(val name: String) : ProfileAction
    data class OnMobileChange(val mobile: String) : ProfileAction
    data class OnGenderSelect(val gender: Gender?) : ProfileAction
    data object OnSaveClick : ProfileAction
    data object OnLogoutClick : ProfileAction
}

sealed interface ProfileEvent {
    data class ShowMessage(val message: UiText) : ProfileEvent
}

class ProfileViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val sessionStorage: SessionStorage,
    private val profileStorage: ProfileStorage
) : ViewModel() {

    private val _state = MutableStateFlow(
        ProfileState(
            name = savedStateHandle[KEY_NAME] ?: "",
            mobile = savedStateHandle[KEY_MOBILE] ?: "",
            isEditing = savedStateHandle[KEY_EDITING] ?: false
        )
    )
    val state = _state.asStateFlow()

    private val _events = Channel<ProfileEvent>()
    val events = _events.receiveAsFlow()

    init {
        observeStoredData()
    }

    private fun observeStoredData() {
        viewModelScope.launch {
            combine(
                sessionStorage.observeSession(),
                profileStorage.observeProfile()
            ) { session, profile -> session to profile }
                .collect { (session, profile) ->
                    _state.update { current ->
                        val editing = current.isEditing
                        current.copy(
                            isLoading = false,
                            email = session?.email.orEmpty(),
                            photoUrl = session?.photoUrl,
                            // Don't clobber in-progress edits with stored values.
                            name = if (editing) current.name else profile?.name ?: session?.name.orEmpty(),
                            mobile = if (editing) current.mobile else profile?.mobile.orEmpty(),
                            gender = if (editing) current.gender else profile?.gender
                        )
                    }
                }
        }
    }

    fun onAction(action: ProfileAction) {
        when (action) {
            ProfileAction.OnEditClick -> {
                savedStateHandle[KEY_EDITING] = true
                _state.update { it.copy(isEditing = true, nameError = null, mobileError = null) }
            }
            ProfileAction.OnCancelEdit -> {
                savedStateHandle[KEY_EDITING] = false
                _state.update { it.copy(isEditing = false, nameError = null, mobileError = null) }
                refreshFromStorage()
            }
            is ProfileAction.OnNameChange -> {
                savedStateHandle[KEY_NAME] = action.name
                _state.update { it.copy(name = action.name, nameError = null) }
            }
            is ProfileAction.OnMobileChange -> {
                val digits = action.mobile.filter(Char::isDigit).take(10)
                savedStateHandle[KEY_MOBILE] = digits
                _state.update { it.copy(mobile = digits, mobileError = null) }
            }
            is ProfileAction.OnGenderSelect -> {
                _state.update { it.copy(gender = action.gender) }
            }
            ProfileAction.OnSaveClick -> save()
            ProfileAction.OnLogoutClick -> {
                viewModelScope.launch { sessionStorage.clear() }
            }
        }
    }

    private fun refreshFromStorage() {
        viewModelScope.launch {
            val profile = profileStorage.observeProfile().first()
            _state.update {
                it.copy(
                    name = profile?.name ?: it.name,
                    mobile = profile?.mobile.orEmpty(),
                    gender = profile?.gender
                )
            }
        }
    }

    private fun save() {
        val current = _state.value

        val nameValidation = ProfileValidator.validateName(current.name)
        if (nameValidation is Result.Error) {
            _state.update { it.copy(nameError = UiText.StringResource(R.string.profile_error_name_blank)) }
            return
        }
        val mobileValidation = ProfileValidator.validateMobile(current.mobile)
        if (mobileValidation is Result.Error) {
            _state.update { it.copy(mobileError = UiText.StringResource(R.string.profile_error_mobile_invalid)) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            profileStorage.save(
                UserProfile(
                    name = current.name.trim(),
                    mobile = current.mobile.trim(),
                    gender = current.gender
                )
            ).onSuccess {
                savedStateHandle[KEY_EDITING] = false
                _state.update { it.copy(isSaving = false, isEditing = false) }
                _events.send(ProfileEvent.ShowMessage(UiText.StringResource(R.string.profile_saved)))
            }.onFailure {
                _state.update { it.copy(isSaving = false) }
                _events.send(ProfileEvent.ShowMessage(UiText.StringResource(R.string.profile_save_failed)))
            }
        }
    }

    private companion object {
        const val KEY_NAME = "profile_name"
        const val KEY_MOBILE = "profile_mobile"
        const val KEY_EDITING = "profile_editing"
    }
}
