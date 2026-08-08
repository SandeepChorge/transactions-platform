package com.madtitan94.transactionsparser.core.presentation

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val INDIA = Locale("en", "IN")
private val integerFormat = NumberFormat.getIntegerInstance(INDIA)
private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
private val dateTimeFormat = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.ENGLISH)

/** "₹2,580" for whole rupees, "₹2,580.50" otherwise. */
fun formatPaise(paise: Long): String {
    val rupees = paise / 100
    val fraction = (paise % 100).toInt()
    val formatted = integerFormat.format(rupees)
    return if (fraction == 0) "₹$formatted" else "₹$formatted.%02d".format(fraction)
}

/** Statement wall-clock stored as-if-UTC — always read back with UTC. */
fun statementDateTime(utcMillis: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(utcMillis), ZoneOffset.UTC)

fun formatStatementDate(utcMillis: Long): String = dateFormat.format(statementDateTime(utcMillis))

fun formatStatementDateTime(utcMillis: Long): String = dateTimeFormat.format(statementDateTime(utcMillis))

fun formatHourOfDay(hour: Int): String {
    val h = hour % 12
    val display = if (h == 0) 12 else h
    return if (hour < 12) "$display AM" else "$display PM"
}
