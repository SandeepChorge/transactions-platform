package com.madtitan94.transactionsparser.core.database.datasource

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteFullException
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.madtitan94.transactionsparser.core.database.TransactionsDatabase
import com.madtitan94.transactionsparser.core.database.account.ActiveAccountProvider
import com.madtitan94.transactionsparser.core.database.dao.BackupDao
import com.madtitan94.transactionsparser.core.database.dao.CategoryDao
import com.madtitan94.transactionsparser.core.database.dao.PayeeDao
import com.madtitan94.transactionsparser.core.database.dao.PayeeIdentifierDao
import com.madtitan94.transactionsparser.core.database.dao.SessionDao
import com.madtitan94.transactionsparser.core.database.dao.TransactionDao
import com.madtitan94.transactionsparser.core.database.dao.UploadLogDao
import com.madtitan94.transactionsparser.core.database.entity.CategoryEntity
import com.madtitan94.transactionsparser.core.database.entity.PayeeEntity
import com.madtitan94.transactionsparser.core.database.entity.PayeeIdentifierEntity
import com.madtitan94.transactionsparser.core.database.entity.SessionEntity
import com.madtitan94.transactionsparser.core.database.entity.TransactionEntity
import com.madtitan94.transactionsparser.core.database.entity.UploadLogEntity
import com.madtitan94.transactionsparser.core.database.toBackupCategory
import com.madtitan94.transactionsparser.core.database.toBackupPayee
import com.madtitan94.transactionsparser.core.database.toBackupPayeeIdentifier
import com.madtitan94.transactionsparser.core.database.toBackupSession
import com.madtitan94.transactionsparser.core.database.toBackupTransaction
import com.madtitan94.transactionsparser.core.database.toBackupUploadLog
import com.madtitan94.transactionsparser.core.database.toCategory
import com.madtitan94.transactionsparser.core.database.toPayee
import com.madtitan94.transactionsparser.core.database.toPayeeIdentifier
import com.madtitan94.transactionsparser.core.database.toPayeeTotals
import com.madtitan94.transactionsparser.core.database.toPeriodTotal
import com.madtitan94.transactionsparser.core.database.toSessionEntity
import com.madtitan94.transactionsparser.core.database.toSessionSummary
import com.madtitan94.transactionsparser.core.database.toStatementSession
import com.madtitan94.transactionsparser.core.database.toTransaction
import com.madtitan94.transactionsparser.core.database.toTransactionEntity
import com.madtitan94.transactionsparser.core.database.toTransactionExportRow
import com.madtitan94.transactionsparser.core.database.toTransactionKey
import com.madtitan94.transactionsparser.core.database.toUploadLog
import com.madtitan94.transactionsparser.core.database.toUploadLogEntity
import com.madtitan94.transactionsparser.core.domain.backup.BackupCategory
import com.madtitan94.transactionsparser.core.domain.backup.BackupSnapshot
import com.madtitan94.transactionsparser.core.domain.backup.BackupTables
import com.madtitan94.transactionsparser.core.domain.backup.IdentifierConflict
import com.madtitan94.transactionsparser.core.domain.backup.RestorePayload
import com.madtitan94.transactionsparser.core.domain.backup.RestoreReport
import com.madtitan94.transactionsparser.core.domain.datasource.BackupLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.CategoryLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.PayeeLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.SessionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.TransactionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.UploadLogLocalDataSource
import com.madtitan94.transactionsparser.core.domain.model.Category
import com.madtitan94.transactionsparser.core.domain.model.Payee
import com.madtitan94.transactionsparser.core.domain.model.PayeeIdentifier
import com.madtitan94.transactionsparser.core.domain.model.PayeeTotals
import com.madtitan94.transactionsparser.core.domain.model.PeriodTotal
import com.madtitan94.transactionsparser.core.domain.model.SessionStatus
import com.madtitan94.transactionsparser.core.domain.model.SessionSummary
import com.madtitan94.transactionsparser.core.domain.model.StatementSession
import com.madtitan94.transactionsparser.core.domain.model.Transaction
import com.madtitan94.transactionsparser.core.domain.model.TransactionExportRow
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

/** Roughly three screens of rows, so scrolling stays ahead of the reader without over-fetching. */
private const val PAGE_SIZE = 50

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
    private val database: TransactionsDatabase,
    private val dao: PayeeDao,
    private val identifiers: PayeeIdentifierDao,
    private val activeAccount: ActiveAccountProvider
) : PayeeLocalDataSource {

    override fun observeByIdentifier(): Flow<Map<String, Payee>> =
        activeAccount.flowForOwner(dao::observeByIdentifier)
            .map { rows -> rows.associate { it.normalizedName to it.payee.toPayee() } }

    override fun observeByNormalizedName(normalizedName: String): Flow<Payee?> =
        activeAccount.flowForOwner { ownerId -> dao.observeByNormalizedName(ownerId, normalizedName) }
            .map { it?.toPayee() }

    override suspend fun findByNormalizedName(normalizedName: String): Result<Payee?, DataError.Local> =
        safeSuspendDbCall {
            dao.findByNormalizedName(activeAccount.currentOwnerId(), normalizedName)?.toPayee()
        }

    override fun observeAll(): Flow<List<Payee>> =
        activeAccount.flowForOwner(dao::observeAll)
            .map { rows -> rows.map { it.toPayee() } }

    override fun observeLinkedIdentifiers(normalizedName: String): Flow<List<PayeeIdentifier>> =
        activeAccount.flowForOwner { ownerId -> identifiers.observeLinkedTo(ownerId, normalizedName) }
            .map { rows -> rows.map { it.toPayeeIdentifier() } }

    override suspend fun findByAlias(alias: String): Result<Payee?, DataError.Local> =
        safeSuspendDbCall {
            dao.findByAlias(activeAccount.currentOwnerId(), alias)?.toPayee()
        }

    /**
     * The payee and its first identifier are written in one transaction: a payee with no
     * identifier is unreachable by every lookup in the app, so a crash between the two writes
     * would strand a mapping the user believes they made.
     */
    override suspend fun saveMapping(
        rawName: String,
        normalizedName: String,
        alias: String,
        categoryId: Long
    ): Result<Long, DataError.Local> =
        safeSuspendDbCall {
            val ownerId = activeAccount.currentOwnerId()
            database.withTransaction {
                val existing = dao.findByNormalizedName(ownerId, normalizedName)
                if (existing == null) {
                    val payeeId = dao.insert(
                        PayeeEntity(ownerId = ownerId, alias = alias, categoryId = categoryId)
                    )
                    identifiers.insert(
                        PayeeIdentifierEntity(
                            ownerId = ownerId,
                            payeeId = payeeId,
                            rawName = rawName,
                            normalizedName = normalizedName
                        )
                    )
                    payeeId
                } else {
                    dao.update(existing.copy(alias = alias, categoryId = categoryId))
                    existing.id
                }
            }
        }

    /**
     * Identifiers move before the payee is deleted, and both happen in one transaction: their FK
     * is `RESTRICT`, so a half-finished merge either rolls back whole or fails at the delete —
     * never leaves a name pointing at a payee that is no longer there.
     */
    override suspend fun linkToPayee(
        rawName: String,
        normalizedName: String,
        targetPayeeId: Long
    ): EmptyResult<DataError.Local> =
        safeSuspendDbCall {
            val ownerId = activeAccount.currentOwnerId()
            database.withTransaction<Unit> {
                val current = dao.findByNormalizedName(ownerId, normalizedName)
                when {
                    current == null -> identifiers.insert(
                        PayeeIdentifierEntity(
                            ownerId = ownerId,
                            payeeId = targetPayeeId,
                            rawName = rawName,
                            normalizedName = normalizedName
                        )
                    )
                    // Already this payee's name — linking it again is a no-op, not an error.
                    current.id == targetPayeeId -> Unit
                    else -> {
                        dao.repointIdentifiers(ownerId, sourceId = current.id, targetId = targetPayeeId)
                        dao.repointTransactions(ownerId, sourceId = current.id, targetId = targetPayeeId)
                        dao.delete(ownerId, current.id)
                    }
                }
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

    /**
     * The Pager is rebuilt when the account changes rather than reused, so an in-flight page
     * load can't deliver the previous account's rows into a list already showing the new one.
     */
    override fun observePagedByPayee(
        normalizedPayee: String,
        includeLinkedNames: Boolean
    ): Flow<PagingData<Transaction>> =
        activeAccount.flowForOwner { ownerId ->
            Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) {
                dao.pagingByPayee(ownerId, normalizedPayee, includeLinkedNames)
            }.flow.map { paging -> paging.map { it.toTransaction() } }
        }

    override fun observePayeeTotals(
        normalizedPayee: String,
        includeLinkedNames: Boolean
    ): Flow<PayeeTotals> =
        activeAccount.flowForOwner { ownerId ->
            dao.observePayeeTotals(ownerId, normalizedPayee, includeLinkedNames)
        }.map { it?.toPayeeTotals() ?: PayeeTotals() }

    override fun observePayeeDayTotals(
        normalizedPayee: String,
        includeLinkedNames: Boolean
    ): Flow<List<PeriodTotal>> =
        activeAccount.flowForOwner { ownerId ->
            dao.observePayeeDayTotals(ownerId, normalizedPayee, includeLinkedNames)
        }.map { rows -> rows.map { it.toPeriodTotal() } }

    override fun observePayeeMonthTotals(
        normalizedPayee: String,
        includeLinkedNames: Boolean
    ): Flow<List<PeriodTotal>> =
        activeAccount.flowForOwner { ownerId ->
            dao.observePayeeMonthTotals(ownerId, normalizedPayee, includeLinkedNames)
        }
            .map { rows -> rows.map { it.toPeriodTotal() } }

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

    override suspend fun exportRows(): Result<List<TransactionExportRow>, DataError.Local> =
        safeSuspendDbCall {
            dao.exportRows(activeAccount.currentOwnerId()).map { it.toTransactionExportRow() }
        }
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

class RoomBackupDataSource(
    private val database: TransactionsDatabase,
    private val dao: BackupDao,
    private val categories: CategoryDao,
    private val activeAccount: ActiveAccountProvider
) : BackupLocalDataSource {

    override suspend fun snapshot(): Result<BackupSnapshot, DataError.Local> =
        safeSuspendDbCall {
            val ownerId = activeAccount.currentOwnerId()
            // All six reads share one transaction so they see one moment. Without it a payee
            // could be merged away between reading `payees` and reading `payee_identifiers`,
            // and the backup would carry an identifier pointing at a payee it does not contain.
            database.withTransaction {
                BackupSnapshot(
                    // Read from the open database rather than from a constant here, so it cannot
                    // drift from the version Room actually migrated to.
                    schemaVersion = database.openHelper.readableDatabase.version,
                    tables = BackupTables(
                        categories = dao.categories(ownerId).map { it.toBackupCategory() },
                        payees = dao.payees(ownerId).map { it.toBackupPayee() },
                        payeeIdentifiers = dao.payeeIdentifiers(ownerId)
                            .map { it.toBackupPayeeIdentifier() },
                        sessions = dao.sessions(ownerId).map { it.toBackupSession() },
                        transactions = dao.transactions(ownerId).map { it.toBackupTransaction() },
                        uploadLogs = dao.uploadLogs(ownerId).map { it.toBackupUploadLog() }
                    )
                )
            }
        }

    override suspend fun schemaVersion(): Result<Int, DataError.Local> =
        safeSuspendDbCall { database.openHelper.readableDatabase.version }

    override suspend fun restore(payload: RestorePayload): Result<RestoreReport, DataError.Local> =
        safeSuspendDbCall {
            val ownerId = activeAccount.currentOwnerId()
            // One transaction around the whole write. A restore that half-succeeded would leave
            // the user unable to tell what arrived, and running it again would double whatever did.
            database.withTransaction { write(ownerId, payload) }
        }

    /**
     * Writes every table in dependency order, remapping the file's ids to the ones this database
     * assigns as it goes.
     *
     * `ownerId` is stamped here and nowhere else — the file has no owner field to read, so a
     * restore cannot produce rows belonging to an account that is not signed in.
     */
    private suspend fun write(ownerId: String, payload: RestorePayload): RestoreReport {
        // Read the local side first, before anything is inserted, so these describe what the
        // account already had rather than what this restore is adding.
        val localCategories = dao.categories(ownerId)
        val localPayeeAliases = dao.payees(ownerId).associate { it.id to it.alias }
        val localIdentifiers = dao.payeeIdentifiers(ownerId).associateBy { it.normalizedName }

        // Matched case-insensitively even though the unique index is exact: the app orders and
        // presents category names with NOCASE, so ending up with "Food" beside "food" would look
        // like a bug rather than like two categories.
        val localByName = localCategories.associateBy { it.name.lowercase() }
        val reusedCategories = mutableMapOf<Long, Long>()
        val newCategories = mutableListOf<BackupCategory>()
        payload.categories.forEach { fileCategory ->
            val local = localByName[fileCategory.name.lowercase()]
            if (local == null) {
                newCategories += fileCategory
            } else {
                reusedCategories[fileCategory.id] = local.id
                // A category the file has in use must not end up in Recently deleted here, or the
                // payees restored under it would hang off a row the user has already thrown away.
                if (local.isDeleted && !fileCategory.isDeleted) categories.restore(ownerId, local.id)
            }
        }
        val categoryIds = reusedCategories + newCategories.map { it.id }.zip(
            dao.insertCategories(
                newCategories.map {
                    CategoryEntity(
                        ownerId = ownerId,
                        name = it.name,
                        isDeleted = it.isDeleted,
                        deletedAtMillis = it.deletedAtMillis
                    )
                }
            )
        )

        val payeeIds = payload.payees.map { it.id }.zip(
            dao.insertPayees(
                payload.payees.map {
                    PayeeEntity(
                        ownerId = ownerId,
                        alias = it.alias,
                        categoryId = categoryIds.getValue(it.categoryId),
                        isDeleted = it.isDeleted,
                        deletedAtMillis = it.deletedAtMillis
                    )
                }
            )
        ).toMap()

        // A statement name can only mean one payee per account, so where the file disagrees with
        // what is already here the local mapping stands and the disagreement is reported. Silently
        // repointing it would change what the user's existing transactions resolve to.
        val conflicts = mutableListOf<IdentifierConflict>()
        val newIdentifiers = payload.payeeIdentifiers.filter { fileIdentifier ->
            val local = localIdentifiers[fileIdentifier.normalizedName]
            if (local == null) {
                true
            } else {
                conflicts += IdentifierConflict(
                    normalizedName = fileIdentifier.normalizedName,
                    keptPayeeAlias = localPayeeAliases[local.payeeId] ?: local.rawName,
                    filePayeeAlias = payload.payees.first { it.id == fileIdentifier.payeeId }.alias
                )
                false
            }
        }
        dao.insertPayeeIdentifiers(
            newIdentifiers.map {
                PayeeIdentifierEntity(
                    ownerId = ownerId,
                    payeeId = payeeIds.getValue(it.payeeId),
                    rawName = it.rawName,
                    normalizedName = it.normalizedName
                )
            }
        )

        val sessionIds = payload.sessions.map { it.id }.zip(
            dao.insertSessions(
                payload.sessions.map {
                    SessionEntity(
                        ownerId = ownerId,
                        fileName = it.fileName,
                        source = it.source,
                        uploadedAtMillis = it.uploadedAtMillis,
                        periodStartMillis = it.periodStartMillis,
                        periodEndMillis = it.periodEndMillis,
                        status = it.status,
                        isDeleted = it.isDeleted,
                        deletedAtMillis = it.deletedAtMillis
                    )
                }
            )
        ).toMap()

        val newTransactionIds = dao.insertTransactions(
            payload.transactions.map { row ->
                TransactionEntity(
                    ownerId = ownerId,
                    sessionId = sessionIds.getValue(row.source.sessionId),
                    dateTimeUtcMillis = row.source.dateTimeUtcMillis,
                    rawPayee = row.source.rawPayee,
                    normalizedPayee = row.source.normalizedPayee,
                    amountPaise = row.source.amountPaise,
                    type = row.source.type,
                    transactionRef = row.source.transactionRef,
                    utr = row.source.utr,
                    payeeId = row.source.payeeId?.let(payeeIds::getValue),
                    isDuplicate = row.isDuplicate,
                    // Only a row this account already had can be pointed at yet; a repeat of
                    // another row from the same file is linked below, once that row has an id.
                    duplicateOfTransactionId = row.duplicateOfLocalId,
                    isExcluded = row.isExcluded,
                    isDeleted = row.source.isDeleted,
                    deletedAtMillis = row.source.deletedAtMillis
                )
            }
        )
        val transactionIds = payload.transactions.map { it.source.id }.zip(newTransactionIds).toMap()

        payload.transactions.forEachIndexed { index, row ->
            if (row.duplicateOfLocalId != null) return@forEachIndexed
            val target = row.source.duplicateOfTransactionId?.let(transactionIds::get)
                ?: return@forEachIndexed
            dao.linkDuplicate(ownerId, newTransactionIds[index], target)
        }

        dao.insertUploadLogs(
            payload.uploadLogs.map {
                UploadLogEntity(
                    ownerId = ownerId,
                    fileName = it.fileName,
                    uploadedAtMillis = it.uploadedAtMillis,
                    success = it.success,
                    source = it.source,
                    failureReason = it.failureReason,
                    sessionId = it.sessionId?.let(sessionIds::getValue),
                    isDeleted = it.isDeleted,
                    deletedAtMillis = it.deletedAtMillis
                )
            }
        )

        return RestoreReport(
            categoriesInserted = newCategories.size,
            categoriesReused = reusedCategories.size,
            payees = payload.payees.size,
            payeeIdentifiers = newIdentifiers.size,
            sessions = payload.sessions.size,
            transactions = payload.transactions.size,
            uploadLogs = payload.uploadLogs.size,
            // Flags the file already carried describe an import that happened elsewhere; what the
            // user needs to know is how much of this file this account already had.
            duplicatesFlagged = payload.transactions.count { it.isDuplicate && !it.source.isDuplicate },
            identifierConflicts = conflicts
        )
    }
}
