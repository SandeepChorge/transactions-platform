package com.madtitan94.transactionsparser.core.domain.backup

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.madtitan94.transactionsparser.core.domain.model.UserSession
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The half of a restore that happens before the user has agreed to anything. Nothing here may
 * write, and the numbers it produces are what the confirmation screen shows — so they have to be
 * the file's real contents rather than anything approximate.
 */
class ReadBackupUseCaseTest {

    private fun useCase(
        reader: FakeDocumentReader = FakeDocumentReader(),
        backups: RecordingBackupDataSource = RecordingBackupDataSource(),
        session: UserSession? = UserSession("google-sub-1", "Sandeep", "someone@example.com", null)
    ) = ReadBackupUseCase(
        documentReader = reader,
        backups = backups,
        sessionStorage = FakeRestoreSessionStorage(session)
    )

    @Test
    fun `describes the file the user picked`() = runTest {
        val result = useCase()("content://docs/backup.json")

        val preview = (result as Result.Success).data
        assertThat(preview.summary.transactions).isEqualTo(2)
        assertThat(preview.summary.categories).isEqualTo(2)
        assertThat(preview.summary.payeeIdentifiers).isEqualTo(2)
        assertThat(preview.appVersionName).isEqualTo("1.0.33")
        assertThat(preview.exportedAtMillis).isEqualTo(1_756_000_000_000L)
    }

    @Test
    fun `says nothing about accounts when the backup is this account's own`() = runTest {
        val preview = (useCase()("content://docs/backup.json") as Result.Success).data

        assertThat(preview.isDifferentAccount).isFalse()
    }

    @Test
    fun `flags a backup exported by another account`() = runTest {
        val result = useCase(
            session = UserSession("google-sub-2", "Someone", "other@example.com", null)
        )("content://docs/backup.json")

        // Restoring it is allowed and is the point of the feature; being surprised by it is not.
        assertThat((result as Result.Success).data.isDifferentAccount).isTrue()
    }

    @Test
    fun `does not flag an account mismatch when the file names no account`() = runTest {
        val reader = FakeDocumentReader(
            Result.Success(BackupCodec.encode(backupFile(account = null)))
        )

        val preview = (useCase(reader = reader)("content://docs/backup.json") as Result.Success).data

        // Stopping a restore over missing metadata would be worse than the surprise it prevents.
        assertThat(preview.isDifferentAccount).isFalse()
    }

    @Test
    fun `reports a file it could not read as such rather than as a bad backup`() = runTest {
        val reader = FakeDocumentReader(Result.Error(DataError.Local.NOT_FOUND))

        val result = useCase(reader = reader)("content://docs/gone.json")

        assertThat((result as Result.Error).error).isEqualTo(BackupError.CouldNotRead)
    }

    @Test
    fun `refuses a backup written by a newer database than this build has`() = runTest {
        val result = useCase(
            backups = RecordingBackupDataSource(supportedSchemaVersion = 3)
        )("content://docs/backup.json")

        assertThat((result as Result.Error).error)
            .isEqualTo(BackupError.NewerSchema(fileSchemaVersion = 4, supportedSchemaVersion = 3))
    }

    @Test
    fun `hands on the corrected file, not the one on disk`() = runTest {
        val stale = backupFile(
            tables = sampleTables(
                payeeIdentifiers = listOf(
                    BackupPayeeIdentifier(1L, 10L, rawName = "corner  cafe", normalizedName = "wrong")
                )
            )
        )
        val reader = FakeDocumentReader(Result.Success(BackupCodec.encode(stale)))

        val preview = (useCase(reader = reader)("content://docs/backup.json") as Result.Success).data

        assertThat(preview.file.payeeIdentifiers.single().normalizedName).isEqualTo("CORNER CAFE")
    }

    @Test
    fun `writes nothing while reading`() = runTest {
        val backups = RecordingBackupDataSource()

        useCase(backups = backups)("content://docs/backup.json")

        assertThat(backups.restores).isEqualTo(0)
    }
}
