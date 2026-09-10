package com.madtitan94.transactionsparser.feature.settings.domain

import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult

/**
 * Clears all app-managed local accounts and data, then ends the current login session.
 * External backups, exported documents, and uploaded telemetry are outside this operation.
 */
interface DeleteAccountService {
    suspend fun deleteAccount(): EmptyResult<DataError.Local>
}

