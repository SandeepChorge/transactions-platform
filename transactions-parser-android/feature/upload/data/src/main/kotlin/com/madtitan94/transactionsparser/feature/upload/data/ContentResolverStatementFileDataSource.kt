package com.madtitan94.transactionsparser.feature.upload.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.madtitan94.transactionsparser.core.domain.parsing.ParseError
import com.madtitan94.transactionsparser.core.domain.util.Result
import com.madtitan94.transactionsparser.feature.upload.domain.PickedFileMetadata
import com.madtitan94.transactionsparser.feature.upload.domain.StatementFileDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ContentResolverStatementFileDataSource(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : StatementFileDataSource {

    override suspend fun metadata(uriString: String): Result<PickedFileMetadata, ParseError> =
        withContext(ioDispatcher) {
            try {
                val uri = Uri.parse(uriString)
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) return@withContext Result.Error(ParseError.NOT_A_PDF)
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else "statement.pdf"
                    val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
                    Result.Success(PickedFileMetadata(displayName = name ?: "statement.pdf", sizeBytes = size))
                } ?: Result.Error(ParseError.NOT_A_PDF)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Result.Error(ParseError.NOT_A_PDF)
            }
        }

    override suspend fun copyToCache(uriString: String): Result<String, ParseError> =
        withContext(ioDispatcher) {
            val tempFile = File(context.cacheDir, "statement_${System.currentTimeMillis()}.pdf")
            try {
                context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext Result.Error(ParseError.EXTRACTION_FAILED)
                Result.Success(tempFile.absolutePath)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                tempFile.delete()
                Result.Error(ParseError.STORAGE_FAILURE)
            }
        }

    override suspend fun deleteTempFile(path: String): Boolean = withContext(ioDispatcher) {
        val file = File(path)
        !file.exists() || file.delete()
    }
}
