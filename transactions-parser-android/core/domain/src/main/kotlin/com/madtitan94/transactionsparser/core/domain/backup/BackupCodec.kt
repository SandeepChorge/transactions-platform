package com.madtitan94.transactionsparser.core.domain.backup

import kotlinx.serialization.json.Json

/**
 * Turns a [BackupFile] into text and back.
 *
 * Pure and free of any data source so it can be round-tripped in a unit test — which is the only
 * honest way to know a backup can actually restore what it wrote.
 *
 * [decode] throws rather than returning a `Result`. Deciding *why* a file is unacceptable —
 * unsupported format version, schema from a newer app, broken references — is the restore
 * validator's job, and giving this function a second opinion on it would mean two places to keep
 * in agreement.
 */
object BackupCodec {

    private val json = Json {
        // The user is told to copy the file off the device and check it before wiping anything,
        // so it has to be readable. A few thousand rows of indented JSON is still a small file.
        prettyPrint = true
        // Lets a file written by a later build of the same format version still load, so an added
        // field is a compatible change rather than one that needs a format version bump.
        ignoreUnknownKeys = true
        // Nothing in the format has a default, but this keeps that from silently becoming
        // optional if one is ever added.
        encodeDefaults = true
    }

    fun encode(backup: BackupFile): String = json.encodeToString(backup)

    /** @throws kotlinx.serialization.SerializationException if [text] is not a backup file. */
    fun decode(text: String): BackupFile = json.decodeFromString(text)
}
