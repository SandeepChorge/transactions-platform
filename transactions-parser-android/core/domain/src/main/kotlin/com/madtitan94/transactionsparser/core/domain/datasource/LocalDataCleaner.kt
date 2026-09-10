package com.madtitan94.transactionsparser.core.domain.datasource

import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult

/** Clears one local store for a device-wide reset. Must be safe to retry after partial deletion. */
fun interface LocalDataCleaner {
    suspend fun clearLocalData(): EmptyResult<DataError.Local>
}
