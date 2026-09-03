package com.madtitan94.transactionsparser.core.parsing

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import assertk.assertions.isFalse
import com.madtitan94.transactionsparser.core.domain.model.TransactionType
import com.madtitan94.transactionsparser.core.domain.parsing.ParseError
import com.madtitan94.transactionsparser.core.domain.parsing.ParsedStatement
import com.madtitan94.transactionsparser.core.domain.util.Result
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class PhonePeStatementParserTest {

    private val parser = PhonePeStatementParser()

    private fun parseSuccessfully(text: String): ParsedStatement {
        val result = parser.parse(text)
        assertThat(result).isInstanceOf(Result.Success::class)
        return (result as Result.Success).data
    }

    @Test
    fun `recognizes phonepe statement`() {
        assertThat(parser.canParse(SampleStatements.PHONEPE)).isTrue()
    }

    @Test
    fun `rejects google pay statement`() {
        assertThat(parser.canParse(SampleStatements.GOOGLE_PAY)).isFalse()
    }

    @Test
    fun `rejects unrelated document`() {
        val result = parser.parse(SampleStatements.NOT_A_STATEMENT)
        assertThat(result).isEqualTo(Result.Error<ParseError>(ParseError.UNRECOGNIZED_FORMAT))
    }

    @Test
    fun `extracts every transaction in the statement`() {
        val statement = parseSuccessfully(SampleStatements.PHONEPE)
        assertThat(statement.transactions.size).isEqualTo(6)
    }

    @Test
    fun `extracts payee amount type and references`() {
        val first = parseSuccessfully(SampleStatements.PHONEPE).transactions.first()

        assertThat(first.rawPayee).isEqualTo("SRI DATTA SUPER SHOPPE")
        assertThat(first.amountPaise).isEqualTo(1_000L)
        assertThat(first.type).isEqualTo(TransactionType.DEBIT)
        assertThat(first.transactionRef).isEqualTo("T2607031346510066848829")
        assertThat(first.utr).isEqualTo("930722951778")
    }

    @Test
    fun `extracts date and time`() {
        val first = parseSuccessfully(SampleStatements.PHONEPE).transactions.first()
        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(first.dateTimeUtcMillis), ZoneOffset.UTC)

        assertThat(dateTime).isEqualTo(LocalDateTime.of(2026, 7, 3, 13, 46))
    }

    @Test
    fun `extracts statement period`() {
        val statement = parseSuccessfully(SampleStatements.PHONEPE)

        assertThat(statement.periodStartMillis).isNotNull()
        val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(statement.periodStartMillis!!), ZoneOffset.UTC)
        val end = LocalDateTime.ofInstant(Instant.ofEpochMilli(statement.periodEndMillis!!), ZoneOffset.UTC)
        assertThat(start).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0))
        assertThat(end).isEqualTo(LocalDateTime.of(2026, 7, 31, 0, 0))
    }

    @Test
    fun `sums to statement total`() {
        val statement = parseSuccessfully(SampleStatements.PHONEPE)
        val totalPaise = statement.transactions.sumOf { it.amountPaise }
        // 10 + 55 + 30 + 1,287.89 + 904 + 65 = ₹2,351.89
        assertThat(totalPaise).isEqualTo(235_189L)
    }

    /**
     * The details column is printed before the type column, so a search for a bare DEBIT/CREDIT
     * token finds one inside the payee first. Direction must come from the Type column alone.
     */
    @Test
    fun `reads direction from the type column and not from the payee name`() {
        val row = parseSuccessfully(SampleStatements.PHONEPE)
            .transactions.single { it.rawPayee == "SHRIRAM CREDIT CO-OP SOCIETY" }

        assertThat(row.type).isEqualTo(TransactionType.DEBIT)
    }

    @Test
    fun `reads a credit from the type column`() {
        val row = parseSuccessfully(SampleStatements.PHONEPE)
            .transactions.single { it.rawPayee == "PARVATI MAHENDRA DAS" }

        assertThat(row.type).isEqualTo(TransactionType.CREDIT)
        assertThat(row.amountPaise).isEqualTo(3_000L)
    }

    /**
     * PhonePe writes at least four opening phrases and may add more. An unrecognised one keeps its
     * details text verbatim so the row survives; the user renames it through the alias flow.
     */
    @Test
    fun `keeps rows whose opening phrase is not recognised`() {
        val transactions = parseSuccessfully(SampleStatements.PHONEPE).transactions

        val billPayment = transactions.single { it.rawPayee == "Payment to Acme Insurance Brokers" }
        assertThat(billPayment.amountPaise).isEqualTo(128_789L)
        assertThat(billPayment.type).isEqualTo(TransactionType.DEBIT)

        val recharge = transactions.single { it.rawPayee == "Mobile recharged 9000000001" }
        assertThat(recharge.amountPaise).isEqualTo(90_400L)
    }

    @Test
    fun `recovers a payee that wrapped onto the next line`() {
        val row = parseSuccessfully(SampleStatements.PHONEPE)
            .transactions.single { it.amountPaise == 6_500L }

        assertThat(row.rawPayee).isEqualTo("TOPIC PRODUCTION PRIVATE LIMITED")
    }

    /** Bill payments and recharges reference OLEX…/NX… rather than T…. */
    @Test
    fun `captures transaction references that are not T ids`() {
        val transactions = parseSuccessfully(SampleStatements.PHONEPE).transactions

        assertThat(transactions.single { it.amountPaise == 128_789L }.transactionRef)
            .isEqualTo("OLEX2607021441483611905461")
        assertThat(transactions.single { it.amountPaise == 90_400L }.transactionRef)
            .isEqualTo("NX2607011100374975945309")
    }

    /**
     * PdfBox emits the same statement with the Type/Amount columns either on the row's line or
     * after its reference lines, depending on page layout. Both must produce identical rows —
     * this is the shape difference that a fixture written by hand cannot represent.
     */
    @Test
    fun `parses identically whichever column order pdfbox emits`() {
        val inline = parseSuccessfully(SampleStatements.PHONEPE).transactions
        val columnsLast = parseSuccessfully(SampleStatements.PHONEPE_COLUMNS_LAST).transactions

        assertThat(columnsLast).isEqualTo(inline)
    }
}
