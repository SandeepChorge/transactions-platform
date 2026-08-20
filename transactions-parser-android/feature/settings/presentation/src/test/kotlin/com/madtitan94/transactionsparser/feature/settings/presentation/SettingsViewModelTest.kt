package com.madtitan94.transactionsparser.feature.settings.presentation

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.startsWith
import com.madtitan94.transactionsparser.core.domain.datasource.DocumentWriter
import com.madtitan94.transactionsparser.core.domain.datasource.SessionStorage
import com.madtitan94.transactionsparser.core.domain.datasource.TransactionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.model.TransactionExportRow
import com.madtitan94.transactionsparser.core.domain.model.TransactionType
import com.madtitan94.transactionsparser.core.domain.model.UserSession
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeSessionStorage(
        initial: UserSession? = UserSession("g1", "Sandeep", "sandeep@example.com", null)
    ) : SessionStorage {
        val session = MutableStateFlow(initial)
        var cleared = false

        override fun observeSession(): Flow<UserSession?> = session

        override suspend fun save(session: UserSession): EmptyResult<DataError.Local> {
            this.session.value = session
            return Result.Success(Unit)
        }

        override suspend fun clear(): EmptyResult<DataError.Local> {
            cleared = true
            session.value = null
            return Result.Success(Unit)
        }
    }

    private class FakeDocumentWriter(
        private val result: EmptyResult<DataError.Local> = Result.Success(Unit)
    ) : DocumentWriter {
        var destination: String? = null
        var content: String? = null

        override suspend fun write(destination: String, content: String): EmptyResult<DataError.Local> {
            this.destination = destination
            this.content = content
            return result
        }
    }

    private fun exportRow(payee: String = "SWIGGY", amountPaise: Long = 25_800L) = TransactionExportRow(
        dateTimeUtcMillis = LocalDateTime.of(2026, 6, 1, 12, 0)
            .toInstant(ZoneOffset.UTC).toEpochMilli(),
        rawPayee = payee,
        alias = "Swiggy",
        category = "Food",
        amountPaise = amountPaise,
        type = TransactionType.DEBIT,
        transactionRef = "REF1",
        utr = "UTR1",
        isDuplicate = false,
        isExcluded = false,
        statementFileName = "june.pdf"
    )

    private fun viewModel(
        sessionStorage: SessionStorage = FakeSessionStorage(),
        rows: Result<List<TransactionExportRow>, DataError.Local> = Result.Success(listOf(exportRow())),
        writer: DocumentWriter = FakeDocumentWriter()
    ) = SettingsViewModel(
        sessionStorage = sessionStorage,
        transactions = FakeTransactionDataSource(rows),
        documentWriter = writer
    )

    // --- logout (moved here from Profile, where it lived before Settings existed) ---

    @Test
    fun `logout click opens confirm dialog without clearing the session`() = runTest {
        val sessionStorage = FakeSessionStorage()
        val viewModel = viewModel(sessionStorage = sessionStorage)

        viewModel.state.test {
            skipItems(1)

            viewModel.onAction(SettingsAction.OnLogoutClick)

            assertThat(awaitItem().showLogoutConfirm).isTrue()
        }
        assertThat(sessionStorage.cleared).isFalse()
    }

    @Test
    fun `confirming logout clears the session and closes the dialog`() = runTest {
        val sessionStorage = FakeSessionStorage()
        val viewModel = viewModel(sessionStorage = sessionStorage)

        viewModel.onAction(SettingsAction.OnLogoutClick)

        viewModel.state.test {
            skipItems(1)

            viewModel.onAction(SettingsAction.OnConfirmLogout)

            // Clearing the session also re-emits state from observeSession, so take the last.
            assertThat(expectMostRecentItem().showLogoutConfirm).isFalse()
        }
        assertThat(sessionStorage.cleared).isTrue()
    }

    @Test
    fun `dismissing logout confirm keeps the session`() = runTest {
        val sessionStorage = FakeSessionStorage()
        val viewModel = viewModel(sessionStorage = sessionStorage)

        viewModel.onAction(SettingsAction.OnLogoutClick)

        viewModel.state.test {
            skipItems(1)

            viewModel.onAction(SettingsAction.OnDismissLogoutConfirm)

            assertThat(awaitItem().showLogoutConfirm).isFalse()
        }
        assertThat(sessionStorage.cleared).isFalse()
    }

    // --- export ---

    @Test
    fun `export asks the screen to open a picker rather than writing anywhere itself`() = runTest {
        val writer = FakeDocumentWriter()
        val viewModel = viewModel(writer = writer)

        viewModel.events.test {
            viewModel.onAction(SettingsAction.OnExportClick)

            val event = awaitItem()
            assertThat(event).isInstanceOf(SettingsEvent.LaunchExportPicker::class)
            assertThat((event as SettingsEvent.LaunchExportPicker).suggestedFileName)
                .startsWith("transactions-")
        }
        // Nothing is written until the user has actually chosen a destination.
        assertThat(writer.destination).isNull()
    }

    @Test
    fun `choosing a destination writes the csv there`() = runTest {
        val writer = FakeDocumentWriter()
        val viewModel = viewModel(rows = Result.Success(listOf(exportRow())), writer = writer)

        viewModel.onAction(SettingsAction.OnExportDestinationChosen("content://docs/out.csv"))

        assertThat(writer.destination).isEqualTo("content://docs/out.csv")
        assertThat(writer.content!!).contains("SWIGGY")
    }

    @Test
    fun `an account with no transactions still exports a header-only file`() = runTest {
        val writer = FakeDocumentWriter()
        val viewModel = viewModel(rows = Result.Success(emptyList()), writer = writer)

        viewModel.onAction(SettingsAction.OnExportDestinationChosen("content://docs/out.csv"))

        // Refusing would be indistinguishable from a failure; an empty file is an honest answer.
        assertThat(writer.content!!.trim().lines().size).isEqualTo(1)
    }

    @Test
    fun `a failed read never writes a partial file`() = runTest {
        val writer = FakeDocumentWriter()
        val viewModel = viewModel(rows = Result.Error(DataError.Local.UNKNOWN), writer = writer)

        viewModel.events.test {
            viewModel.onAction(SettingsAction.OnExportDestinationChosen("content://docs/out.csv"))

            assertThat(awaitItem()).isInstanceOf(SettingsEvent.ShowMessage::class)
        }
        assertThat(writer.destination).isNull()
    }

    @Test
    fun `a failed write reports rather than claiming success`() = runTest {
        val writer = FakeDocumentWriter(result = Result.Error(DataError.Local.DISK_FULL))
        val viewModel = viewModel(writer = writer)

        viewModel.events.test {
            viewModel.onAction(SettingsAction.OnExportDestinationChosen("content://docs/out.csv"))

            assertThat(awaitItem()).isInstanceOf(SettingsEvent.ShowMessage::class)
        }
        assertThat(viewModel.state.value.isExporting).isFalse()
    }

    @Test
    fun `backing out of the picker clears the busy state without an error`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(SettingsAction.OnExportClick)
        assertThat(viewModel.state.value.isExporting).isTrue()

        viewModel.onAction(SettingsAction.OnExportCancelled)

        assertThat(viewModel.state.value.isExporting).isFalse()
    }

    @Test
    fun `tapping export twice does not open two pickers`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(SettingsAction.OnExportClick)
            assertThat(awaitItem()).isInstanceOf(SettingsEvent.LaunchExportPicker::class)

            viewModel.onAction(SettingsAction.OnExportClick)

            expectNoEvents()
        }
    }

    @Test
    fun `a finished export leaves the busy state clear so a second one can start`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(SettingsAction.OnExportClick)
        viewModel.onAction(SettingsAction.OnExportDestinationChosen("content://docs/out.csv"))

        assertThat(viewModel.state.value.isExporting).isFalse()
    }
}
