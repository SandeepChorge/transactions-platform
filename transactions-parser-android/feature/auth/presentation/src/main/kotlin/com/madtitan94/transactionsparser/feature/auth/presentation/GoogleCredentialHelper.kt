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
import org.json.JSONException
import org.json.JSONObject
import java.util.Base64

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
                // Never fall back to the email here: a fallback would silently give the same
                // person two different owner ids and split their data in half.
                val subject = googleSubjectOf(google.idToken)
                    ?: return Result.Error(AuthError.INVALID_CREDENTIAL)

                Result.Success(
                    UserSession(
                        googleId = subject,
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

/**
 * Reads the `sub` claim — Google's permanent id for the account — out of an ID token.
 *
 * `GoogleIdTokenCredential.id` is the email address, which is not a safe owner key: a Workspace
 * rename changes it, and a backend verifying this same token would key on `sub` instead, so the
 * two would never line up. `sub` never changes.
 *
 * The signature is deliberately not checked. This token arrives directly from Play Services over
 * a trusted binder call, so there is nothing in between to forge it. **A server receiving this
 * token over the network must verify it** — there, anyone can post an invented one.
 */
internal fun googleSubjectOf(idToken: String): String? {
    val payload = idToken.split(".").getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
    return try {
        val decoded = Base64.getUrlDecoder().decode(payload)
        JSONObject(String(decoded, Charsets.UTF_8))
            .optString("sub")
            .takeIf { it.isNotBlank() }
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: JSONException) {
        null
    }
}
