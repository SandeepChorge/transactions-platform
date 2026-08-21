package com.madtitan94.transactionsparser.core.domain.datasource

import androidx.paging.PagingData
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
    /** Statement name -> the payee it resolves to, for every mapped name in the account. */
    fun observeByIdentifier(): Flow<Map<String, Payee>>
    /** Null until this statement name is mapped, so a detail screen can offer to map it. */
    fun observeByNormalizedName(normalizedName: String): Flow<Payee?>
    suspend fun findByNormalizedName(normalizedName: String): Result<Payee?, DataError.Local>

    /** Every payee in the account, alias order — the pool alias typeahead suggests from. */
    fun observeAll(): Flow<List<Payee>>

    /**
     * The statement names owned by whoever owns [normalizedName], that name included. Empty for
     * an unmapped name; one entry for a payee with a single identifier.
     */
    fun observeLinkedIdentifiers(normalizedName: String): Flow<List<PayeeIdentifier>>

    /**
     * The payee already using this alias, matched case-insensitively, or null if it is free.
     * Drives the prompt that offers to merge rather than create a second payee of the same name.
     */
    suspend fun findByAlias(alias: String): Result<Payee?, DataError.Local>

    /**
     * Points one statement name at an alias and category, creating the payee the first time that
     * name is mapped and updating whichever payee already owns it after that. Returns the payee id.
     */
    suspend fun saveMapping(
        rawName: String,
        normalizedName: String,
        alias: String,
        categoryId: Long
    ): Result<Long, DataError.Local>

    /**
     * Makes [normalizedName] one of [targetPayeeId]'s statement names.
     *
     * If the name is not mapped yet it simply joins the target. If it already belongs to another
     * payee, that payee is merged away: its identifiers and its assigned transactions move to the
     * target and its now-empty row is deleted, so the history of both spellings ends up under one
     * payee instead of split across two.
     */
    suspend fun linkToPayee(
        rawName: String,
        normalizedName: String,
        targetPayeeId: Long
    ): EmptyResult<DataError.Local>
}

interface SessionLocalDataSource {
    fun observeSummaries(status: SessionStatus): Flow<List<SessionSummary>>
    suspend fun getById(id: Long): Result<StatementSession?, DataError.Local>
    suspend fun insert(session: StatementSession): Result<Long, DataError.Local>
    suspend fun updateStatus(id: Long, status: SessionStatus): EmptyResult<DataError.Local>
}

interface TransactionLocalDataSource {
    fun observeBySession(sessionId: Long): Flow<List<Transaction>>

    /**
     * One payee's transactions across every session, newest first, loaded a page at a time.
     * Keyed on the statement name so an unmapped payee has a history too. Excluded rows are
     * included — the list is where the user sees and reverses that decision.
     *
     * [includeLinkedNames] is what makes this the *payee's* history rather than one spelling's:
     * on by default, it pulls in every other statement name the same payee owns. The detail
     * screen turns it off to filter down to a single identifier.
     */
    fun observePagedByPayee(
        normalizedPayee: String,
        includeLinkedNames: Boolean = true
    ): Flow<PagingData<Transaction>>

    /** Header aggregates for one payee, computed in SQL over all of their rows. */
    fun observePayeeTotals(
        normalizedPayee: String,
        includeLinkedNames: Boolean = true
    ): Flow<PayeeTotals>

    /** Per-day and per-month subtotals for one payee, for the list's section headers. */
    fun observePayeeDayTotals(
        normalizedPayee: String,
        includeLinkedNames: Boolean = true
    ): Flow<List<PeriodTotal>>

    fun observePayeeMonthTotals(
        normalizedPayee: String,
        includeLinkedNames: Boolean = true
    ): Flow<List<PeriodTotal>>

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

    /**
     * Every transaction of this account, flattened for export with its mapping resolved.
     *
     * Deliberately a one-shot list rather than a [Flow] or a [PagingData] stream: an export is a
     * snapshot of a moment, and a file that changed while it was being written would be worse
     * than one that is merely a few seconds old.
     */
    suspend fun exportRows(): Result<List<TransactionExportRow>, DataError.Local>
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
