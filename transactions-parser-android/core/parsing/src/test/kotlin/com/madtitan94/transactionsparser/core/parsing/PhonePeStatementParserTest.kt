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
    fun `extracts all five transactions`() {
        val statement = parseSuccessfully(SampleStatements.PHONEPE)
        assertThat(statement.transactions.size).isEqualTo(5)
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
        assertThat(totalPaise).isEqualTo(15_400L) // 10 + 55 + 35 + 30 + 24 = ₹154
    }
}
