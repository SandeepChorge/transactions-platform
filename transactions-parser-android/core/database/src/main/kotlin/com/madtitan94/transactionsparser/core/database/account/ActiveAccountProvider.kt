package com.madtitan94.transactionsparser.core.database.account

import com.madtitan94.transactionsparser.core.domain.datasource.SessionStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Owner id used while signed out. No Google id can equal it, so a signed-out read
 * matches nothing rather than falling back to some other account's rows.
 */
const val NO_OWNER_ID: String = "__none__"

/**
 * Resolves which account's rows the data sources may touch. Every Room data source goes
 * through this, so callers above the data layer never pass — or forget — an owner id.
 */
class ActiveAccountProvider(private val sessionStorage: SessionStorage) {

    fun observeOwnerId(): Flow<String> = sessionStorage.observeSession()
        .map { it?.googleId ?: NO_OWNER_ID }
        .distinctUntilChanged()

    suspend fun currentOwnerId(): String = observeOwnerId().first()
}
