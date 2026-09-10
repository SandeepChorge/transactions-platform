package com.madtitan94.transactionsparser.feature.profile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.madtitan94.transactionsparser.core.domain.datasource.LocalDataCleaner
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result
import com.madtitan94.transactionsparser.feature.profile.domain.Gender
import com.madtitan94.transactionsparser.feature.profile.domain.ProfileStorage
import com.madtitan94.transactionsparser.feature.profile.domain.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profileDataStore by preferencesDataStore(name = "user_profile_store")

class DataStoreProfileStorage(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.profileDataStore
) : ProfileStorage, LocalDataCleaner {

    override suspend fun clearLocalData(): EmptyResult<DataError.Local> = try {
        dataStore.edit { it.clear() }
        Result.Success(Unit)
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Result.Error(DataError.Local.UNKNOWN)
    }

    private object Keys {
        val NAME = stringPreferencesKey("name")
        val MOBILE = stringPreferencesKey("mobile")
        val GENDER = stringPreferencesKey("gender")
    }

    override fun observeProfile(): Flow<UserProfile?> {
        return dataStore.data.map { prefs ->
            val name = prefs[Keys.NAME] ?: return@map null
            UserProfile(
                name = name,
                mobile = prefs[Keys.MOBILE].orEmpty(),
                gender = prefs[Keys.GENDER]?.let { stored ->
                    runCatching { Gender.valueOf(stored) }.getOrNull()
                }
            )
        }
    }

    override suspend fun save(profile: UserProfile): EmptyResult<DataError.Local> {
        return try {
            dataStore.edit { prefs ->
                prefs[Keys.NAME] = profile.name
                prefs[Keys.MOBILE] = profile.mobile
                profile.gender?.let { prefs[Keys.GENDER] = it.name }
                    ?: prefs.remove(Keys.GENDER)
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.Error(DataError.Local.UNKNOWN)
        }
    }
}
