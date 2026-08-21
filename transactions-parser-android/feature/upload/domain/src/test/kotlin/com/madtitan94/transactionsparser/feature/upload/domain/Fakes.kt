package com.madtitan94.transactionsparser.feature.upload.domain

import com.madtitan94.transactionsparser.core.domain.datasource.PayeeLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.SessionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.TransactionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.UploadLogLocalDataSource
import androidx.paging.PagingData
import com.madtitan94.transactionsparser.core.domain.model.Payee
import com.madtitan94.transactionsparser.core.domain.model.PayeeIdentifier
import com.madtitan94.transactionsparser.core.domain.model.PayeeTotals
import com.madtitan94.transactionsparser.core.domain.model.PeriodTotal
import com.madtitan94.transactionsparser.core.domain.model.SessionStatus
import com.madtitan94.transactionsparser.core.domain.model.SessionSummary
import com.madtitan94.transactionsparser.core.domain.model.StatementSession
import com.madtitan94.transactionsparser.core.domain.model.StatementSource
import com.madtitan94.transactionsparser.core.domain.model.Transaction
import com.madtitan94.transactionsparser.core.domain.model.TransactionExportRow
import com.madtitan94.transactionsparser.core.domain.model.TransactionKey
import com.madtitan94.transactionsparser.core.domain.model.TransactionType
import com.madtitan94.transactionsparser.core.domain.model.UploadLog
import com.madtitan94.transactionsparser.core.domain.parsing.ParseError
import com.madtitan94.transactionsparser.core.domain.parsing.ParsedStatement
import com.madtitan94.transactionsparser.core.domain.parsing.ParsedTransaction
import com.madtitan94.transactionsparser.core.domain.parsing.StatementParser
import com.madtitan94.transactionsparser.core.domain.parsing.StatementTextExtractor
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeExtractor(var result: Result<String, ParseError>) : StatementTextExtractor {
    override suspend fun extractText(filePath: String): Result<String, ParseError> = result
}

/** A parser whose output the test chooses, for simulating re-imports of overlapping periods. */
class FakeConfigurableParser(
    private val transactions: List<ParsedTransaction>,
    private val periodStartMillis: Long = 1L,
    private val periodEndMillis: Long = 2L
) : StatementParser {
    override val source = StatementSource.PHONEPE
    override fun canParse(text: String) = text.contains("PHONEPE_FIXTURE")
    override fun parse(text: String): Result<ParsedStatement, ParseError> {
        if (!canParse(text)) return Result.Error(ParseError.UNRECOGNIZED_FORMAT)
        return Result.Success(
            ParsedStatement(
                source = source,
                periodStartMillis = periodStartMillis,
                periodEndMillis = periodEndMillis,
                transactions = transactions
            )
        )
    }
}

class FakePhonePeParser : StatementParser {
    override val source = StatementSource.PHONEPE
    override fun canParse(text: String) = text.contains("PHONEPE_FIXTURE")
    override fun parse(text: String): Result<ParsedStatement, ParseError> {
        if (!canParse(text)) return Result.Error(ParseError.UNRECOGNIZED_FORMAT)
        return Result.Success(
            ParsedStatement(
                source = source,
                periodStartMillis = 1L,
                periodEndMillis = 2L,
                transactions = listOf(
                    ParsedTransaction(10L, "SRI DATTA SUPER SHOPPE", 1_000, TransactionType.DEBIT, "T1", "111"),
                    ParsedTransaction(20L, "Blinkit", 20_500, TransactionType.DEBIT, "T2", "222")
                )
            )
        )
    }
}

class FakeSessionDataSource : SessionLocalDataSource {
    val inserted = mutableListOf<StatementSession>()
    private var nextId = 1L
    var failOnInsert = false

    override fun observeSummaries(status: SessionStatus): Flow<List<SessionSummary>> =
        MutableStateFlow(emptyList())

    override suspend fun getById(id: Long): Result<StatementSession?, DataError.Local> =
        Result.Success(inserted.find { it.id == id })

    override suspend fun insert(session: StatementSession): Result<Long, DataError.Local> {
        if (failOnInsert) return Result.Error(DataError.Local.UNKNOWN)
        val id = nextId++
        inserted += session.copy(id = id)
        return Result.Success(id)
    }

    override suspend fun updateStatus(id: Long, status: SessionStatus): EmptyResult<DataError.Local> {
        inserted.replaceAll { if (it.id == id) it.copy(status = status) else it }
        return Result.Success(Unit)
    }

    fun statusOf(id: Long): SessionStatus? = inserted.find { it.id == id }?.status
}

class FakeTransactionDataSource : TransactionLocalDataSource {
    val transactions = mutableListOf<Transaction>()
    var failOnUnmappedCount = false

    override fun observeBySession(sessionId: Long): Flow<List<Transaction>> =
        MutableStateFlow(transactions.toList()).map { list -> list.filter { it.sessionId == sessionId } }

    // Payee Detail reads these; the import path under test here never does.
    override fun observePagedByPayee(
        normalizedPayee: String,
        includeLinkedNames: Boolean
    ): Flow<PagingData<Transaction>> = MutableStateFlow(PagingData.empty())

    override fun observePayeeTotals(
        normalizedPayee: String,
        includeLinkedNames: Boolean
    ): Flow<PayeeTotals> = MutableStateFlow(PayeeTotals())

    override fun observePayeeDayTotals(
        normalizedPayee: String,
        includeLinkedNames: Boolean
    ): Flow<List<PeriodTotal>> = MutableStateFlow(emptyList())

    override fun observePayeeMonthTotals(
        normalizedPayee: String,
        includeLinkedNames: Boolean
    ): Flow<List<PeriodTotal>> = MutableStateFlow(emptyList())

    override suspend fun insertAll(transactions: List<Transaction>): EmptyResult<DataError.Local> {
        // Ids are assigned on insert, mirroring Room — the detector relies on stored rows having
        // real ids while the incoming batch does not.
        val startId = this.transactions.size + 1
        this.transactions += transactions.mapIndexed { index, txn -> txn.copy(id = (startId + index).toLong()) }
        return Result.Success(Unit)
    }

    override suspend fun findDuplicateKeys(
        candidates: List<Transaction>
    ): Result<List<TransactionKey>, DataError.Local> {
        val refs = candidates.mapNotNull { it.transactionRef }.toSet()
        val utrs = candidates.mapNotNull { it.utr }.toSet()
        val reflessTimestamps = candidates
            .filter { it.transactionRef == null && it.utr == null }
            .map { it.dateTimeUtcMillis }
            .toSet()

        return Result.Success(
            transactions
                .filter { stored ->
                    stored.transactionRef in refs ||
                        stored.utr in utrs ||
                        (stored.transactionRef == null && stored.utr == null &&
                            stored.dateTimeUtcMillis in reflessTimestamps)
                }
                .map {
                    TransactionKey(
                        id = it.id,
                        transactionRef = it.transactionRef,
                        utr = it.utr,
                        normalizedPayee = it.normalizedPayee,
                        amountPaise = it.amountPaise,
                        dateTimeUtcMillis = it.dateTimeUtcMillis
                    )
                }
        )
    }

    override suspend fun setExcluded(id: Long, isExcluded: Boolean): EmptyResult<DataError.Local> {
        transactions.replaceAll { if (it.id == id) it.copy(isExcluded = isExcluded) else it }
        return Result.Success(Unit)
    }

    override suspend fun setDuplicatesExcluded(
        sessionId: Long,
        normalizedPayee: String,
        isExcluded: Boolean
    ): EmptyResult<DataError.Local> {
        transactions.replaceAll {
            if (it.sessionId == sessionId && it.normalizedPayee == normalizedPayee && it.isDuplicate) {
                it.copy(isExcluded = isExcluded)
            } else {
                it
            }
        }
        return Result.Success(Unit)
    }

    override suspend fun assignPayee(sessionId: Long, normalizedPayee: String, payeeId: Long): EmptyResult<DataError.Local> {
        transactions.replaceAll {
            if (it.sessionId == sessionId && it.normalizedPayee == normalizedPayee) it.copy(payeeId = payeeId) else it
        }
        return Result.Success(Unit)
    }

    // Export is a Settings concern and is covered by its own tests against a fake of its own;
    // nothing in the import path under test here reads it.
    override suspend fun exportRows(): Result<List<TransactionExportRow>, DataError.Local> =
        Result.Success(emptyList())

    // Mirrors the DAO's `AND isExcluded = 0`: an excluded row is not waiting to be mapped.
    override suspend fun unmappedCount(sessionId: Long): Result<Int, DataError.Local> =
        if (failOnUnmappedCount) {
            Result.Error(DataError.Local.UNKNOWN)
        } else {
            Result.Success(
                transactions.count { it.sessionId == sessionId && it.payeeId == null && !it.isExcluded }
            )
        }
}

/** Seeded by statement name, the way `payee_identifiers` keys them. */
class FakePayeeDataSource(initial: Map<String, Payee> = emptyMap()) : PayeeLocalDataSource {
    private val byIdentifier = initial.toMutableMap()

    override fun observeByIdentifier(): Flow<Map<String, Payee>> =
        MutableStateFlow(byIdentifier.toMap())

    override fun observeByNormalizedName(normalizedName: String): Flow<Payee?> =
        MutableStateFlow(byIdentifier[normalizedName])

    override suspend fun findByNormalizedName(normalizedName: String): Result<Payee?, DataError.Local> =
        Result.Success(byIdentifier[normalizedName])

    override fun observeAll(): Flow<List<Payee>> =
        MutableStateFlow(byIdentifier.values.distinctBy { it.id }.sortedBy { it.alias })

    override fun observeLinkedIdentifiers(normalizedName: String): Flow<List<PayeeIdentifier>> {
        val payeeId = byIdentifier[normalizedName]?.id
            ?: return MutableStateFlow(emptyList())
        return MutableStateFlow(
            byIdentifier.filterValues { it.id == payeeId }.map { (name, payee) ->
                PayeeIdentifier(payeeId = payee.id, rawName = name, normalizedName = name)
            }
        )
    }

    override suspend fun findByAlias(alias: String): Result<Payee?, DataError.Local> =
        Result.Success(byIdentifier.values.find { it.alias.equals(alias, ignoreCase = true) })

    override suspend fun saveMapping(
        rawName: String,
        normalizedName: String,
        alias: String,
        categoryId: Long
    ): Result<Long, DataError.Local> {
        val id = byIdentifier[normalizedName]?.id
            ?: ((byIdentifier.values.maxOfOrNull { it.id } ?: 0L) + 1)
        byIdentifier[normalizedName] = Payee(id = id, alias = alias, categoryId = categoryId)
        return Result.Success(id)
    }

    /** Every name the source payee owned moves across, mirroring the merge Room performs. */
    override suspend fun linkToPayee(
        rawName: String,
        normalizedName: String,
        targetPayeeId: Long
    ): EmptyResult<DataError.Local> {
        val target = byIdentifier.values.find { it.id == targetPayeeId }
            ?: return Result.Error(DataError.Local.UNKNOWN)
        val sourceId = byIdentifier[normalizedName]?.id
        byIdentifier.keys
            .filter { byIdentifier[it]?.id == sourceId }
            .forEach { byIdentifier[it] = target }
        byIdentifier[normalizedName] = target
        return Result.Success(Unit)
    }
}

class FakeUploadLogDataSource : UploadLogLocalDataSource {
    val logs = mutableListOf<UploadLog>()

    override fun observeAll(): Flow<List<UploadLog>> = MutableStateFlow(logs.toList())

    override suspend fun log(log: UploadLog): EmptyResult<DataError.Local> {
        logs += log
        return Result.Success(Unit)
    }
}
