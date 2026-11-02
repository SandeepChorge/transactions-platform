package com.madtitan94.transactionsparser.core.catalog

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.categoryCatalogStore by preferencesDataStore(name = "category_catalog")

/**
 * The last catalog document that parsed.
 *
 * Kept out of the account stores on purpose. It is a public list, not anyone's categories, and it
 * has to survive a logout so the next launch can still ignore a bad Remote Config payload.
 *
 * Not registered as a [com.madtitan94.transactionsparser.core.domain.datasource.LocalDataCleaner].
 * Deleting an account must not throw away the only copy of a catalog that parsed.
 */
class DataStoreCategoryCatalogStore(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.categoryCatalogStore
) {
    suspend fun read(): String? = try {
        dataStore.data.first()[Keys.LAST_GOOD_JSON]
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        null
    }

    suspend fun write(json: String) {
        try {
            dataStore.edit { prefs -> prefs[Keys.LAST_GOOD_JSON] = json }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // A failed write leaves the in-memory catalog in place. Losing the cache across a
            // process death is a worse outcome than crashing the launch over it.
        }
    }

    private object Keys {
        val LAST_GOOD_JSON = stringPreferencesKey("last_good_json")
    }
}
