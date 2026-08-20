package com.madtitan94.transactionsparser.core.domain.export

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.startsWith
import com.madtitan94.transactionsparser.core.domain.model.TransactionExportRow
import com.madtitan94.transactionsparser.core.domain.model.TransactionType
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class TransactionCsvTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun row(
        millis: Long = at(2026, 6, 1),
        rawPayee: String = "SWIGGY",
        alias: String? = "Swiggy",
        category: String? = "Food",
        amountPaise: Long = 25_800L,
        transactionRef: String? = "REF1",
        utr: String? = "UTR1",
        isDuplicate: Boolean = false,
        isExcluded: Boolean = false,
        statementFileName: String = "june.pdf"
    ) = TransactionExportRow(
        dateTimeUtcMillis = millis,
        rawPayee = rawPayee,
        alias = alias,
        category = category,
        amountPaise = amountPaise,
        type = TransactionType.DEBIT,
        transactionRef = transactionRef,
        utr = utr,
        isDuplicate = isDuplicate,
        isExcluded = isExcluded,
        statementFileName = statementFileName
    )

    private fun lines(csv: String) = csv.trim().lines()

    @Test
    fun `an export with no rows is still a valid file with just a header`() {
        val csv = TransactionCsv.build(emptyList())

        assertThat(lines(csv)).isEqualTo(listOf(TransactionCsv.HEADER.joinToString(",")))
    }

    @Test
    fun `a row renders in header order`() {
        val csv = TransactionCsv.build(listOf(row()))

        assertThat(lines(csv)[1])
            .isEqualTo("2026-06-01,12:00,SWIGGY,Swiggy,Food,258.00,DEBIT,REF1,UTR1,No,Yes,june.pdf")
    }

    @Test
    fun `a payee name containing a comma is quoted rather than splitting the row`() {
        val csv = TransactionCsv.build(listOf(row(rawPayee = "SMITH, JOHN")))

        assertThat(lines(csv)[1]).contains("\"SMITH, JOHN\"")
        // Still one row: the quoting is what stops the comma becoming a column break.
        assertThat(lines(csv).size).isEqualTo(2)
    }

    @Test
    fun `an embedded quote is doubled, the escape CSV readers expect`() {
        val csv = TransactionCsv.build(listOf(row(rawPayee = "THE \"BIG\" SHOP")))

        assertThat(lines(csv)[1]).contains("\"THE \"\"BIG\"\" SHOP\"")
    }

    @Test
    fun `a newline inside a value is quoted so the row survives`() {
        val csv = TransactionCsv.build(listOf(row(rawPayee = "LINE1\nLINE2")))

        assertThat(csv).contains("\"LINE1\nLINE2\"")
    }

    @Test
    fun `surrounding whitespace is quoted so a reader cannot silently trim it away`() {
        val csv = TransactionCsv.build(listOf(row(rawPayee = " PADDED ")))

        assertThat(lines(csv)[1]).contains("\" PADDED \"")
    }

    @Test
    fun `an unmapped payee exports empty alias and category cells rather than a guess`() {
        val csv = TransactionCsv.build(listOf(row(alias = null, category = null)))

        assertThat(lines(csv)[1]).startsWith("2026-06-01,12:00,SWIGGY,,,")
    }

    @Test
    fun `a missing reference and utr become empty cells`() {
        val csv = TransactionCsv.build(listOf(row(transactionRef = null, utr = null)))

        assertThat(lines(csv)[1]).contains(",DEBIT,,,")
    }

    @Test
    fun `amounts always carry two decimal places so the column sorts and sums`() {
        val csv = TransactionCsv.build(
            listOf(row(amountPaise = 5L), row(amountPaise = 50L), row(amountPaise = 100_000L))
        )

        assertThat(lines(csv).drop(1).map { it.split(",")[5] })
            .isEqualTo(listOf("0.05", "0.50", "1000.00"))
    }

    @Test
    fun `an excluded row is exported with Counted=No rather than being dropped`() {
        val csv = TransactionCsv.build(listOf(row(isDuplicate = true, isExcluded = true)))

        // The file agrees with the app: the row is present, and says it is not counted.
        assertThat(lines(csv).size).isEqualTo(2)
        assertThat(lines(csv)[1]).contains(",Yes,No,june.pdf")
    }

    @Test
    fun `a midnight row keeps the date it displays under, not the day before`() {
        val csv = TransactionCsv.build(listOf(row(millis = at(2026, 6, 1, hour = 0, minute = 0))))

        // Statement times are wall clock stored as-if-UTC. Converting to a local zone here would
        // move an early-morning row onto the previous date and disagree with every screen.
        assertThat(lines(csv)[1]).startsWith("2026-06-01,00:00,")
    }

    @Test
    fun `a late-evening row keeps its own date too`() {
        val csv = TransactionCsv.build(listOf(row(millis = at(2026, 6, 1, hour = 23, minute = 59))))

        assertThat(lines(csv)[1]).startsWith("2026-06-01,23:59,")
    }
}
