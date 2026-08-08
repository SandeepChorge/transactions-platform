package com.madtitan94.transactionsparser.core.presentation

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
