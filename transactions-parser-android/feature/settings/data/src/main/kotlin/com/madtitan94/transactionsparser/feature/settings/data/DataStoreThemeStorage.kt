package com.madtitan94.transactionsparser.feature.settings.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.madtitan94.transactionsparser.feature.settings.domain.ThemePreference
import com.madtitan94.transactionsparser.feature.settings.domain.ThemeStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "appearance_store")

/**
 * Stores the theme choice on its own, in its own DataStore file.
 *
 * It is kept out of the profile and session stores on purpose: appearance is not account data, so
 * it must survive a logout, and it is read before anything knows who the user is.
 */
class DataStoreThemeStorage(private val context: Context) : ThemeStorage {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
    }

    override fun observeTheme(): Flow<ThemePreference> {
        return context.themeDataStore.data.map { prefs ->
            // An unrecognised value falls back rather than throwing — the stored string is only
            // ever written by this class, but a downgrade could leave a name this build has
            // never heard of, and the wrong theme is a better outcome than a crash on launch.
            prefs[Keys.THEME]
                ?.let { stored -> runCatching { ThemePreference.valueOf(stored) }.getOrNull() }
                ?: ThemePreference.SYSTEM
        }
    }

    override suspend fun setTheme(preference: ThemePreference) {
        context.themeDataStore.edit { prefs ->
            prefs[Keys.THEME] = preference.name
        }
    }
}
