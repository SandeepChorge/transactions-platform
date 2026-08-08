package com.madtitan94.transactionsparser.core.parsing

import com.madtitan94.transactionsparser.core.domain.model.StatementSource
import com.madtitan94.transactionsparser.core.domain.model.TransactionType
import com.madtitan94.transactionsparser.core.domain.parsing.ParseError
import com.madtitan94.transactionsparser.core.domain.parsing.ParsedStatement
import com.madtitan94.transactionsparser.core.domain.parsing.ParsedTransaction
import com.madtitan94.transactionsparser.core.domain.parsing.StatementParser
import com.madtitan94.transactionsparser.core.domain.util.Result
import java.time.LocalDate

/**
 * Parses PhonePe "Transaction Statement for <mobile>" PDFs.
 *
 * Row shape (as extracted text):
 *   Jul 03, 2026 Paid to SRI DATTA SUPER SHOPPE DEBIT ₹10
 *   01:46 PM Transaction ID T2607031346510066848829
 *   UTR No. 930722951778
 *   Paid by 5969XXXXXXX0143
 */
class PhonePeStatementParser : StatementParser {

    override val source = StatementSource.PHONEPE

    private val rowDateRegex =
        Regex("""\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+(\d{1,2}),\s+(\d{4})\b""")
    private val anchorRegex = Regex("""(Paid to|Received from)\s+""")
    private val typeRegex = Regex("""\b(DEBIT|CREDIT)\b""")
    private val amountRegex = Regex("""(?:₹|INR|Rs\.?)\s*([\d,]+(?:\.\d{1,2})?)""")
    private val txnIdRegex = Regex("""Transaction ID\s*:?\s*(T\d+)""")
    private val utrRegex = Regex("""UTR No\.?\s*:?\s*(\d+)""")
    private val periodRegex =
        Regex("""(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec),?\s+(\d{4})\s*[-–]\s*(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec),?\s+(\d{4})""")

    private val payeeStopTokens = listOf(
        Regex("""\b(?:DEBIT|CREDIT)\b"""),
        Regex("""[₹\n]"""),
        Regex("""Transaction ID"""),
        Regex("""UTR No""")
    )

    override fun canParse(text: String): Boolean {
        return text.contains("Transaction Statement for", ignoreCase = true) &&
            (txnIdRegex.containsMatchIn(text) || utrRegex.containsMatchIn(text))
    }

    override fun parse(text: String): Result<ParsedStatement, ParseError> {
        if (!canParse(text)) return Result.Error(ParseError.UNRECOGNIZED_FORMAT)

        val transactions = ParserSupport.chunkByRowDates(text, rowDateRegex).mapNotNull { (dateMatch, chunk) ->
            parseChunk(dateMatch, chunk)
        }
        if (transactions.isEmpty()) return Result.Error(ParseError.NO_TRANSACTIONS)

        val period = parsePeriod(text)
        return Result.Success(
            ParsedStatement(
                source = source,
                periodStartMillis = period?.first,
                periodEndMillis = period?.second,
                transactions = transactions
            )
        )
    }

    private fun parseChunk(dateMatch: MatchResult, chunk: String): ParsedTransaction? {
        val anchor = anchorRegex.find(chunk) ?: return null
        val payee = ParserSupport.extractPayee(chunk, anchor.range.last + 1, payeeStopTokens) ?: return null
        val amountPaise = amountRegex.find(chunk)?.groupValues?.get(1)
            ?.let(ParserSupport::amountToPaise) ?: return null

        val (monthName, day, year) = dateMatch.destructured
        val month = ParserSupport.MONTHS[monthName] ?: return null
        val date = runCatching { LocalDate.of(year.toInt(), month, day.toInt()) }.getOrNull() ?: return null

        val type = when {
            anchor.groupValues[1] == "Received from" -> TransactionType.CREDIT
            typeRegex.find(chunk)?.groupValues?.get(1) == "CREDIT" -> TransactionType.CREDIT
            else -> TransactionType.DEBIT
        }

        return ParsedTransaction(
            dateTimeUtcMillis = ParserSupport.toUtcMillis(date, ParserSupport.parseTime(chunk)),
            rawPayee = payee,
            amountPaise = amountPaise,
            type = type,
            transactionRef = txnIdRegex.find(chunk)?.groupValues?.get(1),
            utr = utrRegex.find(chunk)?.groupValues?.get(1)
        )
    }

    private fun parsePeriod(text: String): Pair<Long, Long>? {
        val match = periodRegex.find(text) ?: return null
        val g = match.groupValues
        val start = periodDate(g[1], g[2], g[3]) ?: return null
        val end = periodDate(g[4], g[5], g[6]) ?: return null
        return start to end
    }

    private fun periodDate(day: String, monthName: String, year: String): Long? {
        val month = ParserSupport.MONTHS[monthName] ?: return null
        val date = runCatching { LocalDate.of(year.toInt(), month, day.toInt()) }.getOrNull() ?: return null
        return ParserSupport.toUtcMillis(date, java.time.LocalTime.MIDNIGHT)
    }
}
