package com.madtitan94.transactionsparser.feature.settings.data

import android.content.Context
import com.madtitan94.transactionsparser.core.domain.datasource.LocalDataCleaner
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CacheLocalDataCleaner(private val context: Context) : LocalDataCleaner {
    override suspend fun clearLocalData(): EmptyResult<DataError.Local> = withContext(Dispatchers.IO) {
        try {
            // Only app-owned caches. Do not unlink open DataStore files or user-exported documents.
            val directories = listOf(context.cacheDir) + context.externalCacheDirs.filterNotNull()
            for (directory in directories) {
                if (!directory.exists()) continue
                val entries = directory.listFiles() ?: throw IOException("Cannot read cache directory")
                for (entry in entries) {
                    if (!entry.deleteRecursively()) throw IOException("Cannot delete cache entry")
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.Error(DataError.Local.UNKNOWN)
        }
    }
}
