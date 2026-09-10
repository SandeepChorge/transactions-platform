package com.madtitan94.transactionsparser.feature.auth.data

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import com.madtitan94.transactionsparser.core.domain.datasource.LocalDataCleaner
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.withTimeoutOrNull

/** Ends the app's Google credential session; it does not delete the user's Google account. */
class GoogleCredentialStateCleaner(private val context: Context) : LocalDataCleaner {
    override suspend fun clearLocalData(): EmptyResult<DataError.Local> = try {
        // A stuck provider must not leave the non-cancellable deletion flow open indefinitely.
        withTimeoutOrNull(10_000) {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            Result.Success(Unit)
        } ?: Result.Error(DataError.Local.UNKNOWN)
    } catch (_: ClearCredentialException) {
        // Do not claim a successful sign-out when the provider failed. The user can retry.
        Result.Error(DataError.Local.UNKNOWN)
    }
}
