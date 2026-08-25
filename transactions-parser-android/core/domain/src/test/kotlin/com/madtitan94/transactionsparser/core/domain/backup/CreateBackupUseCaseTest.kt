package com.madtitan94.transactionsparser.core.domain.backup

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.madtitan94.transactionsparser.core.domain.datasource.BackupLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.DocumentWriter
import com.madtitan94.transactionsparser.core.domain.datasource.SessionStorage
import com.madtitan94.transactionsparser.core.domain.model.AppVersion
import com.madtitan94.transactionsparser.core.domain.model.UserSession
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CreateBackupUseCaseTest {

    private class FakeBackupDataSource(
        private val result: Result<BackupSnapshot, DataError.Local> =
            Result.Success(BackupSnapshot(schemaVersion = 4, tables = sampleTables()))
    ) : BackupLocalDataSource {
        var reads = 0
        override suspend fun snapshot(): Result<BackupSnapshot, DataError.Local> {
            reads++
            return result
        }

        override suspend fun schemaVersion(): Result<Int, DataError.Local> = Result.Success(4)

        override suspend fun restore(payload: RestorePayload): Result<RestoreReport, DataError.Local> =
            error("Creating a backup never restores one")
    }

    private class FakeDocumentWriter(
        private val result: EmptyResult<DataError.Local> = Result.Success(Unit)
    ) : DocumentWriter {
        var destination: String? = null
        var content: String? = null
        var writes = 0

        override suspend fun write(destination: String, content: String): EmptyResult<DataError.Local> {
            writes++
            this.destination = destination
            this.content = content
            return result
        }
    }

    private class FakeSessionStorage(
        initial: UserSession? = UserSession("google-sub-1", "Sandeep", "someone@example.com", null)
    ) : SessionStorage {
        val session = MutableStateFlow(initial)
        override fun observeSession(): Flow<UserSession?> = session
        override suspend fun save(session: UserSession): EmptyResult<DataError.Local> = Result.Success(Unit)
        override suspend fun clear(): EmptyResult<DataError.Local> = Result.Success(Unit)
    }

    private fun useCase(
        backups: BackupLocalDataSource = FakeBackupDataSource(),
        sessionStorage: SessionStorage = FakeSessionStorage(),
        writer: DocumentWriter = FakeDocumentWriter()
    ) = CreateBackupUseCase(
        backups = backups,
        sessionStorage = sessionStorage,
        documentWriter = writer,
        appVersion = AppVersion("1.0.33", 33),
        nowMillis = { 1_756_000_000_000L }
    )

    @Test
    fun `writes an envelope describing the app, the account and the schema`() = runTest {
        val writer = FakeDocumentWriter()

        useCase(writer = writer)("content://backup.json")

        val written = BackupCodec.decode(writer.content!!)
        assertThat(written.formatVersion).isEqualTo(BACKUP_FORMAT_VERSION)
        assertThat(written.schemaVersion).isEqualTo(4)
        assertThat(written.exportedAtMillis).isEqualTo(1_756_000_000_000L)
        assertThat(written.app).isEqualTo(BackupApp("1.0.33", 33))
        assertThat(written.account).isEqualTo(BackupAccount("someone@example.com", "google-sub-1"))
        assertThat(writer.destination).isEqualTo("content://backup.json")
    }

    @Test
    fun `carries every table through to the file`() = runTest {
        val writer = FakeDocumentWriter()

        useCase(writer = writer)("content://backup.json")

        val written = BackupCodec.decode(writer.content!!)
        assertThat(written.categories).hasSize(2)
        assertThat(written.payees).hasSize(1)
        assertThat(written.payeeIdentifiers).hasSize(2)
        assertThat(written.sessions).hasSize(1)
        assertThat(written.transactions).hasSize(2)
        assertThat(written.uploadLogs).hasSize(1)
    }

    @Test
    fun `reports the counts that were written`() = runTest {
        val result = useCase()("content://backup.json")

        val summary = (result as Result.Success).data
        assertThat(summary.transactions).isEqualTo(2)
        assertThat(summary.categories).isEqualTo(2)
        assertThat(summary.payeeIdentifiers).isEqualTo(2)
        assertThat(summary.totalRows).isEqualTo(9)
    }

    @Test
    fun `writes an empty account rather than refusing`() = runTest {
        val writer = FakeDocumentWriter()
        val backups = FakeBackupDataSource(
            Result.Success(BackupSnapshot(schemaVersion = 4, tables = emptyTables()))
        )

        // A file with no rows is an honest answer to "back up my data" when there is none, and
        // refusing would be indistinguishable from a failure.
        val result = useCase(backups = backups, writer = writer)("content://backup.json")

        assertThat((result as Result.Success).data.totalRows).isEqualTo(0)
        assertThat(BackupCodec.decode(writer.content!!).transactions).hasSize(0)
    }

    @Test
    fun `still writes a backup when no session is available`() = runTest {
        val writer = FakeDocumentWriter()

        val result = useCase(
            sessionStorage = FakeSessionStorage(initial = null),
            writer = writer
        )("content://backup.json")

        // Losing the "different account" prompt on restore is a far smaller problem than
        // refusing to let someone save their data.
        assertThat(result).isInstanceOf(Result.Success::class)
        assertThat(BackupCodec.decode(writer.content!!).account).isNull()
        assertThat(BackupCodec.decode(writer.content!!).transactions).hasSize(2)
    }

    @Test
    fun `does not write anything when the database cannot be read`() = runTest {
        val writer = FakeDocumentWriter()
        val backups = FakeBackupDataSource(Result.Error(DataError.Local.UNKNOWN))

        val result = useCase(backups = backups, writer = writer)("content://backup.json")

        // A truncated or absent file is better than one the user believes is a backup.
        assertThat((result as Result.Error).error).isEqualTo(DataError.Local.UNKNOWN)
        assertThat(writer.writes).isEqualTo(0)
    }

    @Test
    fun `surfaces a write failure`() = runTest {
        val writer = FakeDocumentWriter(Result.Error(DataError.Local.DISK_FULL))

        val result = useCase(writer = writer)("content://backup.json")

        assertThat((result as Result.Error).error).isEqualTo(DataError.Local.DISK_FULL)
    }

    @Test
    fun `reads the database once per backup`() = runTest {
        val backups = FakeBackupDataSource()

        useCase(backups = backups)("content://backup.json")

        // One read means one consistent snapshot; a second would risk describing a state the
        // database was never in.
        assertThat(backups.reads).isEqualTo(1)
    }

    @Test
    fun `keeps soft-deleted rows the app would not otherwise show`() = runTest {
        val writer = FakeDocumentWriter()

        useCase(writer = writer)("content://backup.json")

        val deleted = BackupCodec.decode(writer.content!!).categories.single { it.isDeleted }
        assertThat(deleted.deletedAtMillis).isNotNull()
    }
}
