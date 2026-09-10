package com.madtitan94.transactionsparser.feature.settings.data

import com.madtitan94.transactionsparser.core.domain.datasource.LocalDataCleaner
import com.madtitan94.transactionsparser.core.domain.datasource.SessionStorage
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result
import com.madtitan94.transactionsparser.feature.settings.domain.DeleteAccountService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The app supplies the stores; Settings has no dependencies on other features or Room. */
class LocalDeleteAccountService(
    private val cleaners: List<LocalDataCleaner>,
    private val sessionStorage: SessionStorage
) : DeleteAccountService {
    private val mutex = Mutex()

    override suspend fun deleteAccount(): EmptyResult<DataError.Local> = mutex.withLock {
        // Once confirmed, disposing the screen must not interrupt a partly completed reset.
        withContext(NonCancellable) {
            try {
                for (cleaner in cleaners) {
                    val result = cleaner.clearLocalData()
                    if (result is Result.Error) return@withContext result
                }
                // Last: this switches AppRoot to LoginRoot and discards the signed-in NavHost.
                // On failure the session remains available so the user can retry deletion.
                sessionStorage.clear()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Result.Error(DataError.Local.UNKNOWN)
            }
        }
    }
}
