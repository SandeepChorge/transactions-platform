package com.madtitan94.transactionsparser.core.presentation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

sealed interface UiText {
    data class DynamicString(val value: String) : UiText
    class StringResource(val id: Int, val args: Array<Any> = emptyArray()) : UiText

    @Composable
    fun asString(): String = when (this) {
        is DynamicString -> value
        is StringResource -> stringResource(id, *args)
    }

    fun asString(context: Context): String = when (this) {
        is DynamicString -> value
        is StringResource -> context.getString(id, *args)
    }
}
