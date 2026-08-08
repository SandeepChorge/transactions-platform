package com.madtitan94.transactionsparser.core.parsing

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.madtitan94.transactionsparser.core.domain.model.TransactionType
import com.madtitan94.transactionsparser.core.domain.parsing.ParseError
import com.madtitan94.transactionsparser.core.domain.parsing.ParsedStatement
import com.madtitan94.transactionsparser.core.domain.util.Result
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class GooglePayStatementParserTest {

    private val parser = GooglePayStatementParser()

    private fun parseSuccessfully(text: String): ParsedStatement {
        val result = parser.parse(text)
        assertThat(result).isInstanceOf(Result.Success::class)
        return (result as Result.Success).data
    }

    @Test
    fun `recognizes google pay statement`() {
        assertThat(parser.canParse(SampleStatements.GOOGLE_PAY)).isTrue()
    }

    @Test
    fun `rejects phonepe statement`() {
        assertThat(parser.canParse(SampleStatements.PHONEPE)).isFalse()
    }

    @Test
    fun `rejects unrelated document`() {
        val result = parser.parse(SampleStatements.NOT_A_STATEMENT)
        assertThat(result).isEqualTo(Result.Error<ParseError>(ParseError.UNRECOGNIZED_FORMAT))
    }

    @Test
    fun `extracts both transactions`() {
        val statement = parseSuccessfully(SampleStatements.GOOGLE_PAY)
        assertThat(statement.transactions.size).isEqualTo(2)
    }

    @Test
    fun `extracts electricity bill row`() {
        val first = parseSuccessfully(SampleStatements.GOOGLE_PAY).transactions.first()

        assertThat(first.rawPayee).isEqualTo("Mahavitaran - Maharashtra Electricity (MSEDCL)")
        assertThat(first.amountPaise).isEqualTo(258_000L)
        assertThat(first.type).isEqualTo(TransactionType.DEBIT)
        assertThat(first.transactionRef).isEqualTo("652624029988")

        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(first.dateTimeUtcMillis), ZoneOffset.UTC)
        assertThat(dateTime).isEqualTo(LocalDateTime.of(2026, 6, 9, 10, 59))
    }

    @Test
    fun `extracts blinkit row with pm time`() {
        val second = parseSuccessfully(SampleStatements.GOOGLE_PAY).transactions[1]

        assertThat(second.rawPayee).isEqualTo("Blinkit")
        assertThat(second.amountPaise).isEqualTo(20_500L)
        assertThat(second.transactionRef).isEqualTo("617478704091")

        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(second.dateTimeUtcMillis), ZoneOffset.UTC)
        assertThat(dateTime).isEqualTo(LocalDateTime.of(2026, 6, 23, 21, 3))
    }

    @Test
    fun `extracts full month statement period`() {
        val statement = parseSuccessfully(SampleStatements.GOOGLE_PAY)

        val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(statement.periodStartMillis!!), ZoneOffset.UTC)
        val end = LocalDateTime.ofInstant(Instant.ofEpochMilli(statement.periodEndMillis!!), ZoneOffset.UTC)
        assertThat(start).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0))
        assertThat(end).isEqualTo(LocalDateTime.of(2026, 6, 30, 0, 0))
    }
}
