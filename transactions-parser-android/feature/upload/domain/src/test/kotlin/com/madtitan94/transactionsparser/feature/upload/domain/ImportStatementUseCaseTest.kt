package com.madtitan94.transactionsparser.feature.upload.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.madtitan94.transactionsparser.core.domain.model.Payee
import com.madtitan94.transactionsparser.core.domain.model.SessionStatus
import com.madtitan94.transactionsparser.core.domain.parsing.ParseError
import com.madtitan94.transactionsparser.core.domain.parsing.StatementParserRegistry
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ImportStatementUseCaseTest {

    private val sessions = FakeSessionDataSource()
    private val transactions = FakeTransactionDataSource()
    private val uploadLogs = FakeUploadLogDataSource()

    private fun useCase(
        extractorResult: Result<String, ParseError> = Result.Success("PHONEPE_FIXTURE"),
        payees: FakePayeeDataSource = FakePayeeDataSource()
    ) = ImportStatementUseCase(
        extractor = FakeExtractor(extractorResult),
        parserRegistry = StatementParserRegistry(listOf(FakePhonePeParser())),
        sessions = sessions,
        transactions = transactions,
        payees = payees,
        uploadLogs = uploadLogs,
        nowMillis = { 999L }
    )

    @Test
    fun `successful import creates pending session with transactions and logs success`() = runTest {
        val result = useCase()("/tmp/x.pdf", "statement.pdf")

        assertThat(result).isInstanceOf(Result.Success::class)
        val data = (result as Result.Success).data
        assertThat(data.totalTransactions).isEqualTo(2)
        assertThat(sessions.inserted.single().status).isEqualTo(SessionStatus.PENDING)
        assertThat(transactions.transactions.size).isEqualTo(2)
        assertThat(uploadLogs.logs.single().success).isTrue()
        assertThat(uploadLogs.logs.single().sessionId).isEqualTo(data.sessionId)
    }

    @Test
    fun `known payees are auto-mapped on re-upload`() = runTest {
        val payees = FakePayeeDataSource(
            initial = listOf(
                Payee(id = 5, rawName = "Blinkit", normalizedName = "BLINKIT", alias = "Groceries", categoryId = 1)
            )
        )

        val result = useCase(payees = payees)("/tmp/x.pdf", "statement.pdf")

        val data = (result as Result.Success).data
        assertThat(data.autoMappedPayees).isEqualTo(1)
        val blinkit = transactions.transactions.find { it.normalizedPayee == "BLINKIT" }
        assertThat(blinkit?.payeeId).isEqualTo(5L)
        val unknown = transactions.transactions.find { it.normalizedPayee == "SRI DATTA SUPER SHOPPE" }
        assertThat(unknown?.payeeId).isNull()
    }

    @Test
    fun `unrecognized document is rejected and logged`() = runTest {
        val result = useCase(extractorResult = Result.Success("random text"))("/tmp/x.pdf", "notes.pdf")

        assertThat(result).isEqualTo(Result.Error<ParseError>(ParseError.UNRECOGNIZED_FORMAT))
        assertThat(sessions.inserted.isEmpty()).isTrue()
        assertThat(uploadLogs.logs.single().success).isFalse()
        assertThat(uploadLogs.logs.single().failureReason).isEqualTo("UNRECOGNIZED_FORMAT")
    }

    @Test
    fun `password protected pdf error propagates`() = runTest {
        val result = useCase(
            extractorResult = Result.Error(ParseError.PASSWORD_PROTECTED)
        )("/tmp/x.pdf", "locked.pdf")

        assertThat(result).isEqualTo(Result.Error<ParseError>(ParseError.PASSWORD_PROTECTED))
        assertThat(uploadLogs.logs.single().failureReason).isEqualTo("PASSWORD_PROTECTED")
    }

    @Test
    fun `storage failure on session insert is reported`() = runTest {
        sessions.failOnInsert = true

        val result = useCase()("/tmp/x.pdf", "statement.pdf")

        assertThat(result).isEqualTo(Result.Error<ParseError>(ParseError.STORAGE_FAILURE))
        assertThat(uploadLogs.logs.single().success).isFalse()
    }
}
