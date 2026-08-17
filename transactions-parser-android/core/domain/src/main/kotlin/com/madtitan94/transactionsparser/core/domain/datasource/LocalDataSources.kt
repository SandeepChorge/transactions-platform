package com.madtitan94.transactionsparser.core.domain.datasource

import com.madtitan94.transactionsparser.core.domain.model.Category
import com.madtitan94.transactionsparser.core.domain.model.Payee
import com.madtitan94.transactionsparser.core.domain.model.SessionStatus
import com.madtitan94.transactionsparser.core.domain.model.SessionSummary
import com.madtitan94.transactionsparser.core.domain.model.StatementSession
import com.madtitan94.transactionsparser.core.domain.model.Transaction
import com.madtitan94.transactionsparser.core.domain.model.TransactionKey
import com.madtitan94.transactionsparser.core.domain.model.UploadLog
import com.madtitan94.transactionsparser.core.domain.model.UserSession
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

interface CategoryLocalDataSource {
    fun observeAll(): Flow<List<Category>>
    /** Soft-deleted categories, most recently deleted first, for the recovery list. */
    fun observeDeleted(): Flow<List<Category>>
    fun observeLinkedPayeeCounts(): Flow<Map<Long, Int>>
    suspend fun insert(name: String): Result<Long, DataError.Local>
    suspend fun rename(id: Long, name: String): EmptyResult<DataError.Local>
    /** Soft delete — the row stays and can be brought back with [restore]. */
    suspend fun delete(id: Long): EmptyResult<DataError.Local>
    suspend fun restore(id: Long): EmptyResult<DataError.Local>
    suspend fun linkedPayeeCount(id: Long): Result<Int, DataError.Local>
}

interface PayeeLocalDataSource {
    fun observeAll(): Flow<List<Payee>>
    suspend fun findByNormalizedName(normalizedName: String): Result<Payee?, DataError.Local>
    /** Inserts or updates by [Payee.normalizedName]; returns the payee id. */
    suspend fun save(payee: Payee): Result<Long, DataError.Local>
}

interface SessionLocalDataSource {
    fun observeSummaries(status: SessionStatus): Flow<List<SessionSummary>>
    suspend fun getById(id: Long): Result<StatementSession?, DataError.Local>
    suspend fun insert(session: StatementSession): Result<Long, DataError.Local>
    suspend fun updateStatus(id: Long, status: SessionStatus): EmptyResult<DataError.Local>
}

interface TransactionLocalDataSource {
    fun observeBySession(sessionId: Long): Flow<List<Transaction>>
    suspend fun insertAll(transactions: List<Transaction>): EmptyResult<DataError.Local>
    /**
     * Keys of already-stored transactions that could match any of [candidates], across every
     * session of this account. Returns candidates to compare, not a verdict — the matching
     * rules live in the domain layer so they stay testable without a database.
     */
    suspend fun findDuplicateKeys(candidates: List<Transaction>): Result<List<TransactionKey>, DataError.Local>
    /** User override for whether a transaction counts toward totals. */
    suspend fun setExcluded(id: Long, isExcluded: Boolean): EmptyResult<DataError.Local>
    /** Same override applied to the flagged rows of one payee in a session, and only those. */
    suspend fun setDuplicatesExcluded(
        sessionId: Long,
        normalizedPayee: String,
        isExcluded: Boolean
    ): EmptyResult<DataError.Local>
    suspend fun assignPayee(sessionId: Long, normalizedPayee: String, payeeId: Long): EmptyResult<DataError.Local>
    suspend fun unmappedCount(sessionId: Long): Result<Int, DataError.Local>
}

interface UploadLogLocalDataSource {
    fun observeAll(): Flow<List<UploadLog>>
    suspend fun log(log: UploadLog): EmptyResult<DataError.Local>
}

/** Persisted Google sign-in session. */
interface SessionStorage {
    fun observeSession(): Flow<UserSession?>
    suspend fun save(session: UserSession): EmptyResult<DataError.Local>
    suspend fun clear(): EmptyResult<DataError.Local>
}
