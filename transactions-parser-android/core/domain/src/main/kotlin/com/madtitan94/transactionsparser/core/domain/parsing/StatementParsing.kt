package com.madtitan94.transactionsparser.core.domain.parsing

import com.madtitan94.transactionsparser.core.domain.model.StatementSource
import com.madtitan94.transactionsparser.core.domain.model.TransactionType
import com.madtitan94.transactionsparser.core.domain.util.Error
import com.madtitan94.transactionsparser.core.domain.util.Result

enum class ParseError : Error {
    FILE_TOO_LARGE,
    NOT_A_PDF,
    PASSWORD_PROTECTED,
    EXTRACTION_FAILED,
    UNRECOGNIZED_FORMAT,
    NO_TRANSACTIONS,
    STORAGE_FAILURE
}

data class ParsedTransaction(
    val dateTimeUtcMillis: Long,
    val rawPayee: String,
    val amountPaise: Long,
    val type: TransactionType,
    val transactionRef: String?,
    val utr: String?
)

data class ParsedStatement(
    val source: StatementSource,
    val periodStartMillis: Long?,
    val periodEndMillis: Long?,
    val transactions: List<ParsedTransaction>
)

/**
 * Parses raw extracted statement text into structured transactions.
 * Add a new implementation per statement source (bank statements in V2)
 * and register it in [StatementParserRegistry].
 */
interface StatementParser {
    val source: StatementSource
    fun canParse(text: String): Boolean
    fun parse(text: String): Result<ParsedStatement, ParseError>
}

class StatementParserRegistry(private val parsers: List<StatementParser>) {
    fun findFor(text: String): StatementParser? = parsers.firstOrNull { it.canParse(text) }
}

/** Extracts plain text from a (non password protected) PDF file on disk. */
interface StatementTextExtractor {
    suspend fun extractText(filePath: String): Result<String, ParseError>
}
