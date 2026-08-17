package com.madtitan94.transactionsparser.feature.upload.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.madtitan94.transactionsparser.core.domain.model.TransactionType
import com.madtitan94.transactionsparser.core.domain.parsing.ParsedTransaction
import com.madtitan94.transactionsparser.core.domain.parsing.StatementParserRegistry
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The re-import cases this feature exists for: importing the same statement twice, and importing
 * a statement whose period overlaps one already imported.
 */
class ImportDuplicateTest {

    private val sessions = FakeSessionDataSource()
    private val transactions = FakeTransactionDataSource()
    private val uploadLogs = FakeUploadLogDataSource()

    private val june = listOf(
        ParsedTransaction(10L, "Swiggy", 25_000, TransactionType.DEBIT, "T-JUN-01", "111"),
        ParsedTransaction(20L, "Blinkit", 12_000, TransactionType.DEBIT, "T-JUN-05", "222"),
        ParsedTransaction(30L, "Zomato", 40_000, TransactionType.DEBIT, "T-JUN-20", "333")
    )

    /** The first ten days of June — the overlap case, re-uploaded after the full month. */
    private val juneFirstTen = june.take(2)

    private fun useCase(parsed: List<ParsedTransaction>) = ImportStatementUseCase(
        extractor = FakeExtractor(Result.Success("PHONEPE_FIXTURE")),
        parserRegistry = StatementParserRegistry(listOf(FakeConfigurableParser(parsed))),
        sessions = sessions,
        transactions = transactions,
        payees = FakePayeeDataSource(),
        uploadLogs = uploadLogs,
        nowMillis = { 999L }
    )

    @Test
    fun `re-importing the very same statement flags every row and counts none of them twice`() =
        runTest {
            useCase(june)("/tmp/a.pdf", "june.pdf")
            val second = useCase(june)("/tmp/a.pdf", "june-again.pdf")

            assertThat((second as Result.Success).data.duplicateTransactions).isEqualTo(3)
            // Nothing is dropped — six rows are stored, three of them not counted.
            assertThat(transactions.transactions.size).isEqualTo(6)
            assertThat(transactions.transactions.count { it.isExcluded }).isEqualTo(3)
            assertThat(transactions.transactions.count { !it.isExcluded }).isEqualTo(3)
        }

    @Test
    fun `re-importing an overlapping date range flags only the overlap`() = runTest {
        useCase(june)("/tmp/june.pdf", "june.pdf")
        val second = useCase(juneFirstTen)("/tmp/june-1-10.pdf", "june-1-10.pdf")

        assertThat((second as Result.Success).data.duplicateTransactions).isEqualTo(2)
        assertThat(transactions.transactions.size).isEqualTo(5)
    }

    @Test
    fun `totals are unchanged by an overlapping re-import`() = runTest {
        useCase(june)("/tmp/june.pdf", "june.pdf")
        val totalAfterFirst = transactions.transactions
            .filterNot { it.isExcluded }
            .sumOf { it.amountPaise }

        useCase(juneFirstTen)("/tmp/june-1-10.pdf", "june-1-10.pdf")
        val totalAfterSecond = transactions.transactions
            .filterNot { it.isExcluded }
            .sumOf { it.amountPaise }

        assertThat(totalAfterSecond).isEqualTo(totalAfterFirst)
        assertThat(totalAfterSecond).isEqualTo(77_000L)
    }

    @Test
    fun `importing a genuinely new period flags nothing`() = runTest {
        useCase(june)("/tmp/june.pdf", "june.pdf")

        val july = listOf(
            ParsedTransaction(40L, "Swiggy", 25_000, TransactionType.DEBIT, "T-JUL-02", "444")
        )
        val second = useCase(july)("/tmp/july.pdf", "july.pdf")

        assertThat((second as Result.Success).data.duplicateTransactions).isEqualTo(0)
        assertThat(transactions.transactions.none { it.isExcluded }).isTrue()
    }

    @Test
    fun `the first import of a fresh database flags nothing`() = runTest {
        val result = useCase(june)("/tmp/june.pdf", "june.pdf")

        assertThat((result as Result.Success).data.duplicateTransactions).isEqualTo(0)
        assertThat(transactions.transactions.any { it.isDuplicate }).isFalse()
    }

    @Test
    fun `a flagged row records which stored transaction it repeats`() = runTest {
        useCase(june)("/tmp/june.pdf", "june.pdf")
        val originalId = transactions.transactions.first { it.transactionRef == "T-JUN-01" }.id

        useCase(juneFirstTen)("/tmp/june-1-10.pdf", "june-1-10.pdf")

        val flagged = transactions.transactions
            .last { it.transactionRef == "T-JUN-01" }
        assertThat(flagged.isDuplicate).isTrue()
        assertThat(flagged.duplicateOfTransactionId).isEqualTo(originalId)
    }
}
