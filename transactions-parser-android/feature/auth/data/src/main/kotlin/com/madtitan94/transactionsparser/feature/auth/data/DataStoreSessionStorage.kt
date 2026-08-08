package com.madtitan94.transactionsparser.feature.auth.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.madtitan94.transactionsparser.core.domain.datasource.SessionStorage
import com.madtitan94.transactionsparser.core.domain.model.UserSession
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "session_store")

class DataStoreSessionStorage(private val context: Context) : SessionStorage {

    private object Keys {
        val GOOGLE_ID = stringPreferencesKey("google_id")
        val NAME = stringPreferencesKey("name")
        val EMAIL = stringPreferencesKey("email")
        val PHOTO_URL = stringPreferencesKey("photo_url")
    }

    override fun observeSession(): Flow<UserSession?> {
        return context.sessionDataStore.data.map { prefs ->
            val googleId = prefs[Keys.GOOGLE_ID] ?: return@map null
            UserSession(
                googleId = googleId,
                name = prefs[Keys.NAME].orEmpty(),
                email = prefs[Keys.EMAIL].orEmpty(),
                photoUrl = prefs[Keys.PHOTO_URL]
            )
        }
    }

    override suspend fun save(session: UserSession): EmptyResult<DataError.Local> {
        return try {
            context.sessionDataStore.edit { prefs ->
                prefs[Keys.GOOGLE_ID] = session.googleId
                prefs[Keys.NAME] = session.name
                prefs[Keys.EMAIL] = session.email
                session.photoUrl?.let { prefs[Keys.PHOTO_URL] = it }
                    ?: prefs.remove(Keys.PHOTO_URL)
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.Error(DataError.Local.UNKNOWN)
        }
    }

    override suspend fun clear(): EmptyResult<DataError.Local> {
        return try {
            context.sessionDataStore.edit { it.clear() }
            Result.Success(Unit)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.Error(DataError.Local.UNKNOWN)
        }
    }
}
