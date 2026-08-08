package com.madtitan94.transactionsparser.feature.upload.domain

import com.madtitan94.transactionsparser.core.domain.datasource.PayeeLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.SessionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.TransactionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.UploadLogLocalDataSource
import com.madtitan94.transactionsparser.core.domain.model.Payee
import com.madtitan94.transactionsparser.core.domain.model.SessionStatus
import com.madtitan94.transactionsparser.core.domain.model.SessionSummary
import com.madtitan94.transactionsparser.core.domain.model.StatementSession
import com.madtitan94.transactionsparser.core.domain.model.StatementSource
import com.madtitan94.transactionsparser.core.domain.model.Transaction
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

    override suspend fun updateStatus(id: Long, status: SessionStatus): EmptyResult<DataError.Local> =
        Result.Success(Unit)
}

class FakeTransactionDataSource : TransactionLocalDataSource {
    val transactions = mutableListOf<Transaction>()

    override fun observeBySession(sessionId: Long): Flow<List<Transaction>> =
        MutableStateFlow(transactions.toList()).map { list -> list.filter { it.sessionId == sessionId } }

    override suspend fun insertAll(transactions: List<Transaction>): EmptyResult<DataError.Local> {
        this.transactions += transactions
        return Result.Success(Unit)
    }

    override suspend fun assignPayee(sessionId: Long, normalizedPayee: String, payeeId: Long): EmptyResult<DataError.Local> {
        transactions.replaceAll {
            if (it.sessionId == sessionId && it.normalizedPayee == normalizedPayee) it.copy(payeeId = payeeId) else it
        }
        return Result.Success(Unit)
    }

    override suspend fun unmappedCount(sessionId: Long): Result<Int, DataError.Local> =
        Result.Success(transactions.count { it.sessionId == sessionId && it.payeeId == null })
}

class FakePayeeDataSource(initial: List<Payee> = emptyList()) : PayeeLocalDataSource {
    private val payees = initial.toMutableList()

    override fun observeAll(): Flow<List<Payee>> = MutableStateFlow(payees.toList())

    override suspend fun findByNormalizedName(normalizedName: String): Result<Payee?, DataError.Local> =
        Result.Success(payees.find { it.normalizedName == normalizedName })

    override suspend fun save(payee: Payee): Result<Long, DataError.Local> {
        val existing = payees.find { it.normalizedName == payee.normalizedName }
        return if (existing == null) {
            val saved = payee.copy(id = (payees.maxOfOrNull { it.id } ?: 0L) + 1)
            payees += saved
            Result.Success(saved.id)
        } else {
            payees.replaceAll { if (it.normalizedName == payee.normalizedName) payee.copy(id = existing.id) else it }
            Result.Success(existing.id)
        }
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
