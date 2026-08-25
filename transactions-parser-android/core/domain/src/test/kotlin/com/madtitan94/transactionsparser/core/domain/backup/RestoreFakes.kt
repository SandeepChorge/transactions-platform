package com.madtitan94.transactionsparser.core.domain.backup

import androidx.paging.PagingData
import com.madtitan94.transactionsparser.core.domain.datasource.BackupLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.DocumentReader
import com.madtitan94.transactionsparser.core.domain.datasource.SessionStorage
import com.madtitan94.transactionsparser.core.domain.datasource.TransactionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.model.PayeeTotals
import com.madtitan94.transactionsparser.core.domain.model.PeriodTotal
import com.madtitan94.transactionsparser.core.domain.model.Transaction
import com.madtitan94.transactionsparser.core.domain.model.TransactionExportRow
import com.madtitan94.transactionsparser.core.domain.model.TransactionKey
import com.madtitan94.transactionsparser.core.domain.model.UserSession
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Records the payload it is handed instead of writing anything.
 *
 * The payload is the whole contract between the domain layer and the writer, so asserting on it is
 * how these tests check that remapping, duplicate flagging and the user's exclusions all survived
 * the trip — without needing a database to see it.
 */
class RecordingBackupDataSource(
    private val restoreResult: Result<RestoreReport, DataError.Local> =
        Result.Success(emptyRestoreReport()),
    private val supportedSchemaVersion: Int = 4
) : BackupLocalDataSource {
    var restored: RestorePayload? = null
    var restores = 0

    override suspend fun snapshot(): Result<BackupSnapshot, DataError.Local> =
        error("Restoring never reads a snapshot")

    override suspend fun schemaVersion(): Result<Int, DataError.Local> =
        Result.Success(supportedSchemaVersion)

    override suspend fun restore(payload: RestorePayload): Result<RestoreReport, DataError.Local> {
        restores++
        restored = payload
        return restoreResult
    }
}

class FakeDocumentReader(
    private val result: Result<String, DataError.Local> = Result.Success(BackupCodec.encode(backupFile()))
) : DocumentReader {
    override suspend fun read(source: String): Result<String, DataError.Local> = result
}

class FakeRestoreSessionStorage(
    initial: UserSession? = UserSession("google-sub-1", "Sandeep", "someone@example.com", null)
) : SessionStorage {
    private val session = MutableStateFlow(initial)
    override fun observeSession(): Flow<UserSession?> = session
    override suspend fun save(session: UserSession): EmptyResult<DataError.Local> = Result.Success(Unit)
    override suspend fun clear(): EmptyResult<DataError.Local> = Result.Success(Unit)
}

/**
 * Only [findDuplicateKeys] is real — it is the one call a restore makes. Everything else throws
 * rather than returning a plausible empty value, so a future call that quietly depends on one fails
 * loudly instead of passing for the wrong reason.
 */
class FakeDuplicateKeySource(
    private val keys: Result<List<TransactionKey>, DataError.Local> = Result.Success(emptyList())
) : TransactionLocalDataSource {
    var lastCandidates: List<Transaction>? = null

    override suspend fun findDuplicateKeys(
        candidates: List<Transaction>
    ): Result<List<TransactionKey>, DataError.Local> {
        lastCandidates = candidates
        return keys
    }

    override fun observeBySession(sessionId: Long): Flow<List<Transaction>> = unused()
    override fun observePagedByPayee(
        normalizedPayee: String,
        includeLinkedNames: Boolean
    ): Flow<PagingData<Transaction>> = unused()
    override fun observePayeeTotals(
        normalizedPayee: String,
        includeLinkedNames: Boolean
    ): Flow<PayeeTotals> = unused()
    override fun observePayeeDayTotals(
        normalizedPayee: String,
        includeLinkedNames: Boolean
    ): Flow<List<PeriodTotal>> = unused()
    override fun observePayeeMonthTotals(
        normalizedPayee: String,
        includeLinkedNames: Boolean
    ): Flow<List<PeriodTotal>> = unused()
    override suspend fun insertAll(transactions: List<Transaction>): EmptyResult<DataError.Local> = unused()
    override suspend fun setExcluded(id: Long, isExcluded: Boolean): EmptyResult<DataError.Local> = unused()
    override suspend fun setDuplicatesExcluded(
        sessionId: Long,
        normalizedPayee: String,
        isExcluded: Boolean
    ): EmptyResult<DataError.Local> = unused()
    override suspend fun assignPayee(
        sessionId: Long,
        normalizedPayee: String,
        payeeId: Long
    ): EmptyResult<DataError.Local> = unused()
    override suspend fun unmappedCount(sessionId: Long): Result<Int, DataError.Local> = unused()
    override suspend fun exportRows(): Result<List<TransactionExportRow>, DataError.Local> = unused()

    private fun <T> unused(): T = error("A restore does not use this")
}

fun emptyRestoreReport() = RestoreReport(
    categoriesInserted = 0,
    categoriesReused = 0,
    payees = 0,
    payeeIdentifiers = 0,
    sessions = 0,
    transactions = 0,
    uploadLogs = 0,
    duplicatesFlagged = 0,
    identifierConflicts = emptyList()
)
