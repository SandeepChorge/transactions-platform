package com.madtitan94.transactionsparser.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.madtitan94.transactionsparser.core.database.entity.CategoryEntity
import com.madtitan94.transactionsparser.core.database.entity.PayeeEntity
import com.madtitan94.transactionsparser.core.database.entity.PayeeIdentifierEntity
import com.madtitan94.transactionsparser.core.database.entity.SessionEntity
import com.madtitan94.transactionsparser.core.database.entity.TransactionEntity
import com.madtitan94.transactionsparser.core.database.entity.UploadLogEntity
import kotlinx.coroutines.flow.Flow

data class CategoryLinkCount(val categoryId: Long, val count: Int)

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE ownerId = :ownerId AND isDeleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeAll(ownerId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE ownerId = :ownerId AND isDeleted = 1 ORDER BY deletedAtMillis DESC")
    fun observeDeleted(ownerId: String): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT categoryId AS categoryId, COUNT(*) AS count
        FROM payees
        WHERE ownerId = :ownerId AND isDeleted = 0
        GROUP BY categoryId
        """
    )
    fun observeLinkedCounts(ownerId: String): Flow<List<CategoryLinkCount>>

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Query("UPDATE categories SET name = :name WHERE id = :id AND ownerId = :ownerId")
    suspend fun rename(ownerId: String, id: Long, name: String)

    @Query(
        "UPDATE categories SET isDeleted = 1, deletedAtMillis = :deletedAtMillis " +
            "WHERE id = :id AND ownerId = :ownerId"
    )
    suspend fun softDelete(ownerId: String, id: Long, deletedAtMillis: Long)

    @Query(
        "UPDATE categories SET isDeleted = 0, deletedAtMillis = NULL " +
            "WHERE id = :id AND ownerId = :ownerId"
    )
    suspend fun restore(ownerId: String, id: Long)

    @Query("SELECT COUNT(*) FROM payees WHERE ownerId = :ownerId AND isDeleted = 0 AND categoryId = :id")
    suspend fun linkedPayeeCount(ownerId: String, id: Long): Int
}

private const val PAYEE_BY_IDENTIFIER =
    "SELECT p.* FROM payees p " +
        "JOIN payee_identifiers i ON i.payeeId = p.id AND i.ownerId = p.ownerId " +
        "WHERE p.ownerId = :ownerId AND p.isDeleted = 0 AND i.normalizedName = :normalizedName " +
        "LIMIT 1"

/** One statement name and the payee it resolves to. */
data class PayeeByIdentifierRow(
    val normalizedName: String,
    @Embedded val payee: PayeeEntity
)

@Dao
interface PayeeDao {
    /**
     * Every mapped statement name in the account, each with its payee — one row per identifier,
     * so a payee that owns several appears once per name. That shape is what auto-map suggestions
     * need: they start from a name on a statement and ask who it is.
     */
    @Query(
        "SELECT i.normalizedName AS normalizedName, p.* FROM payee_identifiers i " +
            "JOIN payees p ON p.id = i.payeeId AND p.ownerId = i.ownerId " +
            "WHERE i.ownerId = :ownerId AND p.isDeleted = 0"
    )
    fun observeByIdentifier(ownerId: String): Flow<List<PayeeByIdentifierRow>>

    /**
     * The payee a statement name resolves to, looked up through its identifiers rather than
     * through a name column on the payee itself — so once a name has been merged into another
     * payee, it resolves to the payee that now owns it instead of to the one it was created as.
     *
     * `LIMIT 1` is belt and braces: `index_payee_identifiers_ownerId_normalizedName` is unique,
     * so a name can only match one identifier per account.
     */
    @Query(PAYEE_BY_IDENTIFIER)
    fun observeByNormalizedName(ownerId: String, normalizedName: String): Flow<PayeeEntity?>

    @Query(PAYEE_BY_IDENTIFIER)
    suspend fun findByNormalizedName(ownerId: String, normalizedName: String): PayeeEntity?

    /** Every payee in the account, alias order — what alias typeahead matches against. */
    @Query(
        "SELECT * FROM payees WHERE ownerId = :ownerId AND isDeleted = 0 " +
            "ORDER BY alias COLLATE NOCASE"
    )
    fun observeAll(ownerId: String): Flow<List<PayeeEntity>>

    /**
     * The payee already answering to this alias, if any.
     *
     * `COLLATE NOCASE` so "Swiggy" and "swiggy" are recognised as the same person — the prompt
     * that offers to merge them is only worth having if it fires on the spelling a user types,
     * not just on an exact byte match.
     */
    @Query(
        "SELECT * FROM payees WHERE ownerId = :ownerId AND isDeleted = 0 " +
            "AND alias = :alias COLLATE NOCASE LIMIT 1"
    )
    suspend fun findByAlias(ownerId: String, alias: String): PayeeEntity?

    @Insert
    suspend fun insert(payee: PayeeEntity): Long

    @Update
    suspend fun update(payee: PayeeEntity)

    /**
     * Hands every identifier of one payee to another. Runs before [delete] in a merge: the
     * identifier FK is `RESTRICT`, so a merge that skipped this step fails loudly at the delete
     * instead of quietly taking the names with it.
     */
    @Query(
        "UPDATE payee_identifiers SET payeeId = :targetId " +
            "WHERE ownerId = :ownerId AND payeeId = :sourceId"
    )
    suspend fun repointIdentifiers(ownerId: String, sourceId: Long, targetId: Long)

    /**
     * Moves the transactions already assigned to one payee onto another.
     *
     * Lives on `PayeeDao` despite writing `transactions`: a merge necessarily rewrites three
     * tables, and keeping all three statements here lets one data source run them inside a single
     * `withTransaction` rather than reaching across data sources for a half of the operation.
     */
    @Query(
        "UPDATE transactions SET payeeId = :targetId " +
            "WHERE ownerId = :ownerId AND payeeId = :sourceId"
    )
    suspend fun repointTransactions(ownerId: String, sourceId: Long, targetId: Long)

    /** Hard delete — the merged-away payee has no identifiers and nothing left to recover. */
    @Query("DELETE FROM payees WHERE ownerId = :ownerId AND id = :id")
    suspend fun delete(ownerId: String, id: Long)
}

@Dao
interface PayeeIdentifierDao {
    /**
     * Every statement name owned by whoever owns [normalizedName], the name itself included.
     * Empty for an unmapped name, which owns no identifier — the detail screen shows the section
     * only when there is more than one, so that degenerate case needs no special handling.
     */
    @Query(
        "SELECT * FROM payee_identifiers WHERE ownerId = :ownerId AND payeeId IN (" +
            "SELECT payeeId FROM payee_identifiers " +
            "WHERE ownerId = :ownerId AND normalizedName = :normalizedName) " +
            "ORDER BY rawName COLLATE NOCASE"
    )
    fun observeLinkedTo(ownerId: String, normalizedName: String): Flow<List<PayeeIdentifierEntity>>

    @Insert
    suspend fun insert(identifier: PayeeIdentifierEntity): Long
}

/**
 * [countedCount] and [mappedCount] respect the user's exclusions; [transactionCount] covers every
 * row, so a session whose transactions all repeat an earlier import still reports what it imported
 * instead of reading as an empty — or failed — upload.
 */
data class SessionSummaryRow(
    @Embedded val session: SessionEntity,
    val transactionCount: Int,
    val countedCount: Int,
    val mappedCount: Int
)

@Dao
interface SessionDao {
    @Query(
        """
        SELECT s.*,
               COUNT(t.id) AS transactionCount,
               IFNULL(SUM(CASE WHEN t.isExcluded = 0 THEN 1 ELSE 0 END), 0) AS countedCount,
               IFNULL(
                   SUM(CASE WHEN t.isExcluded = 0 AND t.payeeId IS NOT NULL THEN 1 ELSE 0 END), 0
               ) AS mappedCount
        FROM sessions s
        LEFT JOIN transactions t ON t.sessionId = s.id AND t.isDeleted = 0
        WHERE s.ownerId = :ownerId AND s.isDeleted = 0 AND s.status = :status
        GROUP BY s.id
        ORDER BY s.uploadedAtMillis DESC
        """
    )
    fun observeSummaries(ownerId: String, status: String): Flow<List<SessionSummaryRow>>

    @Query("SELECT * FROM sessions WHERE id = :id AND ownerId = :ownerId AND isDeleted = 0")
    suspend fun getById(ownerId: String, id: Long): SessionEntity?

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("UPDATE sessions SET status = :status WHERE id = :id AND ownerId = :ownerId")
    suspend fun updateStatus(ownerId: String, id: Long, status: String)
}

/** Just enough of an existing row to decide whether an incoming one repeats it. */
data class DuplicateCandidate(
    val id: Long,
    val transactionRef: String?,
    val utr: String?,
    val normalizedPayee: String,
    val amountPaise: Long,
    val dateTimeUtcMillis: Long
)

/** Header aggregates for one payee. Nullable because a payee with no rows yields all-NULL. */
data class PayeeTotalsRow(
    val countedTotalPaise: Long?,
    val countedCount: Int,
    val transactionCount: Int,
    val duplicateCount: Int,
    val excludedDuplicateCount: Int,
    val firstMillis: Long?,
    val lastMillis: Long?
)

data class PeriodTotalRow(
    val startMillis: Long,
    val countedTotalPaise: Long?,
    val countedCount: Int
)

/** One export row with the payee mapping and statement already joined in. */
data class TransactionExportRowEntity(
    val dateTimeUtcMillis: Long,
    val rawPayee: String,
    val alias: String?,
    val category: String?,
    val amountPaise: Long,
    val type: String,
    val transactionRef: String?,
    val utr: String?,
    val isDuplicate: Boolean,
    val isExcluded: Boolean,
    val statementFileName: String
)

/** Milliseconds in a day — the divisor that floors a timestamp to its day. */
private const val DAY_MILLIS = 86_400_000

/**
 * Matches every transaction belonging to the same payee as `:normalizedPayee` — that name and
 * every other statement name its payee owns.
 *
 * The union of "itself" and "its siblings" is what lets one predicate serve both cases. A mapped
 * name matches the whole identifier set, which is the Phase 5 fix: after a merge, one identifier's
 * history no longer hides the rest. An unmapped name owns no identifier at all, so the subquery is
 * empty and only the first branch fires — it still has a detail screen, showing exactly itself.
 *
 * `:includeLinkedNames` collapses the predicate back to a single name, which is what the detail
 * screen's per-identifier filter binds. One parameterised query rather than an exact-match twin of
 * each of the four, so a filtered header can never drift from the filtered list it sits above.
 *
 * Deliberately matched by name rather than by `transactions.payeeId`: a row imported before its
 * payee was ever mapped keeps a null `payeeId`, and dropping that row out of the payee's own
 * history would be a regression on every account with pre-mapping statements.
 */
private const val SAME_PAYEE_NAMES =
    "(normalizedPayee = :normalizedPayee OR (:includeLinkedNames = 1 AND normalizedPayee IN (" +
        "SELECT sibling.normalizedName FROM payee_identifiers self " +
        "JOIN payee_identifiers sibling " +
        "ON sibling.payeeId = self.payeeId AND sibling.ownerId = self.ownerId " +
        "WHERE self.ownerId = :ownerId AND self.normalizedName = :normalizedPayee)))"

@Dao
interface TransactionDao {
    @Query(
        "SELECT * FROM transactions WHERE ownerId = :ownerId AND isDeleted = 0 " +
            "AND sessionId = :sessionId ORDER BY dateTimeUtcMillis DESC"
    )
    fun observeBySession(ownerId: String, sessionId: Long): Flow<List<TransactionEntity>>

    /**
     * One payee's history across every session, keyed on the statement name rather than the
     * mapping, so an unmapped payee has a detail view too — see [SAME_PAYEE_NAMES] for how a
     * merged payee's other names join in. Excluded rows are deliberately kept in the list: hiding
     * them would leave no way to reverse an exclusion the user disagrees with.
     */
    @Query(
        "SELECT * FROM transactions WHERE ownerId = :ownerId AND isDeleted = 0 " +
            "AND $SAME_PAYEE_NAMES ORDER BY dateTimeUtcMillis DESC, id DESC"
    )
    fun pagingByPayee(
        ownerId: String,
        normalizedPayee: String,
        includeLinkedNames: Boolean
    ): PagingSource<Int, TransactionEntity>

    @Query(
        """
        SELECT SUM(CASE WHEN isExcluded = 0 THEN amountPaise ELSE 0 END) AS countedTotalPaise,
               SUM(CASE WHEN isExcluded = 0 THEN 1 ELSE 0 END) AS countedCount,
               COUNT(*) AS transactionCount,
               SUM(CASE WHEN isDuplicate = 1 THEN 1 ELSE 0 END) AS duplicateCount,
               SUM(CASE WHEN isDuplicate = 1 AND isExcluded = 1 THEN 1 ELSE 0 END)
                   AS excludedDuplicateCount,
               MIN(dateTimeUtcMillis) AS firstMillis,
               MAX(dateTimeUtcMillis) AS lastMillis
        FROM transactions
        WHERE ownerId = :ownerId AND isDeleted = 0 AND $SAME_PAYEE_NAMES
        """
    )
    fun observePayeeTotals(
        ownerId: String,
        normalizedPayee: String,
        includeLinkedNames: Boolean
    ): Flow<PayeeTotalsRow?>

    /**
     * Day subtotals, bucketed by flooring the timestamp to a whole day.
     *
     * Statement times are stored as the printed wall clock read back as UTC (see
     * `statementDateTime`), so a plain UTC floor lands on exactly the day the row displays under.
     * No timezone or DST correction applies — introducing one here would move rows into the
     * wrong bucket.
     */
    @Query(
        """
        SELECT (dateTimeUtcMillis / $DAY_MILLIS) * $DAY_MILLIS AS startMillis,
               SUM(CASE WHEN isExcluded = 0 THEN amountPaise ELSE 0 END) AS countedTotalPaise,
               SUM(CASE WHEN isExcluded = 0 THEN 1 ELSE 0 END) AS countedCount
        FROM transactions
        WHERE ownerId = :ownerId AND isDeleted = 0 AND $SAME_PAYEE_NAMES
        GROUP BY startMillis
        ORDER BY startMillis DESC
        """
    )
    fun observePayeeDayTotals(
        ownerId: String,
        normalizedPayee: String,
        includeLinkedNames: Boolean
    ): Flow<List<PeriodTotalRow>>

    /** Months need calendar arithmetic rather than a divisor, hence `start of month`. */
    @Query(
        """
        SELECT CAST(strftime('%s', dateTimeUtcMillis / 1000, 'unixepoch', 'start of month')
                    AS INTEGER) * 1000 AS startMillis,
               SUM(CASE WHEN isExcluded = 0 THEN amountPaise ELSE 0 END) AS countedTotalPaise,
               SUM(CASE WHEN isExcluded = 0 THEN 1 ELSE 0 END) AS countedCount
        FROM transactions
        WHERE ownerId = :ownerId AND isDeleted = 0 AND $SAME_PAYEE_NAMES
        GROUP BY startMillis
        ORDER BY startMillis DESC
        """
    )
    fun observePayeeMonthTotals(
        ownerId: String,
        normalizedPayee: String,
        includeLinkedNames: Boolean
    ): Flow<List<PeriodTotalRow>>

    /**
     * Every row of this account, with its mapping resolved, for CSV export.
     *
     * `LEFT JOIN` throughout: an unmapped payee has no `payees` row and must still export, with
     * empty alias and category cells. Excluded rows are exported too — the flag rides along as a
     * column so the file agrees with the app instead of quietly holding fewer transactions than
     * the screen shows.
     */
    @Query(
        """
        SELECT t.dateTimeUtcMillis AS dateTimeUtcMillis,
               t.rawPayee AS rawPayee,
               p.alias AS alias,
               c.name AS category,
               t.amountPaise AS amountPaise,
               t.type AS type,
               t.transactionRef AS transactionRef,
               t.utr AS utr,
               t.isDuplicate AS isDuplicate,
               t.isExcluded AS isExcluded,
               s.fileName AS statementFileName
        FROM transactions t
        LEFT JOIN payees p ON p.id = t.payeeId AND p.isDeleted = 0
        LEFT JOIN categories c ON c.id = p.categoryId AND c.isDeleted = 0
        LEFT JOIN sessions s ON s.id = t.sessionId
        WHERE t.ownerId = :ownerId AND t.isDeleted = 0
        ORDER BY t.dateTimeUtcMillis DESC, t.id DESC
        """
    )
    suspend fun exportRows(ownerId: String): List<TransactionExportRowEntity>

    @Insert
    suspend fun insertAll(transactions: List<TransactionEntity>)

    /**
     * Existing rows carrying any of these refs or UTRs, across every session of this account.
     * Matching on identity rather than date is what makes an overlapping-date-range re-import
     * fall out for free — no separate period-overlap logic needed.
     */
    @Query(
        """
        SELECT id, transactionRef, utr, normalizedPayee, amountPaise, dateTimeUtcMillis
        FROM transactions
        WHERE ownerId = :ownerId AND isDeleted = 0
          AND (
            (transactionRef IS NOT NULL AND transactionRef IN (:refs))
            OR (utr IS NOT NULL AND utr IN (:utrs))
          )
        """
    )
    suspend fun findByRefOrUtr(
        ownerId: String,
        refs: List<String>,
        utrs: List<String>
    ): List<DuplicateCandidate>

    /**
     * Candidates for rows that carry neither a ref nor a UTR (Google Pay statements can produce
     * these). Narrowed by timestamp here; payee and amount are compared by the caller.
     */
    @Query(
        """
        SELECT id, transactionRef, utr, normalizedPayee, amountPaise, dateTimeUtcMillis
        FROM transactions
        WHERE ownerId = :ownerId AND isDeleted = 0
          AND transactionRef IS NULL AND utr IS NULL
          AND dateTimeUtcMillis IN (:timestamps)
        """
    )
    suspend fun findReflessAt(
        ownerId: String,
        timestamps: List<Long>
    ): List<DuplicateCandidate>

    @Query(
        "UPDATE transactions SET isExcluded = :isExcluded WHERE id = :id AND ownerId = :ownerId"
    )
    suspend fun setExcluded(ownerId: String, id: Long, isExcluded: Boolean)

    /**
     * Flips only the flagged rows of one payee within a session. Scoped to `isDuplicate = 1` so
     * re-including duplicates can never sweep in a row the user excluded for their own reasons.
     */
    @Query(
        "UPDATE transactions SET isExcluded = :isExcluded WHERE ownerId = :ownerId AND isDeleted = 0 " +
            "AND sessionId = :sessionId AND normalizedPayee = :normalizedPayee AND isDuplicate = 1"
    )
    suspend fun setDuplicatesExcluded(
        ownerId: String,
        sessionId: Long,
        normalizedPayee: String,
        isExcluded: Boolean
    )

    @Query(
        "UPDATE transactions SET payeeId = :payeeId WHERE ownerId = :ownerId AND isDeleted = 0 " +
            "AND sessionId = :sessionId AND normalizedPayee = :normalizedPayee"
    )
    suspend fun assignPayee(ownerId: String, sessionId: Long, normalizedPayee: String, payeeId: Long)

    /** Excluded rows are skipped — mapping a transaction that counts toward nothing is busywork. */
    @Query(
        "SELECT COUNT(*) FROM transactions WHERE ownerId = :ownerId AND isDeleted = 0 " +
            "AND isExcluded = 0 AND sessionId = :sessionId AND payeeId IS NULL"
    )
    suspend fun unmappedCount(ownerId: String, sessionId: Long): Int
}

@Dao
interface UploadLogDao {
    @Query(
        "SELECT * FROM upload_logs WHERE ownerId = :ownerId AND isDeleted = 0 " +
            "ORDER BY uploadedAtMillis DESC"
    )
    fun observeAll(ownerId: String): Flow<List<UploadLogEntity>>

    @Insert
    suspend fun insert(log: UploadLogEntity)
}

/**
 * Whole-table reads for a backup.
 *
 * Every query here deliberately omits the `isDeleted = 0` filter that the feature DAOs apply. A
 * backup is a copy of the database, not of what a screen shows: dropping soft-deleted rows would
 * quietly empty Settings › Recently deleted on the far side of a restore, and there would be no
 * way to notice until someone went looking for something they deleted.
 *
 * Ordered by id so two backups of the same data produce the same file, which is what makes a
 * round-trip test meaningful and a diff of two backups readable.
 */
@Dao
interface BackupDao {
    @Query("SELECT * FROM categories WHERE ownerId = :ownerId ORDER BY id")
    suspend fun categories(ownerId: String): List<CategoryEntity>

    @Query("SELECT * FROM payees WHERE ownerId = :ownerId ORDER BY id")
    suspend fun payees(ownerId: String): List<PayeeEntity>

    @Query("SELECT * FROM payee_identifiers WHERE ownerId = :ownerId ORDER BY id")
    suspend fun payeeIdentifiers(ownerId: String): List<PayeeIdentifierEntity>

    @Query("SELECT * FROM sessions WHERE ownerId = :ownerId ORDER BY id")
    suspend fun sessions(ownerId: String): List<SessionEntity>

    @Query("SELECT * FROM transactions WHERE ownerId = :ownerId ORDER BY id")
    suspend fun transactions(ownerId: String): List<TransactionEntity>

    @Query("SELECT * FROM upload_logs WHERE ownerId = :ownerId ORDER BY id")
    suspend fun uploadLogs(ownerId: String): List<UploadLogEntity>

    // The restore side. Every insert returns the ids SQLite assigned, in the order the rows were
    // given, because that mapping from the file's ids to this database's is the whole job — the
    // file's ids belong to another database and every reference between the tables has to be
    // rebuilt against these.

    @Insert
    suspend fun insertCategories(rows: List<CategoryEntity>): List<Long>

    @Insert
    suspend fun insertPayees(rows: List<PayeeEntity>): List<Long>

    @Insert
    suspend fun insertPayeeIdentifiers(rows: List<PayeeIdentifierEntity>): List<Long>

    @Insert
    suspend fun insertSessions(rows: List<SessionEntity>): List<Long>

    @Insert
    suspend fun insertTransactions(rows: List<TransactionEntity>): List<Long>

    @Insert
    suspend fun insertUploadLogs(rows: List<UploadLogEntity>): List<Long>

    /**
     * Points a restored duplicate at the row it repeats, once both have real ids.
     *
     * Has to be a second pass: the reference is to another row in the same table, so at insert time
     * the row it names may not exist yet.
     */
    @Query(
        "UPDATE transactions SET duplicateOfTransactionId = :targetId " +
            "WHERE id = :id AND ownerId = :ownerId"
    )
    suspend fun linkDuplicate(ownerId: String, id: Long, targetId: Long)
}

@Dao
interface LegacyOwnershipDao {
    @Query("UPDATE categories SET ownerId = :ownerId WHERE ownerId = :legacyOwnerId")
    suspend fun claimCategories(legacyOwnerId: String, ownerId: String)

    @Query("UPDATE payees SET ownerId = :ownerId WHERE ownerId = :legacyOwnerId")
    suspend fun claimPayees(legacyOwnerId: String, ownerId: String)

    @Query("UPDATE payee_identifiers SET ownerId = :ownerId WHERE ownerId = :legacyOwnerId")
    suspend fun claimPayeeIdentifiers(legacyOwnerId: String, ownerId: String)

    @Query("UPDATE sessions SET ownerId = :ownerId WHERE ownerId = :legacyOwnerId")
    suspend fun claimSessions(legacyOwnerId: String, ownerId: String)

    @Query("UPDATE transactions SET ownerId = :ownerId WHERE ownerId = :legacyOwnerId")
    suspend fun claimTransactions(legacyOwnerId: String, ownerId: String)

    @Query("UPDATE upload_logs SET ownerId = :ownerId WHERE ownerId = :legacyOwnerId")
    suspend fun claimUploadLogs(legacyOwnerId: String, ownerId: String)
}
