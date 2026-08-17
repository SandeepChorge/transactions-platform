package com.madtitan94.transactionsparser.core.database.datasource

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteFullException
import com.madtitan94.transactionsparser.core.database.account.ActiveAccountProvider
import com.madtitan94.transactionsparser.core.database.dao.CategoryDao
import com.madtitan94.transactionsparser.core.database.dao.PayeeDao
import com.madtitan94.transactionsparser.core.database.dao.SessionDao
import com.madtitan94.transactionsparser.core.database.dao.TransactionDao
import com.madtitan94.transactionsparser.core.database.dao.UploadLogDao
import com.madtitan94.transactionsparser.core.database.entity.CategoryEntity
import com.madtitan94.transactionsparser.core.database.toCategory
import com.madtitan94.transactionsparser.core.database.toPayee
import com.madtitan94.transactionsparser.core.database.toPayeeEntity
import com.madtitan94.transactionsparser.core.database.toSessionEntity
import com.madtitan94.transactionsparser.core.database.toSessionSummary
import com.madtitan94.transactionsparser.core.database.toStatementSession
import com.madtitan94.transactionsparser.core.database.toTransaction
import com.madtitan94.transactionsparser.core.database.toTransactionEntity
import com.madtitan94.transactionsparser.core.database.toTransactionKey
import com.madtitan94.transactionsparser.core.database.toUploadLog
import com.madtitan94.transactionsparser.core.database.toUploadLogEntity
import com.madtitan94.transactionsparser.core.domain.datasource.CategoryLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.PayeeLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.SessionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.TransactionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.UploadLogLocalDataSource
import com.madtitan94.transactionsparser.core.domain.model.Category
import com.madtitan94.transactionsparser.core.domain.model.Payee
import com.madtitan94.transactionsparser.core.domain.model.SessionStatus
import com.madtitan94.transactionsparser.core.domain.model.SessionSummary
import com.madtitan94.transactionsparser.core.domain.model.StatementSession
import com.madtitan94.transactionsparser.core.domain.model.Transaction
import com.madtitan94.transactionsparser.core.domain.model.TransactionKey
import com.madtitan94.transactionsparser.core.domain.model.UploadLog
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * Well under SQLite's per-statement variable cap (999 on older Android versions), so a large
 * statement can't fail the import just by having too many transactions to look up at once.
 */
private const val SQLITE_BIND_CHUNK = 400

private inline fun <T> safeDbCall(block: () -> T): Result<T, DataError.Local> {
    return try {
        Result.Success(block())
    } catch (e: SQLiteConstraintException) {
        Result.Error(DataError.Local.DUPLICATE)
    } catch (e: SQLiteFullException) {
        Result.Error(DataError.Local.DISK_FULL)
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Result.Error(DataError.Local.UNKNOWN)
    }
}

private suspend inline fun <T> safeSuspendDbCall(block: () -> T): Result<T, DataError.Local> = safeDbCall(block)

/**
 * Re-queries whenever the signed-in account changes, so a stream handed out before a
 * logout can never keep emitting the previous account's rows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> ActiveAccountProvider.flowForOwner(query: (String) -> Flow<T>): Flow<T> =
    observeOwnerId().flatMapLatest(query)

class RoomCategoryDataSource(
    private val dao: CategoryDao,
    private val activeAccount: ActiveAccountProvider,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : CategoryLocalDataSource {

    override fun observeAll(): Flow<List<Category>> =
        activeAccount.flowForOwner(dao::observeAll)
            .map { entities -> entities.map { it.toCategory() } }

    override fun observeDeleted(): Flow<List<Category>> =
        activeAccount.flowForOwner(dao::observeDeleted)
            .map { entities -> entities.map { it.toCategory() } }

    override fun observeLinkedPayeeCounts(): Flow<Map<Long, Int>> =
        activeAccount.flowForOwner(dao::observeLinkedCounts)
            .map { counts -> counts.associate { it.categoryId to it.count } }

    override suspend fun insert(name: String): Result<Long, DataError.Local> =
        safeSuspendDbCall {
            dao.insert(CategoryEntity(ownerId = activeAccount.currentOwnerId(), name = name.trim()))
        }

    override suspend fun rename(id: Long, name: String): EmptyResult<DataError.Local> =
        safeSuspendDbCall { dao.rename(activeAccount.currentOwnerId(), id, name.trim()) }

    override suspend fun delete(id: Long): EmptyResult<DataError.Local> =
        safeSuspendDbCall { dao.softDelete(activeAccount.currentOwnerId(), id, nowMillis()) }

    override suspend fun restore(id: Long): EmptyResult<DataError.Local> =
        safeSuspendDbCall { dao.restore(activeAccount.currentOwnerId(), id) }

    override suspend fun linkedPayeeCount(id: Long): Result<Int, DataError.Local> =
        safeSuspendDbCall { dao.linkedPayeeCount(activeAccount.currentOwnerId(), id) }
}

class RoomPayeeDataSource(
    private val dao: PayeeDao,
    private val activeAccount: ActiveAccountProvider
) : PayeeLocalDataSource {

    override fun observeAll(): Flow<List<Payee>> =
        activeAccount.flowForOwner(dao::observeAll)
            .map { entities -> entities.map { it.toPayee() } }

    override suspend fun findByNormalizedName(normalizedName: String): Result<Payee?, DataError.Local> =
        safeSuspendDbCall {
            dao.findByNormalizedName(activeAccount.currentOwnerId(), normalizedName)?.toPayee()
        }

    override suspend fun save(payee: Payee): Result<Long, DataError.Local> =
        safeSuspendDbCall {
            val ownerId = activeAccount.currentOwnerId()
            val existing = dao.findByNormalizedName(ownerId, payee.normalizedName)
            if (existing == null) {
                dao.insert(payee.toPayeeEntity(ownerId))
            } else {
                dao.update(payee.toPayeeEntity(ownerId).copy(id = existing.id))
                existing.id
            }
        }
}

class RoomSessionDataSource(
    private val dao: SessionDao,
    private val activeAccount: ActiveAccountProvider
) : SessionLocalDataSource {

    override fun observeSummaries(status: SessionStatus): Flow<List<SessionSummary>> =
        activeAccount.flowForOwner { ownerId -> dao.observeSummaries(ownerId, status.name) }
            .map { rows -> rows.map { it.toSessionSummary() } }

    override suspend fun getById(id: Long): Result<StatementSession?, DataError.Local> =
        safeSuspendDbCall { dao.getById(activeAccount.currentOwnerId(), id)?.toStatementSession() }

    override suspend fun insert(session: StatementSession): Result<Long, DataError.Local> =
        safeSuspendDbCall { dao.insert(session.toSessionEntity(activeAccount.currentOwnerId())) }

    override suspend fun updateStatus(id: Long, status: SessionStatus): EmptyResult<DataError.Local> =
        safeSuspendDbCall { dao.updateStatus(activeAccount.currentOwnerId(), id, status.name) }
}

class RoomTransactionDataSource(
    private val dao: TransactionDao,
    private val activeAccount: ActiveAccountProvider
) : TransactionLocalDataSource {

    override fun observeBySession(sessionId: Long): Flow<List<Transaction>> =
        activeAccount.flowForOwner { ownerId -> dao.observeBySession(ownerId, sessionId) }
            .map { entities -> entities.map { it.toTransaction() } }

    override suspend fun insertAll(transactions: List<Transaction>): EmptyResult<DataError.Local> =
        safeSuspendDbCall {
            val ownerId = activeAccount.currentOwnerId()
            dao.insertAll(transactions.map { it.toTransactionEntity(ownerId) })
        }

    override suspend fun findDuplicateKeys(
        candidates: List<Transaction>
    ): Result<List<TransactionKey>, DataError.Local> =
        safeSuspendDbCall {
            val ownerId = activeAccount.currentOwnerId()

            val refs = candidates.mapNotNull { it.transactionRef }.distinct()
            val utrs = candidates.mapNotNull { it.utr }.distinct()
            val reflessTimestamps = candidates
                .filter { it.transactionRef == null && it.utr == null }
                .map { it.dateTimeUtcMillis }
                .distinct()

            buildList {
                // Chunked because Room expands each IN list into one bind variable per item, and
                // SQLite caps those per statement — a large statement would otherwise blow the limit.
                refs.chunked(SQLITE_BIND_CHUNK).forEach { chunk ->
                    addAll(dao.findByRefOrUtr(ownerId, refs = chunk, utrs = emptyList()))
                }
                utrs.chunked(SQLITE_BIND_CHUNK).forEach { chunk ->
                    addAll(dao.findByRefOrUtr(ownerId, refs = emptyList(), utrs = chunk))
                }
                reflessTimestamps.chunked(SQLITE_BIND_CHUNK).forEach { chunk ->
                    addAll(dao.findReflessAt(ownerId, chunk))
                }
            }
                .distinctBy { it.id }
                .map { it.toTransactionKey() }
        }

    override suspend fun setExcluded(id: Long, isExcluded: Boolean): EmptyResult<DataError.Local> =
        safeSuspendDbCall { dao.setExcluded(activeAccount.currentOwnerId(), id, isExcluded) }

    override suspend fun setDuplicatesExcluded(
        sessionId: Long,
        normalizedPayee: String,
        isExcluded: Boolean
    ): EmptyResult<DataError.Local> =
        safeSuspendDbCall {
            dao.setDuplicatesExcluded(
                activeAccount.currentOwnerId(),
                sessionId,
                normalizedPayee,
                isExcluded
            )
        }

    override suspend fun assignPayee(
        sessionId: Long,
        normalizedPayee: String,
        payeeId: Long
    ): EmptyResult<DataError.Local> =
        safeSuspendDbCall {
            dao.assignPayee(activeAccount.currentOwnerId(), sessionId, normalizedPayee, payeeId)
        }

    override suspend fun unmappedCount(sessionId: Long): Result<Int, DataError.Local> =
        safeSuspendDbCall { dao.unmappedCount(activeAccount.currentOwnerId(), sessionId) }
}

class RoomUploadLogDataSource(
    private val dao: UploadLogDao,
    private val activeAccount: ActiveAccountProvider
) : UploadLogLocalDataSource {

    override fun observeAll(): Flow<List<UploadLog>> =
        activeAccount.flowForOwner(dao::observeAll)
            .map { entities -> entities.map { it.toUploadLog() } }

    override suspend fun log(log: UploadLog): EmptyResult<DataError.Local> =
        safeSuspendDbCall { dao.insert(log.toUploadLogEntity(activeAccount.currentOwnerId())) }
}
