package com.madtitan94.transactionsparser.feature.settings.domain

import kotlinx.coroutines.flow.Flow

/**
 * The user's stored theme choice.
 *
 * Observed rather than read once, because the root composable and the Settings screen both hold
 * it at the same time and have to agree the instant it changes.
 */
interface ThemeStorage {

    /** Emits the stored choice, falling back to [ThemePreference.SYSTEM] when none has been made. */
    fun observeTheme(): Flow<ThemePreference>

    /**
     * Stores a choice.
     *
     * Deliberately returns nothing. A failed preference write leaves the app on the theme the
     * user just picked and has no remedy worth putting in front of them, so there is no error for
     * a caller to handle.
     */
    suspend fun setTheme(preference: ThemePreference)
}
