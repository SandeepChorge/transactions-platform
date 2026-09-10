package com.madtitan94.transactionsparser.core.database.datasource

import com.madtitan94.transactionsparser.core.database.TransactionsDatabase
import com.madtitan94.transactionsparser.core.domain.datasource.LocalDataCleaner
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomLocalDataCleaner(private val database: TransactionsDatabase) : LocalDataCleaner {
    override suspend fun clearLocalData(): EmptyResult<DataError.Local> = withContext(Dispatchers.IO) {
        try {
            // Includes every account, soft-deleted rows and payee identifiers. Room handles the
            // foreign-key order, WAL checkpoint and vacuum without closing its live connection.
            database.clearAllTables()
            Result.Success(Unit)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.Error(DataError.Local.UNKNOWN)
        }
    }
}
