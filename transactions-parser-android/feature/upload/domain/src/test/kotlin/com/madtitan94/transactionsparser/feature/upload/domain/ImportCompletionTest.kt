package com.madtitan94.transactionsparser.feature.upload.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.madtitan94.transactionsparser.core.domain.model.Payee
import com.madtitan94.transactionsparser.core.domain.model.SessionStatus
import com.madtitan94.transactionsparser.core.domain.model.TransactionType
import com.madtitan94.transactionsparser.core.domain.parsing.ParsedTransaction
import com.madtitan94.transactionsparser.core.domain.parsing.StatementParserRegistry
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Completion is normally driven by saving a mapping, but an import can leave nothing to map —
 * every payee already known, or every row a flagged repeat. Those sessions used to sit at PENDING
 * forever with no control on screen capable of finishing them.
 */
class ImportCompletionTest {

    private val sessions = FakeSessionDataSource()
    private val transactions = FakeTransactionDataSource()
    private val uploadLogs = FakeUploadLogDataSource()

    private val june = listOf(
        ParsedTransaction(10L, "Swiggy", 25_000, TransactionType.DEBIT, "T-JUN-01", "111"),
        ParsedTransaction(20L, "Blinkit", 12_000, TransactionType.DEBIT, "T-JUN-05", "222")
    )

    private fun useCase(
        parsed: List<ParsedTransaction>,
        payees: FakePayeeDataSource = FakePayeeDataSource()
    ) = ImportStatementUseCase(
        extractor = FakeExtractor(Result.Success("PHONEPE_FIXTURE")),
        parserRegistry = StatementParserRegistry(listOf(FakeConfigurableParser(parsed))),
        sessions = sessions,
        transactions = transactions,
        payees = payees,
        uploadLogs = uploadLogs,
        nowMillis = { 999L }
    )

    private fun knownPayees(vararg rawNames: String) = FakePayeeDataSource(
        rawNames.mapIndexed { index, raw ->
            Payee(
                id = index + 1L,
                rawName = raw,
                normalizedName = raw.uppercase(),
                alias = raw,
                categoryId = 1L
            )
        }
    )

    @Test
    fun `a statement that is entirely duplicates completes instead of waiting for a mapping`() =
        runTest {
            useCase(june)("/tmp/june.pdf", "june.pdf")
            val second = useCase(june)("/tmp/june.pdf", "june-again.pdf")

            val result = (second as Result.Success).data
            assertThat(result.duplicateTransactions).isEqualTo(2)
            assertThat(result.completedOnImport).isTrue()
            assertThat(sessions.statusOf(result.sessionId)).isEqualTo(SessionStatus.COMPLETED)
        }

    @Test
    fun `a statement whose payees are all already known completes on import`() = runTest {
        // No duplicates at all here — genuinely new spending, every payee mapped in an earlier
        // session, so every row arrives auto-assigned and there is nothing left to confirm.
        val result = useCase(june, knownPayees("Swiggy", "Blinkit"))("/tmp/june.pdf", "june.pdf")

        val data = (result as Result.Success).data
        assertThat(data.duplicateTransactions).isEqualTo(0)
        assertThat(data.autoMappedPayees).isEqualTo(2)
        assertThat(data.completedOnImport).isTrue()
        assertThat(sessions.statusOf(data.sessionId)).isEqualTo(SessionStatus.COMPLETED)
    }

    @Test
    fun `a statement with work left to do stays pending`() = runTest {
        val result = useCase(june, knownPayees("Swiggy"))("/tmp/june.pdf", "june.pdf")

        val data = (result as Result.Success).data
        assertThat(data.completedOnImport).isFalse()
        assertThat(sessions.statusOf(data.sessionId)).isEqualTo(SessionStatus.PENDING)
    }

    @Test
    fun `a partly-duplicate statement stays pending on the rows that still need mapping`() =
        runTest {
            useCase(june)("/tmp/june.pdf", "june.pdf")
            val july = june + ParsedTransaction(
                30L, "Zomato", 40_000, TransactionType.DEBIT, "T-JUL-02", "333"
            )

            val second = useCase(july)("/tmp/july.pdf", "july.pdf")

            val data = (second as Result.Success).data
            assertThat(data.duplicateTransactions).isEqualTo(2)
            assertThat(data.completedOnImport).isFalse()
            assertThat(sessions.statusOf(data.sessionId)).isEqualTo(SessionStatus.PENDING)
        }

    @Test
    fun `a statement that parses to no transactions completes rather than stranding`() = runTest {
        val result = useCase(emptyList())("/tmp/empty.pdf", "empty.pdf")

        val data = (result as Result.Success).data
        assertThat(data.totalTransactions).isEqualTo(0)
        assertThat(sessions.statusOf(data.sessionId)).isEqualTo(SessionStatus.COMPLETED)
    }

    @Test
    fun `a failed count leaves the session pending rather than completing it blindly`() = runTest {
        transactions.failOnUnmappedCount = true

        val result = useCase(june, knownPayees("Swiggy", "Blinkit"))("/tmp/june.pdf", "june.pdf")

        val data = (result as Result.Success).data
        assertThat(data.completedOnImport).isFalse()
        assertThat(sessions.statusOf(data.sessionId)).isEqualTo(SessionStatus.PENDING)
    }
}
