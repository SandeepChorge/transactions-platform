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
     * The whole payee directory, over all time: every named payee, plus every statement name
     * nobody has claimed yet.
     *
     * Two halves unioned rather than one grouped query, because the two kinds are found by
     * different routes and only one of them can be found at all when it has no spend:
     *
     * - **Named payees start from `payees`**, so a payee whose every transaction is excluded — or
     *   who was created before their statement was imported — still appears, at zero. Grouping
     *   transactions could not produce that row, because there is no row to group.
     * - **Unclaimed names start from `transactions`**, where the name is the only identity there
     *   is. A group with nothing countable in it should not exist at all, so the exclusions *and*
     *   the debit filter belong in its `WHERE` rather than in its aggregates. That is also what
     *   keeps this half agreeing with `observePayeeSummary.unmappedPayeeCount`, which filters the
     *   same way: a name that has only ever sent the user money is not an unmapped payee with work
     *   outstanding, and listing it at zero would pad the directory's to-do list with rows that can
     *   never move.
     *
     * On the named half the exclusions are **conditional aggregation, not join conditions** — a
     * payee whose spend is entirely excluded must read as zero rather than vanish, and `SUM` over
     * no matching rows yields NULL, which will not bind to a non-null `Long`. Both halves count
     * debits only, matching every other payee aggregate: a refund arriving from a shop is not spend
     * at it.
     *
     * The named half joins transactions once — on the stamped `payeeId`, or on a name this payee
     * owns when the stamp is missing — rather than joining `payee_identifiers` alongside them. A
     * second join would fan every transaction out once per identifier and multiply the totals of
     * exactly the merged payees this app exists to get right.
     */
    @Query(
        """
        SELECT p.id AS payeeId,
               p.alias AS alias,
               IFNULL((SELECT pi.rawName FROM payee_identifiers pi
                        WHERE pi.ownerId = p.ownerId AND pi.payeeId = p.id
                        ORDER BY pi.id LIMIT 1), p.alias) AS statementName,
               IFNULL((SELECT pi.normalizedName FROM payee_identifiers pi
                        WHERE pi.ownerId = p.ownerId AND pi.payeeId = p.id
                        ORDER BY pi.id LIMIT 1), '') AS normalizedName,
               c.name AS categoryName,
               (SELECT COUNT(*) FROM payee_identifiers pi
                 WHERE pi.ownerId = p.ownerId AND pi.payeeId = p.id) AS identifierCount,
               IFNULL(SUM(CASE WHEN t.id IS NOT NULL AND t.isDeleted = 0 AND t.isExcluded = 0
                                AND s.isDeleted = 0 AND s.status <> 'CANCELLED'
                                AND t.type = 'DEBIT'
                               THEN t.amountPaise ELSE 0 END), 0) AS totalPaise,
               COUNT(CASE WHEN t.isDeleted = 0 AND t.isExcluded = 0
                           AND s.isDeleted = 0 AND s.status <> 'CANCELLED'
                           AND t.type = 'DEBIT'
                          THEN t.id END) AS transactionCount
        FROM payees p
        JOIN categories c ON c.id = p.categoryId AND c.ownerId = p.ownerId
        LEFT JOIN transactions t ON t.ownerId = p.ownerId
             AND (t.payeeId = p.id
                  OR (t.payeeId IS NULL AND t.normalizedPayee IN (
                        SELECT pi.normalizedName FROM payee_identifiers pi
                        WHERE pi.ownerId = p.ownerId AND pi.payeeId = p.id)))
        LEFT JOIN sessions s ON s.id = t.sessionId AND s.ownerId = t.ownerId
        WHERE p.ownerId = :ownerId AND p.isDeleted = 0
        GROUP BY p.id

        UNION ALL

        SELECT NULL AS payeeId,
               NULL AS alias,
               MIN(t.rawPayee) AS statementName,
               t.normalizedPayee AS normalizedName,
               NULL AS categoryName,
               0 AS identifierCount,
               SUM(t.amountPaise) AS totalPaise,
               COUNT(t.id) AS transactionCount
        $COUNTABLE_ROWS_FROM
        LEFT JOIN payee_identifiers i ON i.ownerId = t.ownerId
             AND i.normalizedName = t.normalizedPayee
        WHERE t.ownerId = :ownerId AND t.isDeleted = 0 AND t.isExcluded = 0
          AND s.isDeleted = 0 AND s.status <> 'CANCELLED'
          AND t.type = 'DEBIT'
          AND IFNULL(t.payeeId, i.payeeId) IS NULL
        GROUP BY t.normalizedPayee

        ORDER BY totalPaise DESC, statementName COLLATE NOCASE ASC
        """
    )
    fun observeDirectory(ownerId: String): Flow<List<PayeeDirectoryRow>>

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

/** One day of the whole account, the two directions kept apart. */
data class DayTotalRow(
    val startMillis: Long,
    val debitPaise: Long,
    val creditPaise: Long,
    val transactionCount: Int
)

/** In, out and their counts over one range. Always one row, zeroed when the range is empty. */
data class TypeTotalsRow(
    val debitPaise: Long,
    val creditPaise: Long,
    val debitCount: Int,
    val creditCount: Int
)

/** Spend under one category; both columns are null for the unmapped bucket. */
data class CategoryTotalRow(
    val categoryId: Long?,
    val categoryName: String?,
    val totalPaise: Long,
    val transactionCount: Int
)

/**
 * One category's spend beside the whole account's, over the same range — the insight header.
 *
 * The two are read together so the share between them is taken from one moment; see
 * [TransactionDao.observeCategoryShare].
 */
data class CategoryShareRow(
    val categoryPaise: Long,
    val categoryCount: Int,
    /**
     * Distinct payees the category's spend reached *in this range*, merged identities counted once.
     *
     * Range-scoped rather than a count of `payees.categoryId` rows, and counted rather than derived
     * from the ranked list beside it: that list is fetched with a limit, so counting its rows would
     * report the limit on any category larger than it — the bug the Pulse hero shipped with in #31
     * and had to be fixed for. It also gives the unmapped bucket, which has no `payees` rows at all,
     * an answer of its own.
     */
    val categoryPayeeCount: Int,
    val accountPaise: Long
)

/**
 * Spend to one payee. [alias] is null while the name is unmapped, which is when [statementName] —
 * the name the bank printed — is what the row has to be called.
 */
data class PayeeTotalRow(
    val payeeId: Long?,
    val alias: String?,
    val statementName: String,
    /**
     * One of the payee's normalised names — the key `PayeeDetailRoute` opens on.
     *
     * Any one of them will do for a merged payee: the detail screen resolves siblings through
     * `SAME_PAYEE_NAMES`, so opening on `SWIGGY` and opening on `SWIGGY*ORDER` land on the same
     * history. It is taken with `MIN` alongside [statementName]'s own `MIN`, so in principle the two
     * could come from different rows of the group; that costs nothing, because the pair is only ever
     * used to open a screen that then re-resolves the whole payee for itself.
     */
    val normalizedName: String,
    val totalPaise: Long,
    val transactionCount: Int
)

/**
 * One row of the payee directory: a payee the user has named, or a statement name nobody has
 * claimed yet.
 *
 * [alias] and [categoryName] are null for the second kind, which is what the screen reads to draw
 * it as unnamed. Listing both kinds is deliberate — a directory of only the *named* payees would
 * present a partial list as a complete one, the same failure the Mapping health dashboard exists to
 * prevent, and it would disagree with `PayeeSummary.payeeCount` on what the account contains.
 */
data class PayeeDirectoryRow(
    val payeeId: Long?,
    val alias: String?,
    /** The name a statement printed, and what an unnamed row has to be called. */
    val statementName: String,
    /** The key `PayeeDetailRoute` opens on; see [PayeeTotalRow.normalizedName]. */
    val normalizedName: String,
    val categoryName: String?,
    /**
     * How many statement names this payee answers to.
     *
     * Zero for an unclaimed name: it is not that the payee has no names, but that no identifier row
     * has ever claimed it, which is exactly the work the Mapping health dashboard is pointing at.
     */
    val identifierCount: Int,
    val totalPaise: Long,
    val transactionCount: Int
)

/** How many payees a range's spend reached, and how much of it reached nobody in particular. */
data class PayeeSummaryRow(
    val payeeCount: Int,
    val unmappedPaise: Long,
    val unmappedPayeeCount: Int,
    val unmappedTransactionCount: Int
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

/**
 * Every row of the account that a dashboard is allowed to count, before any grouping.
 *
 * Three decisions are baked in here rather than repeated per query, so the four aggregates cannot
 * drift apart and quietly disagree with each other on one screen:
 *
 * - **`isExcluded = 0`, and never `isDuplicate`.** Exclusion is the user's decision and duplicate
 *   detection is only what seeded it; a repeat the user has re-included is a transaction they have
 *   said is real, and a dashboard that still left it out would contradict the list it was read from.
 * - **Cancelled statements are left out.** Cancelling is how a user throws an import away, but it
 *   only flips the session's status — the rows stay. Counting them would put spend the user has
 *   discarded into every total. Pending statements *are* counted: their rows are real, only the
 *   payee mapping is unfinished, and hiding a freshly imported statement until it is mapped would
 *   read as a failed import.
 * - **The range is half-open.** `>= from` and `< to`, so a row at midnight lands in exactly one of
 *   two adjacent months rather than in both.
 */
private const val COUNTABLE_ROWS_FROM =
    "FROM transactions t " +
        "JOIN sessions s ON s.id = t.sessionId AND s.ownerId = t.ownerId "

private const val COUNTABLE_ROWS_WHERE =
    "WHERE t.ownerId = :ownerId AND t.isDeleted = 0 AND t.isExcluded = 0 " +
        "AND s.isDeleted = 0 AND s.status <> 'CANCELLED' " +
        "AND t.dateTimeUtcMillis >= :fromMillis AND t.dateTimeUtcMillis < :toMillisExclusive "

/**
 * Resolves each row to the payee it belongs to *today*, which is not always the one stamped on it.
 *
 * `transactions.payeeId` is written by `assignPayee`, which is session-scoped: only the statement
 * being mapped is stamped, so rows imported before their payee existed keep a null `payeeId`
 * forever. Grouping on that column alone would drop them out of a total presented as complete.
 * Falling back to `payee_identifiers` — the same table the payee-scoped queries join through —
 * picks them back up, because a name is the identity a statement actually carries.
 *
 * The stamp wins where there is one. A merge re-points both the stamped rows and the identifiers
 * together, so the two paths agree; where they could not, the explicit assignment is the more
 * specific fact.
 */
private const val RESOLVED_PAYEE_JOIN =
    "LEFT JOIN payee_identifiers i ON i.ownerId = t.ownerId " +
        "AND i.normalizedName = t.normalizedPayee " +
        "LEFT JOIN payees p ON p.ownerId = t.ownerId AND p.id = IFNULL(t.payeeId, i.payeeId) "

/** The resolved payee, or NULL when the name is mapped to nobody. */
private const val RESOLVED_PAYEE_ID = "IFNULL(t.payeeId, i.payeeId)"

/**
 * What makes two rows the same payee, as a value that can be grouped or counted.
 *
 * Tagged rather than the id alone so unmapped rows fall back to their statement name instead of
 * collapsing into one NULL bucket the way the category aggregate deliberately does. The tag also
 * keeps the two kinds apart: a payee with id 7 and an unmapped name that happens to read "7" are
 * `payee:7` and `name:7`, not one payee counted once.
 *
 * The same expression backs both the ranked list and the count beside it, so the number in the hero
 * and the rows underneath it can never disagree about what one payee is.
 */
private const val PAYEE_GROUP_KEY =
    "CASE WHEN $RESOLVED_PAYEE_ID IS NULL " +
        "THEN 'name:' || t.normalizedPayee " +
        "ELSE 'payee:' || $RESOLVED_PAYEE_ID END"

/**
 * Narrows a resolved row to one category, where a null `:categoryId` means the unmapped bucket.
 *
 * Null is a real value to ask for here, not a missing argument. `payees.categoryId` is non-null, so
 * spend arrives without a category in exactly one way — its payee is not mapped yet — and that
 * bucket is what the donut draws as the hatch and what the Categories screen offers as
 * *Uncategorised*. Making it unopenable would leave the one slice the user most needs to act on as
 * the only one with nothing behind it.
 *
 * The two branches cannot both fire: `p.categoryId = NULL` is NULL rather than true in SQL, so an
 * asked-for null falls to the first branch and an asked-for id falls to the second.
 *
 * Matched on `payees.categoryId` rather than by joining `categories`, and deliberately without an
 * `isDeleted` filter, for the same reason [TransactionDao.observeCategoryTotals] does it: a category
 * the user has since deleted still names the spend that was mapped to it, and dropping the mapping
 * would move real spend into the unmapped bucket and overstate how much of the account is unmapped.
 */
private const val SAME_CATEGORY =
    "((:categoryId IS NULL AND p.categoryId IS NULL) OR p.categoryId = :categoryId) "

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
     * Day subtotals for the whole account — the trend chart's series.
     *
     * Bucketed by flooring the timestamp to a whole day in UTC, for the same reason
     * [observePayeeDayTotals] does: statement times are the printed wall clock stored as-if UTC, so
     * a UTC floor lands on the day the row displays under. A timezone conversion here would move
     * rows into the wrong day for every user not on UTC.
     *
     * Days with no rows are absent rather than zero — a gap in a period is not the same fact as a
     * day of no spending, and only the caller knows which one the chart should draw.
     */
    @Query(
        """
        SELECT (t.dateTimeUtcMillis / $DAY_MILLIS) * $DAY_MILLIS AS startMillis,
               IFNULL(SUM(CASE WHEN t.type = 'DEBIT' THEN t.amountPaise ELSE 0 END), 0)
                   AS debitPaise,
               IFNULL(SUM(CASE WHEN t.type = 'CREDIT' THEN t.amountPaise ELSE 0 END), 0)
                   AS creditPaise,
               COUNT(t.id) AS transactionCount
        $COUNTABLE_ROWS_FROM
        $COUNTABLE_ROWS_WHERE
        GROUP BY startMillis
        ORDER BY startMillis DESC
        """
    )
    fun observeDayTotals(
        ownerId: String,
        fromMillis: Long,
        toMillisExclusive: Long
    ): Flow<List<DayTotalRow>>

    /**
     * In, out and their counts over the range — the three KPI tiles, in one pass.
     *
     * Every sum is wrapped in `IFNULL`: an aggregate over no rows yields NULL, not 0, and a NULL
     * cannot bind to the non-null columns of [TypeTotalsRow]. An empty range is a normal state —
     * a new account, or a month with nothing in it — so it has to read as zeroes rather than crash.
     */
    @Query(
        """
        SELECT IFNULL(SUM(CASE WHEN t.type = 'DEBIT' THEN t.amountPaise ELSE 0 END), 0)
                   AS debitPaise,
               IFNULL(SUM(CASE WHEN t.type = 'CREDIT' THEN t.amountPaise ELSE 0 END), 0)
                   AS creditPaise,
               IFNULL(SUM(CASE WHEN t.type = 'DEBIT' THEN 1 ELSE 0 END), 0) AS debitCount,
               IFNULL(SUM(CASE WHEN t.type = 'CREDIT' THEN 1 ELSE 0 END), 0) AS creditCount
        $COUNTABLE_ROWS_FROM
        $COUNTABLE_ROWS_WHERE
        """
    )
    fun observeTypeTotals(
        ownerId: String,
        fromMillis: Long,
        toMillisExclusive: Long
    ): Flow<TypeTotalsRow>

    /**
     * Spend per category, largest first, resolved through the payee mapping — see
     * [RESOLVED_PAYEE_JOIN] for why the stamped `payeeId` is not enough on its own.
     *
     * Debits only. This answers "where did it go", and a salary landing is not a category of spend.
     *
     * Rows whose payee resolves to nobody group together under a null id — SQLite gathers NULLs
     * into one group — which is the bucket the donut draws as the hatch. Neither the payee nor the
     * category is filtered on `isDeleted`: a category the user has since deleted still names the
     * spend that was mapped to it, and dropping that mapping would move real spend into the
     * unmapped bucket and overstate how much of the account is unmapped.
     */
    @Query(
        """
        SELECT c.id AS categoryId,
               c.name AS categoryName,
               IFNULL(SUM(t.amountPaise), 0) AS totalPaise,
               COUNT(t.id) AS transactionCount
        $COUNTABLE_ROWS_FROM
        $RESOLVED_PAYEE_JOIN
        LEFT JOIN categories c ON c.ownerId = t.ownerId AND c.id = p.categoryId
        $COUNTABLE_ROWS_WHERE
        AND t.type = 'DEBIT'
        GROUP BY c.id
        ORDER BY totalPaise DESC, categoryName
        """
    )
    fun observeCategoryTotals(
        ownerId: String,
        fromMillis: Long,
        toMillisExclusive: Long
    ): Flow<List<CategoryTotalRow>>

    /**
     * The [limit] largest payees by spend, **grouped by the merged payee rather than by the
     * statement name**.
     *
     * That is the whole point of this query. `SWIGGY`, `SWIGGY BANGALORE` and `SWIGGY*ORDER` mapped
     * onto one payee must total as one row: grouped by name they would be three under-counted rows,
     * and the user's actual largest payee could be pushed out of the top five by its own spelling
     * variants. Names that resolve to nobody keep their own row — unmapped money still ranks, under
     * the name the statement printed.
     *
     * The group key is a tagged string rather than the id alone, so unmapped rows fall back to
     * grouping by name instead of collapsing into a single NULL bucket the way the category
     * aggregate deliberately does.
     */
    @Query(
        """
        SELECT $RESOLVED_PAYEE_ID AS payeeId,
               MIN(p.alias) AS alias,
               MIN(t.rawPayee) AS statementName,
               MIN(t.normalizedPayee) AS normalizedName,
               IFNULL(SUM(t.amountPaise), 0) AS totalPaise,
               COUNT(t.id) AS transactionCount
        $COUNTABLE_ROWS_FROM
        $RESOLVED_PAYEE_JOIN
        $COUNTABLE_ROWS_WHERE
        AND t.type = 'DEBIT'
        GROUP BY $PAYEE_GROUP_KEY
        ORDER BY totalPaise DESC, statementName
        LIMIT :limit
        """
    )
    fun observeTopPayees(
        ownerId: String,
        fromMillis: Long,
        toMillisExclusive: Long,
        limit: Int
    ): Flow<List<PayeeTotalRow>>

    /**
     * How many payees the period's spend reached, and how much of it reached nobody.
     *
     * Counted rather than derived from [observeTopPayees], which is fetched with a limit: counting
     * its rows would report the limit on any account larger than it, so "across 40 payees" would be
     * what a busy account and a quiet one both said.
     *
     * The unmapped value is already available as the null bucket of [observeCategoryTotals] — every
     * mapped payee carries a category, so no category means no payee. The count of distinct names is
     * not, and it is what turns a percentage into a task: "₹5,770 unnamed" is a statistic, "₹5,770
     * across three payees" is an afternoon's work the user can picture finishing.
     *
     * Conditional aggregation rather than a `WHERE` on the unmapped rows: filtering the whole query
     * down to them would leave no rows to count the account's payees from, and a fully mapped
     * account — the state the nudge exists to reach — would come back as no row at all.
     *
     * The unmapped names are counted on `normalizedPayee` rather than `rawPayee` so two printings of
     * the same merchant that differ only in spacing are one name to be mapped, not two.
     */
    @Query(
        """
        SELECT COUNT(DISTINCT $PAYEE_GROUP_KEY) AS payeeCount,
               IFNULL(SUM(CASE WHEN $RESOLVED_PAYEE_ID IS NULL
                               THEN t.amountPaise ELSE 0 END), 0) AS unmappedPaise,
               COUNT(DISTINCT CASE WHEN $RESOLVED_PAYEE_ID IS NULL
                                   THEN t.normalizedPayee END) AS unmappedPayeeCount,
               COUNT(CASE WHEN $RESOLVED_PAYEE_ID IS NULL
                          THEN t.id END) AS unmappedTransactionCount
        $COUNTABLE_ROWS_FROM
        $RESOLVED_PAYEE_JOIN
        $COUNTABLE_ROWS_WHERE
        AND t.type = 'DEBIT'
        """
    )
    fun observePayeeSummary(
        ownerId: String,
        fromMillis: Long,
        toMillisExclusive: Long
    ): Flow<PayeeSummaryRow>

    /**
     * Month-bucketed spend under one category — the insight screen's trend card.
     *
     * Months need calendar arithmetic rather than a divisor, hence `start of month`, the same way
     * [observePayeeMonthTotals] does it. The bucket is computed in UTC because that is the clock the
     * rows are stored in; see [observeDayTotals].
     *
     * Deliberately *not* bounded by the dashboard's active filter in practice: the caller passes a
     * months-back window instead, because "how is this going over time" is a longer question than
     * any single period answers. The parameters are still a plain half-open range, so the query has
     * no opinion about which window it is given.
     *
     * Debits only, matching every other category aggregate: a salary landing is not spend under a
     * category.
     */
    @Query(
        """
        SELECT CAST(strftime('%s', t.dateTimeUtcMillis / 1000, 'unixepoch', 'start of month')
                    AS INTEGER) * 1000 AS startMillis,
               IFNULL(SUM(t.amountPaise), 0) AS countedTotalPaise,
               COUNT(t.id) AS countedCount
        $COUNTABLE_ROWS_FROM
        $RESOLVED_PAYEE_JOIN
        $COUNTABLE_ROWS_WHERE
        AND t.type = 'DEBIT' AND $SAME_CATEGORY
        GROUP BY startMillis
        ORDER BY startMillis DESC
        """
    )
    fun observeCategoryMonthTotals(
        ownerId: String,
        categoryId: Long?,
        fromMillis: Long,
        toMillisExclusive: Long
    ): Flow<List<PeriodTotalRow>>

    /**
     * Day-bucketed spend under one category — the insight screen's spend-by-day card, and the same
     * rows its by-weekday card re-buckets rather than re-queries.
     *
     * Days with no rows are absent rather than zero, for the reason [observeDayTotals] gives: a gap
     * is not the same fact as a day of no spending, and only the caller knows which the chart draws.
     */
    @Query(
        """
        SELECT (t.dateTimeUtcMillis / $DAY_MILLIS) * $DAY_MILLIS AS startMillis,
               IFNULL(SUM(t.amountPaise), 0) AS countedTotalPaise,
               COUNT(t.id) AS countedCount
        $COUNTABLE_ROWS_FROM
        $RESOLVED_PAYEE_JOIN
        $COUNTABLE_ROWS_WHERE
        AND t.type = 'DEBIT' AND $SAME_CATEGORY
        GROUP BY startMillis
        ORDER BY startMillis DESC
        """
    )
    fun observeCategoryDayTotals(
        ownerId: String,
        categoryId: Long?,
        fromMillis: Long,
        toMillisExclusive: Long
    ): Flow<List<PeriodTotalRow>>

    /**
     * The [limit] largest payees *within one category* — the same aggregate [observeTopPayees]
     * computes for the whole account, with a category added to the `WHERE`.
     *
     * One query, two call sites, and the same merged-payee grouping: the identifiers of a merged
     * payee total as one row here too, which is the failure this app was built to stop repeating.
     *
     * Asked for the unmapped bucket, this is the breakdown that stops "₹5,770 has no name on it"
     * being a dead end — the rows come back grouped by statement name, which is the only identity
     * they have.
     */
    @Query(
        """
        SELECT $RESOLVED_PAYEE_ID AS payeeId,
               MIN(p.alias) AS alias,
               MIN(t.rawPayee) AS statementName,
               MIN(t.normalizedPayee) AS normalizedName,
               IFNULL(SUM(t.amountPaise), 0) AS totalPaise,
               COUNT(t.id) AS transactionCount
        $COUNTABLE_ROWS_FROM
        $RESOLVED_PAYEE_JOIN
        $COUNTABLE_ROWS_WHERE
        AND t.type = 'DEBIT' AND $SAME_CATEGORY
        GROUP BY $PAYEE_GROUP_KEY
        ORDER BY totalPaise DESC, statementName
        LIMIT :limit
        """
    )
    fun observeCategoryTopPayees(
        ownerId: String,
        categoryId: Long?,
        fromMillis: Long,
        toMillisExclusive: Long,
        limit: Int
    ): Flow<List<PayeeTotalRow>>

    /**
     * One category's spend beside the account's, over the same range, in a single row.
     *
     * Both figures come from one pass on purpose. The insight header states a share — "32% of all
     * spend" — and a share computed from two independently collected flows can be assembled from
     * two different moments, so the header would occasionally contradict the dashboard the user
     * just came from. One row cannot disagree with itself.
     *
     * Conditional aggregation rather than two queries also means the empty case behaves: a range
     * with nothing in it yields one row of zeroes rather than no row at all.
     */
    @Query(
        """
        SELECT IFNULL(SUM(CASE WHEN $SAME_CATEGORY THEN t.amountPaise ELSE 0 END), 0)
                   AS categoryPaise,
               IFNULL(SUM(CASE WHEN $SAME_CATEGORY THEN 1 ELSE 0 END), 0)
                   AS categoryCount,
               COUNT(DISTINCT CASE WHEN $SAME_CATEGORY
                                   THEN $PAYEE_GROUP_KEY END) AS categoryPayeeCount,
               IFNULL(SUM(t.amountPaise), 0) AS accountPaise
        $COUNTABLE_ROWS_FROM
        $RESOLVED_PAYEE_JOIN
        $COUNTABLE_ROWS_WHERE
        AND t.type = 'DEBIT'
        """
    )
    fun observeCategoryShare(
        ownerId: String,
        categoryId: Long?,
        fromMillis: Long,
        toMillisExclusive: Long
    ): Flow<CategoryShareRow>

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
