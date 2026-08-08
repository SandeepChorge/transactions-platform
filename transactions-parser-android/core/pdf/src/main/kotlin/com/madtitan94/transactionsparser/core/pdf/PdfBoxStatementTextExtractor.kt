package com.madtitan94.transactionsparser.core.pdf

import android.content.Context
import com.madtitan94.transactionsparser.core.domain.parsing.ParseError
import com.madtitan94.transactionsparser.core.domain.parsing.StatementTextExtractor
import com.madtitan94.transactionsparser.core.domain.util.Result
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * On-device text extraction with PdfBox-Android. No statement data ever leaves the device.
 */
class PdfBoxStatementTextExtractor(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : StatementTextExtractor {

    private val initialized by lazy {
        PDFBoxResourceLoader.init(context.applicationContext)
        true
    }

    override suspend fun extractText(filePath: String): Result<String, ParseError> =
        withContext(ioDispatcher) {
            initialized
            val file = File(filePath)
            if (!file.exists()) return@withContext Result.Error(ParseError.EXTRACTION_FAILED)

            try {
                PDDocument.load(file).use { document ->
                    if (document.isEncrypted) {
                        return@withContext Result.Error(ParseError.PASSWORD_PROTECTED)
                    }
                    val stripper = PDFTextStripper().apply { sortByPosition = true }
                    Result.Success(stripper.getText(document))
                }
            } catch (e: InvalidPasswordException) {
                Result.Error(ParseError.PASSWORD_PROTECTED)
            } catch (e: IOException) {
                Result.Error(ParseError.EXTRACTION_FAILED)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Result.Error(ParseError.EXTRACTION_FAILED)
            }
        }
}
