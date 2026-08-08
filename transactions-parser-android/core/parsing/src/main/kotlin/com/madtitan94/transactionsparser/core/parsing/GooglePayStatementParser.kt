package com.madtitan94.transactionsparser.core.parsing

import com.madtitan94.transactionsparser.core.domain.model.StatementSource
import com.madtitan94.transactionsparser.core.domain.model.TransactionType
import com.madtitan94.transactionsparser.core.domain.parsing.ParseError
import com.madtitan94.transactionsparser.core.domain.parsing.ParsedStatement
import com.madtitan94.transactionsparser.core.domain.parsing.ParsedTransaction
import com.madtitan94.transactionsparser.core.domain.parsing.StatementParser
import com.madtitan94.transactionsparser.core.domain.util.Result
import java.time.LocalDate
import java.time.LocalTime

/**
 * Parses Google Pay "Transaction statement" PDFs.
 *
 * Row shape (as extracted text):
 *   09 Jun, 2026 Paid to Mahavitaran - Maharashtra Electricity (MSEDCL) ₹2,580
 *   10:59 AM UPI Transaction ID: 652624029988
 *   Paid by Union Bank of India 0143
 */
class GooglePayStatementParser : StatementParser {

    override val source = StatementSource.GOOGLE_PAY

    private val rowDateRegex =
        Regex("""\b(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec),\s+(\d{4})\b""")
    private val anchorRegex = Regex("""(Paid to|Received from|Money received from)\s+""")
    private val amountRegex = Regex("""(?:₹|INR|Rs\.?)\s*([\d,]+(?:\.\d{1,2})?)""")
    private val txnIdRegex = Regex("""UPI Transaction ID\s*:?\s*(\d+)""")
    private val periodRegex =
        Regex("""(\d{1,2})\s+([A-Z][a-z]+)\s+(\d{4})\s*[-–]\s*(\d{1,2})\s+([A-Z][a-z]+)\s+(\d{4})""")

    private val payeeStopTokens = listOf(
        Regex("""[₹\n]"""),
        Regex("""\busing\b"""),
        Regex("""UPI Transaction ID"""),
        Regex("""Paid by""")
    )

    override fun canParse(text: String): Boolean {
        val lower = text.lowercase()
        val looksLikeGpay = lower.contains("google pay") || lower.contains("transaction statement")
        return looksLikeGpay && lower.contains("upi transaction id")
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

        val (day, monthName, year) = dateMatch.destructured
        val month = ParserSupport.MONTHS[monthName] ?: return null
        val date = runCatching { LocalDate.of(year.toInt(), month, day.toInt()) }.getOrNull() ?: return null

        val type = if (anchor.groupValues[1].contains("Received")) {
            TransactionType.CREDIT
        } else {
            TransactionType.DEBIT
        }

        return ParsedTransaction(
            dateTimeUtcMillis = ParserSupport.toUtcMillis(date, ParserSupport.parseTime(chunk)),
            rawPayee = payee,
            amountPaise = amountPaise,
            type = type,
            transactionRef = txnIdRegex.find(chunk)?.groupValues?.get(1),
            utr = null
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
        val month = ParserSupport.FULL_MONTHS[monthName] ?: ParserSupport.MONTHS[monthName] ?: return null
        val date = runCatching { LocalDate.of(year.toInt(), month, day.toInt()) }.getOrNull() ?: return null
        return ParserSupport.toUtcMillis(date, LocalTime.MIDNIGHT)
    }
}
