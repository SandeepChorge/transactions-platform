package com.madtitan94.transactionsparser.core.parsing

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

internal object ParserSupport {

    val MONTHS = mapOf(
        "Jan" to 1, "Feb" to 2, "Mar" to 3, "Apr" to 4, "May" to 5, "Jun" to 6,
        "Jul" to 7, "Aug" to 8, "Sep" to 9, "Oct" to 10, "Nov" to 11, "Dec" to 12
    )

    val FULL_MONTHS = mapOf(
        "January" to 1, "February" to 2, "March" to 3, "April" to 4, "May" to 5, "June" to 6,
        "July" to 7, "August" to 8, "September" to 9, "October" to 10, "November" to 11, "December" to 12
    )

    val TIME_REGEX = Regex("""\b(\d{1,2}):(\d{2})\s*([AP]M)\b""")

    val WHITESPACE_REGEX = Regex("""\s+""")

    /** "2,580" -> 258000; "2,580.50" -> 258050. Null when not a parsable amount. */
    fun amountToPaise(raw: String): Long? {
        val value = raw.replace(",", "").trim().toBigDecimalOrNull() ?: return null
        return value.movePointRight(2).toLong()
    }

    /** First hh:mm AM/PM occurrence in [chunk], or midnight when absent. */
    fun parseTime(chunk: String): LocalTime {
        val match = TIME_REGEX.find(chunk) ?: return LocalTime.MIDNIGHT
        val (h, m, meridiem) = match.destructured
        var hour = h.toInt() % 12
        if (meridiem == "PM") hour += 12
        val minute = m.toInt()
        if (hour !in 0..23 || minute !in 0..59) return LocalTime.MIDNIGHT
        return LocalTime.of(hour, minute)
    }

    /** Statement wall-clock time stored as-if-UTC so it round-trips on any device timezone. */
    fun toUtcMillis(date: LocalDate, time: LocalTime): Long =
        LocalDateTime.of(date, time).toInstant(ZoneOffset.UTC).toEpochMilli()

    /**
     * Payee text between [anchorEndIndex] and the first stop token.
     * Tolerates PDF extraction that keeps a row on one line or splits it across lines.
     */
    fun extractPayee(chunk: String, anchorEndIndex: Int, stopTokens: List<Regex>): String? {
        val rest = chunk.substring(anchorEndIndex)
        val stopAt = stopTokens.mapNotNull { it.find(rest)?.range?.first }.minOrNull() ?: rest.length
        return rest.take(stopAt)
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { null }
    }

    /** Splits [text] into per-transaction chunks, one starting at each row-date match. */
    fun chunkByRowDates(text: String, rowDateRegex: Regex): List<Pair<MatchResult, String>> {
        val matches = rowDateRegex.findAll(text).toList()
        return matches.mapIndexed { index, match ->
            val end = matches.getOrNull(index + 1)?.range?.first ?: text.length
            match to text.substring(match.range.first, end)
        }
    }
}
