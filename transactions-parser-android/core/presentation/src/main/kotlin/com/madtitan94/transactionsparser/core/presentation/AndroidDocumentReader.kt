package com.madtitan94.transactionsparser.core.presentation

import android.content.Context
import android.net.Uri
import com.madtitan94.transactionsparser.core.domain.datasource.DocumentReader
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Reads a Storage Access Framework document the user picked, the counterpart of
 * [AndroidDocumentWriter].
 *
 * A backup file the user chose from another device could be anything, so the read is capped: a
 * file larger than [MAX_BYTES] is rejected before it is loaded rather than after, since a JSON
 * parser handed a multi-gigabyte video will exhaust memory before it decides the file is not JSON.
 */
class AndroidDocumentReader(private val context: Context) : DocumentReader {

    override suspend fun read(source: String): Result<String, DataError.Local> =
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(source)
                val stream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext Result.Error(DataError.Local.NOT_FOUND)

                val bytes = stream.use { input ->
                    // Hand-rolled rather than InputStream.readNBytes, which needs API 33 while
                    // this app supports 26. Stops one byte past the cap so a file that exactly
                    // fits is not mistaken for one that overruns.
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (buffer.size() <= MAX_BYTES) {
                        val read = input.read(chunk)
                        if (read == -1) break
                        buffer.write(chunk, 0, read)
                    }
                    buffer.toByteArray()
                }
                if (bytes.size > MAX_BYTES) {
                    return@withContext Result.Error(DataError.Local.UNKNOWN)
                }
                Result.Success(bytes.decodeToString())
            } catch (e: IOException) {
                Result.Error(DataError.Local.UNKNOWN)
            } catch (e: SecurityException) {
                // The granted URI permission did not survive — process death between the picker
                // returning and the read starting is the realistic way here.
                Result.Error(DataError.Local.NOT_FOUND)
            }
        }

    private companion object {
        /**
         * Comfortably above any real backup — the largest dataset seen so far is a few hundred
         * transactions, and even a hundred thousand would not approach this — while still small
         * enough that loading it whole cannot take the app down.
         */
        const val MAX_BYTES = 64 * 1024 * 1024
    }
}
