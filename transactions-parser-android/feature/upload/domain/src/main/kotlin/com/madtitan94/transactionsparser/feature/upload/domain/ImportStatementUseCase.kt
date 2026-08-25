package com.madtitan94.transactionsparser.feature.upload.domain

import com.madtitan94.transactionsparser.core.domain.datasource.PayeeLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.SessionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.TransactionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.UploadLogLocalDataSource
import com.madtitan94.transactionsparser.core.domain.duplicate.DuplicateDetector
import com.madtitan94.transactionsparser.core.domain.model.Payee
import com.madtitan94.transactionsparser.core.domain.model.SessionStatus
import com.madtitan94.transactionsparser.core.domain.model.StatementSession
import com.madtitan94.transactionsparser.core.domain.model.StatementSource
import com.madtitan94.transactionsparser.core.domain.model.Transaction
import com.madtitan94.transactionsparser.core.domain.model.UploadLog
import com.madtitan94.transactionsparser.core.domain.model.normalizePayee
import com.madtitan94.transactionsparser.core.domain.parsing.ParseError
import com.madtitan94.transactionsparser.core.domain.parsing.ParsedStatement
import com.madtitan94.transactionsparser.core.domain.parsing.StatementParserRegistry
import com.madtitan94.transactionsparser.core.domain.parsing.StatementTextExtractor
import com.madtitan94.transactionsparser.core.domain.util.Result

data class ImportResult(
    val sessionId: Long,
    val source: StatementSource,
    val totalTransactions: Int,
    val autoMappedPayees: Int,
    /** Rows detected as repeats of earlier imports. Kept and flagged, never dropped. */
    val duplicateTransactions: Int,
    /** True when the import left nothing to map, so the session went straight to COMPLETED. */
    val completedOnImport: Boolean = false
)

/**
 * Extracts, parses and persists an uploaded statement as a new PENDING session.
 *
 * Payees already mapped in earlier sessions (matched by normalized statement name)
 * are auto-assigned so the user only confirms them once — the "game changer" flow.
 */
class ImportStatementUseCase(
    private val extractor: StatementTextExtractor,
    private val parserRegistry: StatementParserRegistry,
    private val sessions: SessionLocalDataSource,
    private val transactions: TransactionLocalDataSource,
    private val payees: PayeeLocalDataSource,
    private val uploadLogs: UploadLogLocalDataSource,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {

    suspend operator fun invoke(tempFilePath: String, fileName: String): Result<ImportResult, ParseError> {
        val text = when (val extraction = extractor.extractText(tempFilePath)) {
            is Result.Error -> return failure(fileName, extraction.error, source = null)
            is Result.Success -> extraction.data
        }

        val parser = parserRegistry.findFor(text)
            ?: return failure(fileName, ParseError.UNRECOGNIZED_FORMAT, source = null)

        val parsed = when (val parseResult = parser.parse(text)) {
            is Result.Error -> return failure(fileName, parseResult.error, parser.source)
            is Result.Success -> parseResult.data
        }

        return persist(parsed, fileName)
    }

    private suspend fun persist(parsed: ParsedStatement, fileName: String): Result<ImportResult, ParseError> {
        val sessionId = when (
            val inserted = sessions.insert(
                StatementSession(
                    fileName = fileName,
                    source = parsed.source,
                    uploadedAtMillis = nowMillis(),
                    periodStartMillis = parsed.periodStartMillis,
                    periodEndMillis = parsed.periodEndMillis,
                    status = SessionStatus.PENDING
                )
            )
        ) {
            is Result.Error -> return failure(fileName, ParseError.STORAGE_FAILURE, parsed.source)
            is Result.Success -> inserted.data
        }

        val knownPayees = knownPayeesFor(parsed)
        val toInsert = parsed.transactions.map { txn ->
            val normalized = normalizePayee(txn.rawPayee)
            Transaction(
                sessionId = sessionId,
                dateTimeUtcMillis = txn.dateTimeUtcMillis,
                rawPayee = txn.rawPayee,
                normalizedPayee = normalized,
                amountPaise = txn.amountPaise,
                type = txn.type,
                transactionRef = txn.transactionRef,
                utr = txn.utr,
                payeeId = knownPayees[normalized]?.id
            )
        }

        // Detection can't be allowed to sink an otherwise-good import, so a lookup failure
        // falls back to inserting everything unflagged rather than aborting.
        val existingKeys = when (val found = transactions.findDuplicateKeys(toInsert)) {
            is Result.Error -> emptyList()
            is Result.Success -> found.data
        }
        val flagged = DuplicateDetector.flag(toInsert, existingKeys)

        if (transactions.insertAll(flagged) is Result.Error) {
            return failure(fileName, ParseError.STORAGE_FAILURE, parsed.source)
        }

        uploadLogs.log(
            UploadLog(
                fileName = fileName,
                uploadedAtMillis = nowMillis(),
                success = true,
                source = parsed.source,
                failureReason = null,
                sessionId = sessionId
            )
        )

        // A statement can arrive with nothing left to map — every payee already known from an
        // earlier session, or every row flagged as a repeat. There is no button to press in that
        // case, so completion has to happen here or the session sits PENDING forever. On a lookup
        // failure we leave it PENDING, which is the behaviour it had before this check existed.
        val needsMapping = (transactions.unmappedCount(sessionId) as? Result.Success)?.data ?: 1
        val completedOnImport = needsMapping == 0
        if (completedOnImport) {
            sessions.updateStatus(sessionId, SessionStatus.COMPLETED)
        }

        return Result.Success(
            ImportResult(
                sessionId = sessionId,
                source = parsed.source,
                totalTransactions = flagged.size,
                autoMappedPayees = knownPayees.size,
                duplicateTransactions = flagged.count { it.isDuplicate },
                completedOnImport = completedOnImport
            )
        )
    }

    private suspend fun knownPayeesFor(parsed: ParsedStatement): Map<String, Payee> {
        return parsed.transactions
            .map { normalizePayee(it.rawPayee) }
            .distinct()
            .mapNotNull { normalized ->
                val found = payees.findByNormalizedName(normalized)
                if (found is Result.Success) found.data?.let { normalized to it } else null
            }
            .toMap()
    }

    private suspend fun failure(
        fileName: String,
        error: ParseError,
        source: StatementSource?
    ): Result<ImportResult, ParseError> {
        uploadLogs.log(
            UploadLog(
                fileName = fileName,
                uploadedAtMillis = nowMillis(),
                success = false,
                source = source,
                failureReason = error.name,
                sessionId = null
            )
        )
        return Result.Error(error)
    }
}
