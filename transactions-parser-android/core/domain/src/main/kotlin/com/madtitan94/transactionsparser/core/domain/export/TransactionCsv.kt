package com.madtitan94.transactionsparser.core.domain.export

import com.madtitan94.transactionsparser.core.domain.model.TransactionExportRow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Renders exported rows as RFC 4180 CSV.
 *
 * Pure and free of Android so the escaping rules can be tested directly — quoting is the part of
 * CSV that silently corrupts a file when it is wrong, and a payee name containing a comma is not
 * a rare case in bank statements.
 */
object TransactionCsv {

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    /**
     * Machine-readable on purpose: ISO dates, 24-hour times, and amounts as plain decimals with
     * no currency symbol or grouping separators. The display formatters render for people; a
     * spreadsheet needs values it can sort and sum.
     */
    val HEADER = listOf(
        "Date",
        "Time",
        "Payee",
        "Alias",
        "Category",
        "Amount",
        "Type",
        "Reference",
        "UTR",
        "Duplicate",
        "Counted",
        "Statement"
    )

    fun build(rows: List<TransactionExportRow>): String = buildString {
        appendLine(HEADER.joinToString(",", transform = ::escape))
        rows.forEach { row ->
            appendLine(cells(row).joinToString(",", transform = ::escape))
        }
    }

    private fun cells(row: TransactionExportRow): List<String> {
        // Statement times are the printed wall clock stored as-if-UTC, so they are read back with
        // ZoneOffset.UTC — the same rule the day/month subtotal queries rely on. Converting to the
        // device zone here would shift a row onto a different date than the app shows for it.
        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(row.dateTimeUtcMillis), ZoneOffset.UTC)
        return listOf(
            dateFormat.format(dateTime),
            timeFormat.format(dateTime),
            row.rawPayee,
            row.alias.orEmpty(),
            row.category.orEmpty(),
            formatAmount(row.amountPaise),
            row.type.name,
            row.transactionRef.orEmpty(),
            row.utr.orEmpty(),
            row.isDuplicate.toYesNo(),
            // Inverted deliberately: the app's stored flag is "excluded", but "Counted" is the
            // question a reader of the spreadsheet is actually asking of a row.
            (!row.isExcluded).toYesNo(),
            row.statementFileName
        )
    }

    /** Always two decimal places, so a column of amounts sorts and sums as text or as numbers. */
    private fun formatAmount(paise: Long): String {
        val sign = if (paise < 0) "-" else ""
        val absolute = kotlin.math.abs(paise)
        return "$sign${absolute / 100}.${(absolute % 100).toString().padStart(2, '0')}"
    }

    private fun Boolean.toYesNo(): String = if (this) "Yes" else "No"

    /**
     * Quote only when the value would otherwise break the row, and double any embedded quote.
     * A leading or trailing space is quoted too — some readers trim it, which would silently
     * change a payee name.
     */
    private fun escape(value: String): String {
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' } ||
            value != value.trim()
        if (!needsQuoting) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}
