package com.madtitan94.transactionsparser.core.presentation

import android.content.Context
import android.net.Uri
import com.madtitan94.transactionsparser.core.domain.datasource.DocumentWriter
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Writes to a Storage Access Framework document the user picked.
 *
 * SAF needs no runtime storage permission — the system picker *is* the grant — so the failures
 * worth handling are a full disk and a destination that disappeared between being picked and
 * being written.
 */
class AndroidDocumentWriter(private val context: Context) : DocumentWriter {

    override suspend fun write(destination: String, content: String): EmptyResult<DataError.Local> =
        withContext(Dispatchers.IO) {
            try {
                // "wt" truncates. Without it, overwriting a longer existing file leaves the old
                // tail behind and produces a CSV with trailing garbage after the last row.
                val stream = context.contentResolver.openOutputStream(Uri.parse(destination), "wt")
                    ?: return@withContext Result.Error(DataError.Local.NOT_FOUND)
                stream.use { it.write(content.toByteArray()) }
                Result.Success(Unit)
            } catch (e: IOException) {
                Result.Error(if (e.isDiskFull()) DataError.Local.DISK_FULL else DataError.Local.UNKNOWN)
            } catch (e: SecurityException) {
                // The granted URI permission did not survive — process death between the picker
                // returning and the write starting is the realistic way here.
                Result.Error(DataError.Local.NOT_FOUND)
            }
        }

    private fun IOException.isDiskFull(): Boolean =
        message?.contains("ENOSPC", ignoreCase = true) == true ||
            message?.contains("No space left", ignoreCase = true) == true
}
