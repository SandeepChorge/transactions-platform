package com.madtitan94.transactionsparser.core.domain.backup

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BackupCodecTest {

    @Test
    fun `round-trips a populated backup unchanged`() {
        val original = backupFile()

        val decoded = BackupCodec.decode(BackupCodec.encode(original))

        // Whole-object equality rather than field spot-checks: a backup that loses one column is
        // exactly the failure this format exists to prevent, and spot-checks only catch the
        // columns someone remembered to list.
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun `round-trips an empty account`() {
        val original = backupFile(tables = emptyTables())

        val decoded = BackupCodec.decode(BackupCodec.encode(original))

        assertThat(decoded).isEqualTo(original)
        assertThat(decoded.transactions).hasSize(0)
    }

    @Test
    fun `keeps soft-deleted rows and their deletion timestamps`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(backupFile()))

        val deleted = decoded.categories.single { it.isDeleted }
        assertThat(deleted.name).isEqualTo("Travel")
        assertThat(deleted.deletedAtMillis).isEqualTo(1_700_000_000_000L)
    }

    @Test
    fun `keeps the self-reference between a duplicate and the row it repeats`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(backupFile()))

        val duplicate = decoded.transactions.single { it.isDuplicate }
        assertThat(duplicate.duplicateOfTransactionId).isEqualTo(10_000L)
        assertThat(duplicate.transactionRef).isNull()
        assertThat(duplicate.utr).isNull()
    }

    @Test
    fun `writes no ownerId anywhere`() {
        // The guarantee that a restore cannot write rows owned by an account that is not signed
        // in rests on the field being absent from the format, so it is worth asserting on the
        // text rather than trusting the data class to stay as it is.
        val encoded = BackupCodec.encode(backupFile())

        assertThat(encoded).doesNotContain("ownerId")
    }

    @Test
    fun `keeps amounts as integer paise`() {
        val encoded = BackupCodec.encode(backupFile())

        assertThat(encoded).contains("\"amountPaise\": 25800")
    }

    @Test
    fun `rejects a file with a missing table`() {
        val withoutTransactions = BackupCodec.encode(backupFile())
            .let { Json.parseToJsonElement(it).jsonObject }
            .filterKeys { it != "transactions" }
            .let { Json.encodeToString(JsonObject(it)) }

        // A missing key is a malformed file, not an empty table — the difference matters when the
        // file is the only copy of someone's data.
        assertThrows<SerializationException> { BackupCodec.decode(withoutTransactions) }
    }

    @Test
    fun `rejects text that is not a backup at all`() {
        assertThrows<SerializationException> { BackupCodec.decode("Date,Time,Payee\n2026-06-01,12:00,X") }
    }

    @Test
    fun `ignores a field added by a later build of the same format version`() {
        val encoded = BackupCodec.encode(backupFile())
            .replaceFirst("{", "{\n  \"somethingAddedLater\": true,")

        assertThat(BackupCodec.decode(encoded)).isEqualTo(backupFile())
    }
}
