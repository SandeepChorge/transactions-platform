package com.madtitan94.transactionsparser.feature.auth.presentation

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.madtitan94.transactionsparser.core.domain.model.UserSession
import com.madtitan94.transactionsparser.core.domain.util.Error
import com.madtitan94.transactionsparser.core.domain.util.Result

enum class AuthError : Error {
    CANCELLED,
    NO_CREDENTIAL,
    INVALID_CREDENTIAL,
    UNKNOWN
}

/**
 * Wraps Credential Manager "Sign in with Google". Must be called with an Activity context.
 */
class GoogleCredentialHelper(private val webClientId: String) {

    suspend fun requestSignIn(activityContext: Context): Result<UserSession, AuthError> {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val response = CredentialManager.create(activityContext)
                .getCredential(activityContext, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                Result.Success(
                    UserSession(
                        googleId = google.id,
                        name = google.displayName.orEmpty().ifBlank { google.id.substringBefore("@") },
                        email = google.id,
                        photoUrl = google.profilePictureUri?.toString()
                    )
                )
            } else {
                Result.Error(AuthError.INVALID_CREDENTIAL)
            }
        } catch (e: GetCredentialCancellationException) {
            Result.Error(AuthError.CANCELLED)
        } catch (e: NoCredentialException) {
            Result.Error(AuthError.NO_CREDENTIAL)
        } catch (e: GetCredentialException) {
            Result.Error(AuthError.UNKNOWN)
        }
    }
}
