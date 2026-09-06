package com.madtitan94.transactionsparser.core.presentation

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val INDIA = Locale("en", "IN")
private val integerFormat = NumberFormat.getIntegerInstance(INDIA)
private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
private val dateTimeFormat = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.ENGLISH)
private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val dayHeaderFormat = DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy", Locale.ENGLISH)
private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

/** "₹2,580" for whole rupees, "₹2,580.50" otherwise. */
fun formatPaise(paise: Long): String {
    val rupees = paise / 100
    val fraction = (paise % 100).toInt()
    val formatted = integerFormat.format(rupees)
    return if (fraction == 0) "₹$formatted" else "₹$formatted.%02d".format(fraction)
}

/**
 * "₹1.4k", "₹38.4k", "₹1.2L" — for the few places a full figure will not fit.
 *
 * Indian scale, not international: past a lakh the number reads in lakhs, because "₹1.2L" is what
 * the amount is called here and "₹120.0k" is not. Below ten thousand nothing is abbreviated, since
 * "₹4,280" fits everywhere the short form would have gone and abbreviating it only loses precision.
 *
 * Use it in a donut centre, a KPI tile or an average — never in a hero number or a list row, where
 * the exact figure is the thing the user came to read.
 */
fun formatPaiseCompact(paise: Long): String {
    val rupees = paise / 100
    val sign = if (rupees < 0) "-" else ""
    val magnitude = kotlin.math.abs(rupees)
    return when {
        magnitude >= 10_000_000L -> "$sign₹%.1fCr".format(magnitude / 10_000_000.0)
        magnitude >= 100_000L -> "$sign₹%.1fL".format(magnitude / 100_000.0)
        magnitude >= 10_000L -> "$sign₹%.1fk".format(magnitude / 1_000.0)
        // "-₹1,234", never "₹-1,234": the sign belongs in front of the money, not inside it.
        else -> sign + formatPaise(magnitude * 100)
    }
}

/** Statement wall-clock stored as-if-UTC — always read back with UTC. */
fun statementDateTime(utcMillis: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(utcMillis), ZoneOffset.UTC)

fun formatStatementDate(utcMillis: Long): String = dateFormat.format(statementDateTime(utcMillis))

fun formatStatementDateTime(utcMillis: Long): String = dateTimeFormat.format(statementDateTime(utcMillis))

fun formatStatementTime(utcMillis: Long): String = timeFormat.format(statementDateTime(utcMillis))

/** "Sunday, 01 Jun 2026" — the day header above a run of that day's transactions. */
fun formatStatementDayHeader(utcMillis: Long): String = dayHeaderFormat.format(statementDateTime(utcMillis))

/** "June 2026" — the month header a day run rolls up into. */
fun formatStatementMonth(utcMillis: Long): String = monthFormat.format(statementDateTime(utcMillis))

/**
 * "26 Aug 2026" for a real instant — the moment a file was written, say.
 *
 * Deliberately not [formatStatementDate]: that one reads UTC back because a statement timestamp is
 * a printed wall clock stored as-if-UTC. A wall-clock reading of a genuine instant would show the
 * wrong day to anyone east or west of Greenwich.
 */
fun formatInstantDate(epochMillis: Long): String =
    dateFormat.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()))

fun formatHourOfDay(hour: Int): String {
    val h = hour % 12
    val display = if (h == 0) 12 else h
    return if (hour < 12) "$display AM" else "$display PM"
}
