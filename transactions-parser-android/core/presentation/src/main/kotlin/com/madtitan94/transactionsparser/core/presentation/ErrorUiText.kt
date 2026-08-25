package com.madtitan94.transactionsparser.core.presentation

import com.madtitan94.transactionsparser.core.domain.backup.BackupError
import com.madtitan94.transactionsparser.core.domain.parsing.ParseError
import com.madtitan94.transactionsparser.core.domain.util.DataError

fun DataError.toUiText(): UiText {
    return when (this) {
        DataError.Local.DISK_FULL -> UiText.StringResource(R.string.error_disk_full)
        DataError.Local.NOT_FOUND -> UiText.StringResource(R.string.error_not_found)
        DataError.Local.DUPLICATE -> UiText.StringResource(R.string.error_duplicate)
        else -> UiText.StringResource(R.string.error_unknown)
    }
}

fun ParseError.toUiText(): UiText {
    return when (this) {
        ParseError.FILE_TOO_LARGE -> UiText.StringResource(R.string.error_file_too_large)
        ParseError.NOT_A_PDF -> UiText.StringResource(R.string.error_not_a_pdf)
        ParseError.PASSWORD_PROTECTED -> UiText.StringResource(R.string.error_password_protected)
        ParseError.EXTRACTION_FAILED -> UiText.StringResource(R.string.error_extraction_failed)
        ParseError.UNRECOGNIZED_FORMAT -> UiText.StringResource(R.string.error_unrecognized_format)
        ParseError.NO_TRANSACTIONS -> UiText.StringResource(R.string.error_no_transactions)
        ParseError.STORAGE_FAILURE -> UiText.StringResource(R.string.error_storage_failure)
    }
}

/**
 * Deliberately specific, one message per reason. The file the user picked may be the only copy of
 * their data, so "this backup was written by a newer version of the app" — which tells them what to
 * do — is worth eight strings that a single "couldn't import that file" would have saved.
 */
fun BackupError.toUiText(): UiText {
    return when (this) {
        BackupError.NotABackup -> UiText.StringResource(R.string.error_backup_not_a_backup)
        is BackupError.UnsupportedFormat -> UiText.StringResource(
            R.string.error_backup_unsupported_format,
            arrayOf(formatVersion)
        )
        is BackupError.NewerSchema -> UiText.StringResource(R.string.error_backup_newer_schema)
        is BackupError.BrokenReferences -> UiText.StringResource(
            R.string.error_backup_broken_references,
            arrayOf(count, example)
        )
        is BackupError.DuplicateIds -> UiText.StringResource(
            R.string.error_backup_duplicate_ids,
            arrayOf(table, count)
        )
        is BackupError.UnknownValue -> UiText.StringResource(
            R.string.error_backup_unknown_value,
            arrayOf(field, value)
        )
        is BackupError.InvalidValue -> UiText.StringResource(
            R.string.error_backup_invalid_value,
            arrayOf(field, detail)
        )
        is BackupError.ConflictingNames -> UiText.StringResource(
            R.string.error_backup_conflicting_names,
            arrayOf(normalizedName)
        )
        BackupError.CouldNotRead -> UiText.StringResource(R.string.error_backup_could_not_read)
    }
}
