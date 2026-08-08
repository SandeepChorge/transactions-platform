package com.madtitan94.transactionsparser.feature.profile.domain

import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Error
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

enum class Gender { MALE, FEMALE, OTHER }

data class UserProfile(
    val name: String,
    val mobile: String,
    val gender: Gender?
)

interface ProfileStorage {
    fun observeProfile(): Flow<UserProfile?>
    suspend fun save(profile: UserProfile): EmptyResult<DataError.Local>
}

enum class ProfileValidationError : Error {
    NAME_BLANK,
    MOBILE_INVALID
}

object ProfileValidator {
    private val mobileRegex = Regex("^[6-9]\\d{9}$")

    fun validateName(name: String): EmptyResult<ProfileValidationError> {
        return if (name.isBlank()) {
            Result.Error(ProfileValidationError.NAME_BLANK)
        } else {
            Result.Success(Unit)
        }
    }

    /** Mobile is optional — blank is valid; non-blank must be a 10 digit Indian mobile. */
    fun validateMobile(mobile: String): EmptyResult<ProfileValidationError> {
        return if (mobile.isBlank() || mobileRegex.matches(mobile)) {
            Result.Success(Unit)
        } else {
            Result.Error(ProfileValidationError.MOBILE_INVALID)
        }
    }
}
