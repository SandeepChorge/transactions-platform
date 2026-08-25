package com.madtitan94.transactionsparser.feature.settings.presentation

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.endsWith
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.startsWith
import com.madtitan94.transactionsparser.core.domain.backup.BACKUP_FORMAT_VERSION
import com.madtitan94.transactionsparser.core.domain.backup.BackupAccount
import com.madtitan94.transactionsparser.core.domain.backup.BackupApp
import com.madtitan94.transactionsparser.core.domain.backup.BackupCategory
import com.madtitan94.transactionsparser.core.domain.backup.BackupCodec
import com.madtitan94.transactionsparser.core.domain.backup.BackupFile
import com.madtitan94.transactionsparser.core.domain.backup.BackupPayee
import com.madtitan94.transactionsparser.core.domain.backup.BackupPayeeIdentifier
import com.madtitan94.transactionsparser.core.domain.backup.BackupSession
import com.madtitan94.transactionsparser.core.domain.backup.BackupSnapshot
import com.madtitan94.transactionsparser.core.domain.backup.BackupTables
import com.madtitan94.transactionsparser.core.domain.backup.BackupTransaction
import com.madtitan94.transactionsparser.core.domain.backup.CreateBackupUseCase
import com.madtitan94.transactionsparser.core.domain.backup.ReadBackupUseCase
import com.madtitan94.transactionsparser.core.domain.backup.RestoreBackupUseCase
import com.madtitan94.transactionsparser.core.domain.backup.RestorePayload
import com.madtitan94.transactionsparser.core.domain.backup.RestoreReport
import com.madtitan94.transactionsparser.core.domain.datasource.BackupLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.DocumentReader
import com.madtitan94.transactionsparser.core.domain.datasource.DocumentWriter
import com.madtitan94.transactionsparser.core.domain.datasource.SessionStorage
import com.madtitan94.transactionsparser.core.domain.datasource.TransactionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.model.AppVersion
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

    private class FakeBackupDataSource(
        private val result: Result<BackupSnapshot, DataError.Local> =
            Result.Success(BackupSnapshot(schemaVersion = 4, tables = backupTables())),
        private val restoreResult: Result<RestoreReport, DataError.Local> =
            Result.Success(restoreReport()),
        private val supportedSchemaVersion: Int = 4
    ) : BackupLocalDataSource {
        var restored: RestorePayload? = null

        override suspend fun snapshot(): Result<BackupSnapshot, DataError.Local> = result

        override suspend fun schemaVersion(): Result<Int, DataError.Local> =
            Result.Success(supportedSchemaVersion)

        override suspend fun restore(payload: RestorePayload): Result<RestoreReport, DataError.Local> {
            restored = payload
            return restoreResult
        }
    }

    private class FakeDocumentReader(
        private val result: Result<String, DataError.Local> = Result.Success(backupJson())
    ) : DocumentReader {
        var source: String? = null

        override suspend fun read(source: String): Result<String, DataError.Local> {
            this.source = source
            return result
        }
    }

    /**
     * One transaction and the payee it belongs to — enough to tell a written backup apart from an
     * empty one. The exhaustive round-trip fixtures live with the codec's own tests in core:domain;
     * this only needs to prove the ViewModel reached the use case.
     */
    private companion object {
        fun backupTables(transactions: Int = 1) = BackupTables(
            categories = listOf(BackupCategory(1L, "Food", isDeleted = false, deletedAtMillis = null)),
            payees = listOf(BackupPayee(10L, "Swiggy", 1L, isDeleted = false, deletedAtMillis = null)),
            payeeIdentifiers = listOf(BackupPayeeIdentifier(100L, 10L, "SWIGGY", "SWIGGY")),
            sessions = listOf(
                BackupSession(
                    id = 1000L,
                    fileName = "june.pdf",
                    source = "PHONEPE",
                    uploadedAtMillis = 0L,
                    periodStartMillis = null,
                    periodEndMillis = null,
                    status = "COMPLETED",
                    isDeleted = false,
                    deletedAtMillis = null
                )
            ),
            transactions = (1..transactions).map {
                BackupTransaction(
                    id = it.toLong(),
                    sessionId = 1000L,
                    dateTimeUtcMillis = 0L,
                    rawPayee = "SWIGGY",
                    normalizedPayee = "SWIGGY",
                    amountPaise = 25_800L,
                    type = "DEBIT",
                    transactionRef = "REF$it",
                    utr = "UTR$it",
                    payeeId = 10L,
                    isDuplicate = false,
                    duplicateOfTransactionId = null,
                    isExcluded = false,
                    isDeleted = false,
                    deletedAtMillis = null
                )
            },
            uploadLogs = emptyList()
        )

        /** The same account as a file, ready for the restore path to read back. */
        fun backupJson(googleId: String = "g1", transactions: Int = 1): String {
            val tables = backupTables(transactions)
            return BackupCodec.encode(
                BackupFile(
                    formatVersion = BACKUP_FORMAT_VERSION,
                    schemaVersion = 4,
                    exportedAtMillis = 1_756_000_000_000L,
                    app = BackupApp("1.0.0", 1),
                    account = BackupAccount("someone@example.com", googleId),
                    categories = tables.categories,
                    payees = tables.payees,
                    payeeIdentifiers = tables.payeeIdentifiers,
                    sessions = tables.sessions,
                    transactions = tables.transactions,
                    uploadLogs = tables.uploadLogs
                )
            )
        }

        fun restoreReport(transactions: Int = 1) = RestoreReport(
            categoriesInserted = 1,
            categoriesReused = 0,
            payees = 1,
            payeeIdentifiers = 1,
            sessions = 1,
            transactions = transactions,
            uploadLogs = 0,
            duplicatesFlagged = 0,
            identifierConflicts = emptyList()
        )
    }

    private fun viewModel(
        sessionStorage: SessionStorage = FakeSessionStorage(),
        rows: Result<List<TransactionExportRow>, DataError.Local> = Result.Success(listOf(exportRow())),
        writer: DocumentWriter = FakeDocumentWriter(),
        backups: BackupLocalDataSource = FakeBackupDataSource(),
        backupWriter: DocumentWriter = writer,
        reader: DocumentReader = FakeDocumentReader()
    ): SettingsViewModel {
        val transactions = FakeTransactionDataSource(rows)
        return SettingsViewModel(
            sessionStorage = sessionStorage,
            transactions = transactions,
            documentWriter = writer,
            // The real use cases rather than stand-ins: they are pure given these fakes, and a fake
            // here would prove only that the ViewModel calls something. It also means these tests
            // exercise real validation, so a file the validator would reject is rejected here too.
            createBackup = CreateBackupUseCase(
                backups = backups,
                sessionStorage = sessionStorage,
                documentWriter = backupWriter,
                appVersion = AppVersion("1.0.0", 1)
            ),
            readBackup = ReadBackupUseCase(
                documentReader = reader,
                backups = backups,
                sessionStorage = sessionStorage
            ),
            restoreBackup = RestoreBackupUseCase(backups = backups, transactions = transactions)
        )
    }

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

    // --- backup ---

    @Test
    fun `backup asks the screen to open its own picker, suggesting a json file`() = runTest {
        val writer = FakeDocumentWriter()
        val viewModel = viewModel(writer = writer)

        viewModel.events.test {
            viewModel.onAction(SettingsAction.OnBackupClick)

            val event = awaitItem()
            assertThat(event).isInstanceOf(SettingsEvent.LaunchBackupPicker::class)
            assertThat((event as SettingsEvent.LaunchBackupPicker).suggestedFileName)
                .startsWith("transactions-backup-")
            assertThat(event.suggestedFileName).endsWith(".json")
        }
        assertThat(writer.destination).isNull()
    }

    @Test
    fun `choosing a destination writes a backup there`() = runTest {
        val writer = FakeDocumentWriter()
        val viewModel = viewModel(writer = writer)

        viewModel.onAction(SettingsAction.OnBackupDestinationChosen("content://docs/backup.json"))

        assertThat(writer.destination).isEqualTo("content://docs/backup.json")
        // The whole account, not the flattened export view — a category the CSV would never
        // mention on its own is proof the backup went through the other path.
        assertThat(writer.content!!).contains("\"categories\"")
        assertThat(writer.content!!).contains("\"formatVersion\"")
    }

    @Test
    fun `a backup that cannot be read never writes a partial file`() = runTest {
        val writer = FakeDocumentWriter()
        val viewModel = viewModel(
            writer = writer,
            backups = FakeBackupDataSource(Result.Error(DataError.Local.UNKNOWN))
        )

        viewModel.events.test {
            viewModel.onAction(SettingsAction.OnBackupDestinationChosen("content://docs/backup.json"))

            assertThat(awaitItem()).isInstanceOf(SettingsEvent.ShowMessage::class)
        }
        assertThat(writer.destination).isNull()
    }

    @Test
    fun `a failed backup write reports rather than claiming success`() = runTest {
        val writer = FakeDocumentWriter(result = Result.Error(DataError.Local.DISK_FULL))
        val viewModel = viewModel(writer = writer)

        viewModel.events.test {
            viewModel.onAction(SettingsAction.OnBackupDestinationChosen("content://docs/backup.json"))

            assertThat(awaitItem()).isInstanceOf(SettingsEvent.ShowMessage::class)
        }
        assertThat(viewModel.state.value.isBackingUp).isFalse()
    }

    @Test
    fun `tapping backup twice does not open two pickers`() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onAction(SettingsAction.OnBackupClick)
            assertThat(awaitItem()).isInstanceOf(SettingsEvent.LaunchBackupPicker::class)

            viewModel.onAction(SettingsAction.OnBackupClick)

            expectNoEvents()
        }
    }

    @Test
    fun `backing out of the backup picker clears the busy state without an error`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(SettingsAction.OnBackupClick)
        viewModel.onAction(SettingsAction.OnBackupCancelled)

        assertThat(viewModel.state.value.isBackingUp).isFalse()
    }

    // --- restore ---

    @Test
    fun `restore asks the screen to open a picker and reads nothing yet`() = runTest {
        val reader = FakeDocumentReader()
        val viewModel = viewModel(reader = reader)

        viewModel.events.test {
            viewModel.onAction(SettingsAction.OnRestoreClick)

            assertThat(awaitItem()).isInstanceOf(SettingsEvent.LaunchRestorePicker::class)
        }
        assertThat(reader.source).isNull()
    }

    @Test
    fun `a backup from this account goes straight to the summary`() = runTest {
        val backups = FakeBackupDataSource()
        val viewModel = viewModel(backups = backups)

        viewModel.onAction(SettingsAction.OnRestoreSourceChosen("content://docs/backup.json"))

        val stage = viewModel.state.value.restore
        assertThat(stage).isInstanceOf(RestoreStage.Confirm::class)
        assertThat((stage as RestoreStage.Confirm).preview.summary.transactions).isEqualTo(1)
        // Reading and validating writes nothing; only confirming does.
        assertThat(backups.restored).isNull()
    }

    @Test
    fun `a backup from another account stops to say so before showing the summary`() = runTest {
        val backups = FakeBackupDataSource()
        val viewModel = viewModel(
            backups = backups,
            reader = FakeDocumentReader(Result.Success(backupJson(googleId = "someone-else")))
        )

        viewModel.onAction(SettingsAction.OnRestoreSourceChosen("content://docs/backup.json"))
        assertThat(viewModel.state.value.restore).isInstanceOf(RestoreStage.AccountMismatch::class)

        viewModel.onAction(SettingsAction.OnRestoreAccountAccepted)

        // Accepting whose data it is only moves on to the summary — it is not consent to write.
        assertThat(viewModel.state.value.restore).isInstanceOf(RestoreStage.Confirm::class)
        assertThat(backups.restored).isNull()
    }

    @Test
    fun `confirming writes the backup and reports what it did`() = runTest {
        val backups = FakeBackupDataSource()
        val viewModel = viewModel(backups = backups)

        viewModel.onAction(SettingsAction.OnRestoreSourceChosen("content://docs/backup.json"))
        viewModel.onAction(SettingsAction.OnRestoreConfirmed)

        val stage = viewModel.state.value.restore
        assertThat(stage).isInstanceOf(RestoreStage.Done::class)
        assertThat((stage as RestoreStage.Done).report.transactions).isEqualTo(1)
        assertThat(backups.restored!!.transactions).hasSize(1)
    }

    @Test
    fun `backing out at the summary writes nothing`() = runTest {
        val backups = FakeBackupDataSource()
        val viewModel = viewModel(backups = backups)

        viewModel.onAction(SettingsAction.OnRestoreSourceChosen("content://docs/backup.json"))
        viewModel.onAction(SettingsAction.OnRestoreDismissed)

        assertThat(viewModel.state.value.restore).isEqualTo(RestoreStage.Idle)
        assertThat(backups.restored).isNull()
        // And the flow is free to start again rather than stuck busy.
        viewModel.events.test {
            viewModel.onAction(SettingsAction.OnRestoreClick)
            assertThat(awaitItem()).isInstanceOf(SettingsEvent.LaunchRestorePicker::class)
        }
    }

    @Test
    fun `a file that is not a backup is reported and nothing is written`() = runTest {
        val backups = FakeBackupDataSource()
        val viewModel = viewModel(
            backups = backups,
            reader = FakeDocumentReader(Result.Success("Date,Time,Payee\n2026-06-01,12:00,X"))
        )

        viewModel.events.test {
            viewModel.onAction(SettingsAction.OnRestoreSourceChosen("content://docs/notes.csv"))

            assertThat(awaitItem()).isInstanceOf(SettingsEvent.ShowMessage::class)
        }
        assertThat(viewModel.state.value.restore).isEqualTo(RestoreStage.Idle)
        assertThat(backups.restored).isNull()
    }

    @Test
    fun `a backup written by a newer schema is refused before the summary`() = runTest {
        val viewModel = viewModel(backups = FakeBackupDataSource(supportedSchemaVersion = 3))

        viewModel.events.test {
            viewModel.onAction(SettingsAction.OnRestoreSourceChosen("content://docs/backup.json"))

            assertThat(awaitItem()).isInstanceOf(SettingsEvent.ShowMessage::class)
        }
        assertThat(viewModel.state.value.restore).isEqualTo(RestoreStage.Idle)
    }

    @Test
    fun `a failed write leaves the flow where it started rather than claiming success`() = runTest {
        val backups = FakeBackupDataSource(restoreResult = Result.Error(DataError.Local.DISK_FULL))
        val viewModel = viewModel(backups = backups)

        viewModel.onAction(SettingsAction.OnRestoreSourceChosen("content://docs/backup.json"))

        viewModel.events.test {
            viewModel.onAction(SettingsAction.OnRestoreConfirmed)

            assertThat(awaitItem()).isInstanceOf(SettingsEvent.ShowMessage::class)
        }
        // The write is one transaction, so a failure means nothing landed and Idle is the truth.
        assertThat(viewModel.state.value.restore).isEqualTo(RestoreStage.Idle)
    }

    @Test
    fun `export and backup track their busy states separately`() = runTest {
        val viewModel = viewModel()

        viewModel.onAction(SettingsAction.OnExportClick)

        // Both rows show a spinner otherwise, which reads as the app doing two things at once.
        assertThat(viewModel.state.value.isExporting).isTrue()
        assertThat(viewModel.state.value.isBackingUp).isFalse()
    }
}
