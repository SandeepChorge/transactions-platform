package com.madtitan94.transactionsparser.core.domain.backup

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test

/**
 * The gate every file passes through before anything is written.
 *
 * Each of these is a way a restore could otherwise get halfway through and stop, leaving an account
 * in a state nobody asked for — so the assertions are as much about *nothing having happened* as
 * about the error being right.
 */
class BackupValidatorTest {

    private fun validate(file: BackupFile, supportedSchemaVersion: Int = 4) =
        BackupValidator.validate(BackupCodec.encode(file), supportedSchemaVersion)

    private fun errorFrom(file: BackupFile, supportedSchemaVersion: Int = 4): BackupError =
        (validate(file, supportedSchemaVersion) as Result.Error).error

    @Test
    fun `accepts a file this app wrote`() {
        val result = validate(backupFile())

        val accepted = (result as Result.Success).data
        assertThat(accepted.transactions).hasSize(2)
        assertThat(accepted.payeeIdentifiers).hasSize(2)
    }

    @Test
    fun `accepts an empty account`() {
        assertThat(validate(backupFile(tables = emptyTables()))).isInstanceOf(Result.Success::class)
    }

    @Test
    fun `rejects text that is not a backup`() {
        val result = BackupValidator.validate("not json at all", supportedSchemaVersion = 4)

        assertThat((result as Result.Error).error).isEqualTo(BackupError.NotABackup)
    }

    @Test
    fun `rejects a file with a table key missing rather than treating it as empty`() {
        val withoutSessions = BackupCodec.encode(backupFile())
            .let { Json.parseToJsonElement(it).jsonObject }
            .filterKeys { it != "sessions" }
            .let { Json.encodeToString(JsonObject(it)) }

        val result = BackupValidator.validate(withoutSessions, supportedSchemaVersion = 4)

        assertThat((result as Result.Error).error).isEqualTo(BackupError.NotABackup)
    }

    @Test
    fun `rejects a format version this build does not know`() {
        val error = errorFrom(backupFile(formatVersion = BACKUP_FORMAT_VERSION + 1))

        assertThat(error).isEqualTo(BackupError.UnsupportedFormat(BACKUP_FORMAT_VERSION + 1))
    }

    @Test
    fun `rejects a backup from a newer database than this build has`() {
        val error = errorFrom(backupFile(schemaVersion = 5), supportedSchemaVersion = 4)

        assertThat(error).isEqualTo(BackupError.NewerSchema(fileSchemaVersion = 5, supportedSchemaVersion = 4))
    }

    @Test
    fun `accepts a backup from an older database`() {
        // Older is what this feature exists for — the file is read as the format it is.
        assertThat(validate(backupFile(schemaVersion = 2))).isInstanceOf(Result.Success::class)
    }

    @Test
    fun `rejects a transaction pointing at a session the file does not contain`() {
        val tables = sampleTables(
            transactions = sampleTables().transactions.map { it.copy(sessionId = 9_999L) }
        )

        val error = errorFrom(backupFile(tables = tables))

        assertThat(error).isInstanceOf(BackupError.BrokenReferences::class)
        assertThat((error as BackupError.BrokenReferences).count).isEqualTo(2)
    }

    @Test
    fun `rejects a payee pointing at a category the file does not contain`() {
        val tables = sampleTables(payees = sampleTables().payees.map { it.copy(categoryId = 77L) })

        assertThat(errorFrom(backupFile(tables = tables)))
            .isInstanceOf(BackupError.BrokenReferences::class)
    }

    @Test
    fun `rejects a duplicate back-link pointing at a transaction the file does not contain`() {
        val tables = sampleTables(
            transactions = sampleTables().transactions.map {
                if (it.isDuplicate) it.copy(duplicateOfTransactionId = 4_242L) else it
            }
        )

        // Left unchecked this row would restore with a back-link to whichever local transaction
        // happened to be given that id.
        assertThat(errorFrom(backupFile(tables = tables)))
            .isInstanceOf(BackupError.BrokenReferences::class)
    }

    @Test
    fun `rejects repeated ids within one table`() {
        val first = sampleTables().categories.first()
        val tables = sampleTables(categories = listOf(first, first.copy(name = "Other")))

        assertThat(errorFrom(backupFile(tables = tables)))
            .isEqualTo(BackupError.DuplicateIds("categories", 1))
    }

    @Test
    fun `rejects a transaction type this build has no meaning for`() {
        val tables = sampleTables(
            transactions = sampleTables().transactions.map { it.copy(type = "REFUND") }
        )

        assertThat(errorFrom(backupFile(tables = tables)))
            .isEqualTo(BackupError.UnknownValue("transactions.type", "REFUND"))
    }

    @Test
    fun `rejects a statement source this build has no parser for`() {
        val tables = sampleTables(
            sessions = sampleTables().sessions.map { it.copy(source = "PAYTM") }
        )

        assertThat(errorFrom(backupFile(tables = tables)))
            .isEqualTo(BackupError.UnknownValue("sessions.source", "PAYTM"))
    }

    @Test
    fun `accepts an upload log with no source, which is how a failed upload is recorded`() {
        val tables = sampleTables(
            uploadLogs = sampleTables().uploadLogs.map {
                it.copy(source = null, success = false, failureReason = "UNRECOGNIZED_FORMAT")
            }
        )

        assertThat(validate(backupFile(tables = tables))).isInstanceOf(Result.Success::class)
    }

    @Test
    fun `rejects a negative timestamp`() {
        val tables = sampleTables(
            transactions = sampleTables().transactions.map { it.copy(dateTimeUtcMillis = -1L) }
        )

        assertThat(errorFrom(backupFile(tables = tables)))
            .isInstanceOf(BackupError.InvalidValue::class)
    }

    @Test
    fun `recomputes normalized names rather than trusting the file`() {
        val tables = sampleTables(
            payeeIdentifiers = listOf(
                BackupPayeeIdentifier(id = 1L, payeeId = 10L, rawName = "corner  cafe", normalizedName = "stale"),
                BackupPayeeIdentifier(id = 2L, payeeId = 10L, rawName = "OTHER SHOP", normalizedName = "also stale")
            ),
            transactions = sampleTables().transactions.map {
                it.copy(rawPayee = "corner  cafe", normalizedPayee = "stale")
            }
        )

        val accepted = (validate(backupFile(tables = tables)) as Result.Success).data

        // A file written under older normalization rules would otherwise import names that never
        // match anything again — auto-mapping and duplicate detection both key on these.
        assertThat(accepted.payeeIdentifiers.first().normalizedName).isEqualTo("CORNER CAFE")
        assertThat(accepted.transactions.first().normalizedPayee).isEqualTo("CORNER CAFE")
    }

    @Test
    fun `rejects two statement names that mean the same thing`() {
        val tables = sampleTables(
            payeeIdentifiers = listOf(
                BackupPayeeIdentifier(id = 1L, payeeId = 10L, rawName = "Corner Cafe", normalizedName = "CORNER CAFE"),
                BackupPayeeIdentifier(id = 2L, payeeId = 10L, rawName = "corner  cafe", normalizedName = "CORNER CAFE")
            )
        )

        // One account can only read a statement name one way, so a file claiming two is refused
        // rather than half-imported.
        assertThat(errorFrom(backupFile(tables = tables)))
            .isEqualTo(BackupError.ConflictingNames("CORNER CAFE"))
    }
}
