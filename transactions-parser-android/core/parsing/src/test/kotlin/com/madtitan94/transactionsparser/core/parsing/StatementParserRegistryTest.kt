package com.madtitan94.transactionsparser.core.parsing

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.madtitan94.transactionsparser.core.domain.model.StatementSource
import com.madtitan94.transactionsparser.core.domain.parsing.StatementParserRegistry
import org.junit.jupiter.api.Test

class StatementParserRegistryTest {

    private val registry = StatementParserRegistry(
        parsers = listOf(PhonePeStatementParser(), GooglePayStatementParser())
    )

    @Test
    fun `routes phonepe text to phonepe parser`() {
        assertThat(registry.findFor(SampleStatements.PHONEPE)?.source)
            .isEqualTo(StatementSource.PHONEPE)
    }

    @Test
    fun `routes google pay text to google pay parser`() {
        assertThat(registry.findFor(SampleStatements.GOOGLE_PAY)?.source)
            .isEqualTo(StatementSource.GOOGLE_PAY)
    }

    @Test
    fun `returns null for unrecognized documents`() {
        assertThat(registry.findFor(SampleStatements.NOT_A_STATEMENT)).isNull()
    }
}
