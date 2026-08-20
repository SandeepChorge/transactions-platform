package com.madtitan94.transactionsparser.feature.settings.presentation

import androidx.paging.PagingData
import com.madtitan94.transactionsparser.core.domain.datasource.TransactionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.model.PayeeTotals
import com.madtitan94.transactionsparser.core.domain.model.PeriodTotal
import com.madtitan94.transactionsparser.core.domain.model.Transaction
import com.madtitan94.transactionsparser.core.domain.model.TransactionExportRow
import com.madtitan94.transactionsparser.core.domain.model.TransactionKey
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.flow.Flow

/**
 * Only [exportRows] matters to Settings; the rest of the interface exists so the class compiles.
 * Anything Settings does not call throws rather than returning a plausible-looking empty value,
 * so a future call that quietly relies on one fails loudly instead of passing for a bad reason.
 */
class FakeTransactionDataSource(
    private val rows: Result<List<TransactionExportRow>, DataError.Local>
) : TransactionLocalDataSource {

    var exportCount = 0
        private set

    override suspend fun exportRows(): Result<List<TransactionExportRow>, DataError.Local> {
        exportCount++
        return rows
    }

    override fun observeBySession(sessionId: Long): Flow<List<Transaction>> = unused()
    override fun observePagedByPayee(normalizedPayee: String): Flow<PagingData<Transaction>> = unused()
    override fun observePayeeTotals(normalizedPayee: String): Flow<PayeeTotals> = unused()
    override fun observePayeeDayTotals(normalizedPayee: String): Flow<List<PeriodTotal>> = unused()
    override fun observePayeeMonthTotals(normalizedPayee: String): Flow<List<PeriodTotal>> = unused()

    override suspend fun insertAll(transactions: List<Transaction>): EmptyResult<DataError.Local> = unused()

    override suspend fun findDuplicateKeys(
        candidates: List<Transaction>
    ): Result<List<TransactionKey>, DataError.Local> = unused()

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

    private fun <T> unused(): T = error("Settings does not use this")
}
