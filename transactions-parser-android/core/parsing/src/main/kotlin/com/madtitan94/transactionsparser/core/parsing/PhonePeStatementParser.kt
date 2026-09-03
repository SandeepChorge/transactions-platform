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
 * A row is identified by its columns — date, Type, Amount — never by the phrase that opens the
 * Transaction Details column. PhonePe writes at least four such phrases ("Paid to", "Received
 * from", "Payment to", "Mobile recharged") and is free to add more, so treating that vocabulary
 * as a requirement silently drops rows: a real June 2026 statement lost 5 of 142 that way,
 * 4.6% of its value, with nothing in the app to show for it.
 *
 * PdfBox emits the columns in either of two shapes depending on how it orders the page, and
 * both must parse identically:
 *
 *   Jul 03, 2026 Paid to SRI DATTA SUPER SHOPPE   DEBIT   ₹10
 *   01:46 PM     Transaction ID T2607031346510066848829
 *
 *   Jul 03, 2026 Paid to SRI DATTA SUPER SHOPPE
 *   01:46 PM
 *   Transaction ID T2607031346510066848829
 *   DEBIT
 *   ₹10
 */
class PhonePeStatementParser : StatementParser {

    override val source = StatementSource.PHONEPE

    private val rowDateRegex =
        Regex("""\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+(\d{1,2}),\s+(\d{4})\b""")

    /**
     * The Type and Amount columns, matched as one unit.
     *
     * Binding them together is the point: a bare `\b(DEBIT|CREDIT)\b` search also matches a payee
     * such as "SHRIRAM CREDIT CO-OP", and because the details column is printed *before* the type
     * column, that payee would win the search and flip the row's direction. Direction is only ever
     * read from the Type column, and the amount that follows it is what identifies that column.
     */
    private val typeAmountRegex =
        Regex("""\b(DEBIT|CREDIT)\s*(?:₹|INR|Rs\.?)\s*([\d,]+(?:\.\d{1,2})?)""")

    /** Stripped from the details text when present, so a known payee reads as a bare name. */
    private val payeePhraseRegex = Regex("""^(?:Paid to|Received from)\s+""")

    /** Where the details column ends and the per-row reference lines begin. */
    private val payeeStopRegex = Regex("""Transaction ID|UTR No|Paid by|Jio Prepaid""")

    // Not every reference is a "T…" id — bill payments use OLEX…, recharges NX….
    private val txnIdRegex = Regex("""Transaction ID\s*:?\s*([A-Za-z0-9]+)""")
    private val utrRegex = Regex("""UTR No\.?\s*:?\s*(\d+)""")
    private val periodRegex =
        Regex("""(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec),?\s+(\d{4})\s*[-–]\s*(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec),?\s+(\d{4})""")

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

    /**
     * A chunk is a transaction when it carries a date and a Type/Amount pair. Nothing else is
     * required, and nothing recognisable is dropped for want of a payee we know how to name.
     */
    private fun parseChunk(dateMatch: MatchResult, chunk: String): ParsedTransaction? {
        val typeAmount = typeAmountRegex.find(chunk) ?: return null
        val amountPaise = ParserSupport.amountToPaise(typeAmount.groupValues[2]) ?: return null
        val type =
            if (typeAmount.groupValues[1] == "CREDIT") TransactionType.CREDIT else TransactionType.DEBIT

        val (monthName, day, year) = dateMatch.destructured
        val month = ParserSupport.MONTHS[monthName] ?: return null
        val date = runCatching { LocalDate.of(year.toInt(), month, day.toInt()) }.getOrNull() ?: return null

        val transactionRef = txnIdRegex.find(chunk)?.groupValues?.get(1)

        return ParsedTransaction(
            dateTimeUtcMillis = ParserSupport.toUtcMillis(date, ParserSupport.parseTime(chunk)),
            // A row with no details text at all still belongs in the statement; its reference is
            // real, printed, and unique, so it carries the row until the user names it.
            rawPayee = extractPayee(dateMatch, chunk) ?: transactionRef ?: return null,
            amountPaise = amountPaise,
            type = type,
            transactionRef = transactionRef,
            utr = utrRegex.find(chunk)?.groupValues?.get(1)
        )
    }

    /**
     * Whatever the statement printed in the Transaction Details column, with the columns that share
     * the row removed. A recognised opening phrase is stripped so the payee reads as a bare name;
     * any other phrasing is kept verbatim — "Payment to Google" is a worse label than "Google" but
     * an infinitely better one than the row not existing, and the user renames it once through the
     * alias flow, after which `payee_identifiers` folds later spellings into the same payee.
     */
    private fun extractPayee(dateMatch: MatchResult, chunk: String): String? {
        val afterDate = chunk.substring(dateMatch.value.length)
        val details = afterDate.take(payeeStopRegex.find(afterDate)?.range?.first ?: afterDate.length)
            .replace(typeAmountRegex, " ")
            .replace(ParserSupport.TIME_REGEX, " ")
            .replace(ParserSupport.WHITESPACE_REGEX, " ")
            .trim()
        return details.replaceFirst(payeePhraseRegex, "").trim().ifBlank { null }
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
