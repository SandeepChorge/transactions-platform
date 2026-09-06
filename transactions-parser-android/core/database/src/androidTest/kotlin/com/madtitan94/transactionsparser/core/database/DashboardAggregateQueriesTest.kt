package com.madtitan94.transactionsparser.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.madtitan94.transactionsparser.core.database.entity.CategoryEntity
import com.madtitan94.transactionsparser.core.database.entity.PayeeEntity
import com.madtitan94.transactionsparser.core.database.entity.PayeeIdentifierEntity
import com.madtitan94.transactionsparser.core.database.entity.SessionEntity
import com.madtitan94.transactionsparser.core.database.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * The dashboard aggregates are raw SQL over the whole account, and every figure a chart shows comes
 * out of them. Nothing above the DAO can catch a mistake here: a payee grouped by the wrong key or
 * a range boundary off by a millisecond produces a plausible-looking number, not an error.
 *
 * The case these tests exist for above all others is the merged payee. Three statement spellings
 * mapped onto one payee must total as one row; grouped by name they would be three under-counted
 * rows, and the account's real largest payee could be pushed out of the top five by its own
 * spelling variants.
 */
@RunWith(AndroidJUnit4::class)
class DashboardAggregateQueriesTest {

    private lateinit var database: TransactionsDatabase

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            TransactionsDatabase::class.java
        ).build()
        listOf(
            COMPLETED_SESSION to "COMPLETED",
            PENDING_SESSION to "PENDING",
            CANCELLED_SESSION to "CANCELLED"
        ).forEach { (id, status) ->
            database.sessionDao().insert(
                SessionEntity(
                    id = id,
                    ownerId = OWNER,
                    fileName = "statement-$id.pdf",
                    source = "PHONEPE",
                    uploadedAtMillis = 0L,
                    periodStartMillis = null,
                    periodEndMillis = null,
                    status = status
                )
            )
        }
        // The second account needs a session of its own for its rows to hang off.
        database.sessionDao().insert(
            SessionEntity(
                id = OTHER_ACCOUNT_SESSION,
                ownerId = OTHER_OWNER,
                fileName = "theirs.pdf",
                source = "PHONEPE",
                uploadedAtMillis = 0L,
                periodStartMillis = null,
                periodEndMillis = null,
                status = "COMPLETED"
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** Statement times are wall-clock read back as UTC, so tests build them the same way. */
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private suspend fun insert(
        millis: Long,
        amountPaise: Long,
        payee: String = PAYEE,
        type: String = "DEBIT",
        payeeId: Long? = null,
        sessionId: Long = COMPLETED_SESSION,
        isDuplicate: Boolean = false,
        isExcluded: Boolean = false,
        ownerId: String = OWNER
    ) {
        database.transactionDao().insertAll(
            listOf(
                TransactionEntity(
                    ownerId = ownerId,
                    sessionId = sessionId,
                    dateTimeUtcMillis = millis,
                    rawPayee = payee,
                    normalizedPayee = payee,
                    amountPaise = amountPaise,
                    type = type,
                    transactionRef = null,
                    utr = null,
                    payeeId = payeeId,
                    isDuplicate = isDuplicate,
                    isExcluded = isExcluded
                )
            )
        )
    }

    private suspend fun dayTotals(from: Long = ALL_FROM, to: Long = ALL_TO) =
        database.transactionDao().observeDayTotals(OWNER, from, to).first()

    private suspend fun typeTotals(from: Long = ALL_FROM, to: Long = ALL_TO) =
        database.transactionDao().observeTypeTotals(OWNER, from, to).first()

    private suspend fun categoryTotals(from: Long = ALL_FROM, to: Long = ALL_TO) =
        database.transactionDao().observeCategoryTotals(OWNER, from, to).first()

    private suspend fun topPayees(limit: Int = 10, from: Long = ALL_FROM, to: Long = ALL_TO) =
        database.transactionDao().observeTopPayees(OWNER, from, to, limit).first()

    // ---- Day totals -------------------------------------------------------------------------

    @Test
    fun dayTotalsBucketByUtcDayAndKeepTheTwoDirectionsApart() = runTest {
        insert(at(2026, 6, 10, hour = 0, minute = 0), 1_000)
        insert(at(2026, 6, 10, hour = 23, minute = 59), 2_000)
        insert(at(2026, 6, 10, hour = 9), 50_000, type = "CREDIT")
        insert(at(2026, 6, 11), 4_000)

        val days = dayTotals()

        // Newest first, one bucket per day, and the salary is not a day of negative spending.
        assertThat(days).hasSize(2)
        assertThat(days[0].startMillis).isEqualTo(at(2026, 6, 11, hour = 0))
        assertThat(days[0].debitPaise).isEqualTo(4_000L)
        assertThat(days[1].startMillis).isEqualTo(at(2026, 6, 10, hour = 0))
        assertThat(days[1].debitPaise).isEqualTo(3_000L)
        assertThat(days[1].creditPaise).isEqualTo(50_000L)
        assertThat(days[1].transactionCount).isEqualTo(3)
    }

    /** A gap in the data is not the same fact as a day of no spending — only the caller knows. */
    @Test
    fun daysWithNoRowsAreAbsentRatherThanZero() = runTest {
        insert(at(2026, 6, 10), 1_000)
        insert(at(2026, 6, 13), 1_000)

        assertThat(dayTotals()).hasSize(2)
    }

    // ---- Type totals ------------------------------------------------------------------------

    @Test
    fun typeTotalsSplitInFromOutAndDeriveTheNet() = runTest {
        insert(at(2026, 6, 10), 1_000)
        insert(at(2026, 6, 11), 2_500)
        insert(at(2026, 6, 12), 10_000, type = "CREDIT")

        val totals = typeTotals().toTypeTotals()

        assertThat(totals.debitPaise).isEqualTo(3_500L)
        assertThat(totals.creditPaise).isEqualTo(10_000L)
        assertThat(totals.debitCount).isEqualTo(2)
        assertThat(totals.creditCount).isEqualTo(1)
        assertThat(totals.netPaise).isEqualTo(6_500L)
    }

    /**
     * An aggregate over no rows yields NULL, not 0, and a NULL cannot bind to the row's non-null
     * columns. An empty range is an ordinary state — a new account, or a quiet month — so it has to
     * read as zeroes rather than crash the dashboard that asked for it.
     */
    @Test
    fun anEmptyRangeReadsAsZeroesRatherThanFailingToBind() = runTest {
        insert(at(2026, 6, 10), 1_000)

        val totals = typeTotals(from = at(2026, 7, 1), to = at(2026, 8, 1)).toTypeTotals()

        assertThat(totals.debitPaise).isEqualTo(0L)
        assertThat(totals.creditPaise).isEqualTo(0L)
        assertThat(totals.debitCount).isEqualTo(0)
        assertThat(totals.netPaise).isEqualTo(0L)
    }

    // ---- What counts ------------------------------------------------------------------------

    /**
     * Exclusion is the user's decision; duplicate detection is only what seeded it. A repeat the
     * user has re-included is a transaction they have said is real, so it counts.
     */
    @Test
    fun excludedRowsAreLeftOutWhileAnIncludedDuplicateStillCounts() = runTest {
        insert(at(2026, 6, 10), 1_000)
        insert(at(2026, 6, 10), 2_000, isDuplicate = true, isExcluded = false)
        insert(at(2026, 6, 10), 9_000, isDuplicate = true, isExcluded = true)

        val totals = typeTotals().toTypeTotals()

        assertThat(totals.debitPaise).isEqualTo(3_000L)
        assertThat(totals.debitCount).isEqualTo(2)
        assertThat(dayTotals().single().transactionCount).isEqualTo(2)
    }

    /**
     * Cancelling is how a user throws an import away — but it only flips the session's status and
     * leaves the rows behind. A pending statement's rows are real; only the mapping is unfinished.
     */
    @Test
    fun cancelledStatementsAreLeftOutWhilePendingOnesCount() = runTest {
        insert(at(2026, 6, 10), 1_000, sessionId = COMPLETED_SESSION)
        insert(at(2026, 6, 10), 2_000, sessionId = PENDING_SESSION)
        insert(at(2026, 6, 10), 90_000, sessionId = CANCELLED_SESSION)

        assertThat(typeTotals().toTypeTotals().debitPaise).isEqualTo(3_000L)
        assertThat(topPayees().single().totalPaise).isEqualTo(3_000L)
    }

    /** `>= from` and `< to`, so a midnight row lands in exactly one of two adjacent ranges. */
    @Test
    fun theRangeIsHalfOpenAtTheTop() = runTest {
        insert(at(2026, 6, 1, hour = 0), 1_000)
        insert(at(2026, 6, 30, hour = 23, minute = 59), 2_000)
        insert(at(2026, 7, 1, hour = 0), 4_000)

        val june = typeTotals(from = at(2026, 6, 1, hour = 0), to = at(2026, 7, 1, hour = 0))

        assertThat(june.toTypeTotals().debitPaise).isEqualTo(3_000L)
        assertThat(june.toTypeTotals().debitCount).isEqualTo(2)
    }

    @Test
    fun anotherAccountsRowsNeverCount() = runTest {
        insert(at(2026, 6, 10), 1_000)
        insert(
            at(2026, 6, 10),
            99_000,
            ownerId = OTHER_OWNER,
            sessionId = OTHER_ACCOUNT_SESSION
        )

        assertThat(typeTotals().toTypeTotals().debitPaise).isEqualTo(1_000L)
        assertThat(topPayees()).hasSize(1)
        assertThat(dayTotals().single().debitPaise).isEqualTo(1_000L)
    }

    // ---- Category totals --------------------------------------------------------------------

    @Test
    fun categoryTotalsResolveThroughThePayeeMappingAndRankLargestFirst() = runTest {
        val groceries = category("Groceries")
        val transport = category("Transport")
        val shop = payee("Corner shop", groceries, PAYEE)
        payee("Cab", transport, OTHER_PAYEE)
        insert(at(2026, 6, 10), 1_000, payee = PAYEE)
        insert(at(2026, 6, 11), 2_000, payee = PAYEE, payeeId = shop)
        insert(at(2026, 6, 12), 9_000, payee = OTHER_PAYEE)

        val totals = categoryTotals().map { it.toCategoryTotal() }

        assertThat(totals.map { it.categoryName }).containsExactly("Transport", "Groceries")
        assertThat(totals[0].totalPaise).isEqualTo(9_000L)
        assertThat(totals[1].totalPaise).isEqualTo(3_000L)
        assertThat(totals[1].transactionCount).isEqualTo(2)
    }

    /**
     * `payees.categoryId` is non-null, so an unmapped payee is the only way spend can arrive with
     * no category. That bucket is what the donut draws as the hatch and what Mapping health reports
     * as the unmapped share — dropping it would present a partial total as a complete one.
     */
    @Test
    fun unmappedSpendGathersIntoTheNullCategoryBucket() = runTest {
        val groceries = category("Groceries")
        payee("Corner shop", groceries, PAYEE)
        insert(at(2026, 6, 10), 1_000, payee = PAYEE)
        insert(at(2026, 6, 11), 5_000, payee = "UNMAPPED ONE")
        insert(at(2026, 6, 12), 3_000, payee = "UNMAPPED TWO")

        val totals = categoryTotals().map { it.toCategoryTotal() }
        val unmapped = totals.single { it.categoryId == null }

        assertThat(unmapped.categoryName).isNull()
        assertThat(unmapped.totalPaise).isEqualTo(8_000L)
        assertThat(unmapped.transactionCount).isEqualTo(2)
    }

    /** "Where did it go" — a salary landing is not a category of spend. */
    @Test
    fun categoryTotalsCountDebitsOnly() = runTest {
        val groceries = category("Groceries")
        payee("Corner shop", groceries, PAYEE)
        insert(at(2026, 6, 10), 1_000, payee = PAYEE)
        insert(at(2026, 6, 11), 50_000, payee = PAYEE, type = "CREDIT")

        assertThat(categoryTotals().single().totalPaise).isEqualTo(1_000L)
    }

    @Test
    fun aCategoryWithNoSpendInTheRangeIsAbsentRatherThanZero() = runTest {
        category("Groceries")

        assertThat(categoryTotals()).isEmpty()
    }

    // ---- Top payees -------------------------------------------------------------------------

    /**
     * The reason this slice exists. Three spellings, one payee, one row — grouped by name they
     * would be three rows of 1_000, 2_000 and 3_000 and the ranking would be wrong.
     */
    @Test
    fun aMergedPayeeTotalsAsOneRowUnderItsAlias() = runTest {
        val groceries = category("Groceries")
        payee("Corner shop", groceries, PAYEE, OTHER_SPELLING, THIRD_SPELLING)
        insert(at(2026, 6, 10), 1_000, payee = PAYEE)
        insert(at(2026, 6, 11), 2_000, payee = OTHER_SPELLING)
        insert(at(2026, 6, 12), 3_000, payee = THIRD_SPELLING)

        val ranked = topPayees().map { it.toPayeeTotal() }

        assertThat(ranked).hasSize(1)
        assertThat(ranked.single().label).isEqualTo("Corner shop")
        assertThat(ranked.single().totalPaise).isEqualTo(6_000L)
        assertThat(ranked.single().transactionCount).isEqualTo(3)
    }

    /**
     * `assignPayee` is session-scoped, so a row imported before its payee existed keeps a null
     * `payeeId` forever. Falling back to `payee_identifiers` is what keeps it in its payee's total
     * instead of stranding it in a row of its own.
     */
    @Test
    fun aRowWithNoStampedPayeeIdStillResolvesThroughItsIdentifier() = runTest {
        val groceries = category("Groceries")
        val shop = payee("Corner shop", groceries, PAYEE, OTHER_SPELLING)
        insert(at(2026, 6, 10), 1_000, payee = PAYEE, payeeId = null)
        insert(at(2026, 6, 11), 2_000, payee = OTHER_SPELLING, payeeId = shop)

        val ranked = topPayees().map { it.toPayeeTotal() }

        assertThat(ranked).hasSize(1)
        assertThat(ranked.single().payeeId).isEqualTo(shop)
        assertThat(ranked.single().totalPaise).isEqualTo(3_000L)
    }

    /** Unmapped money still ranks, under the name the statement printed, and never collapses. */
    @Test
    fun unmappedNamesKeepSeparateRowsLabelledByTheStatement() = runTest {
        insert(at(2026, 6, 10), 5_000, payee = "UNMAPPED ONE")
        insert(at(2026, 6, 11), 1_000, payee = "UNMAPPED ONE")
        insert(at(2026, 6, 12), 3_000, payee = "UNMAPPED TWO")

        val ranked = topPayees().map { it.toPayeeTotal() }

        assertThat(ranked.map { it.label }).containsExactly("UNMAPPED ONE", "UNMAPPED TWO")
        assertThat(ranked.map { it.payeeId }).containsExactly(null, null)
        assertThat(ranked[0].totalPaise).isEqualTo(6_000L)
    }

    @Test
    fun topPayeesRankByTotalAndHonourTheLimit() = runTest {
        insert(at(2026, 6, 10), 1_000, payee = "SMALL")
        insert(at(2026, 6, 11), 5_000, payee = "LARGE")
        insert(at(2026, 6, 12), 3_000, payee = "MIDDLE")

        assertThat(topPayees(limit = 2).map { it.toPayeeTotal().label })
            .containsExactly("LARGE", "MIDDLE")
    }

    @Test
    fun topPayeesCountDebitsOnly() = runTest {
        insert(at(2026, 6, 10), 1_000, payee = PAYEE)
        insert(at(2026, 6, 11), 90_000, payee = PAYEE, type = "CREDIT")

        assertThat(topPayees().single().totalPaise).isEqualTo(1_000L)
    }

    private suspend fun category(name: String): Long =
        database.categoryDao().insert(CategoryEntity(ownerId = OWNER, name = name))

    /** A payee owning [names] — one name for an ordinary mapping, several for a merged one. */
    private suspend fun payee(alias: String, categoryId: Long, vararg names: String): Long {
        val payeeId = database.payeeDao().insert(
            PayeeEntity(ownerId = OWNER, alias = alias, categoryId = categoryId)
        )
        names.forEach { name ->
            database.payeeIdentifierDao().insert(
                PayeeIdentifierEntity(
                    ownerId = OWNER,
                    payeeId = payeeId,
                    rawName = name,
                    normalizedName = name
                )
            )
        }
        return payeeId
    }

    private companion object {
        const val OWNER = "google-sub-a"
        const val OTHER_OWNER = "google-sub-b"
        const val COMPLETED_SESSION = 1L
        const val PENDING_SESSION = 2L
        const val CANCELLED_SESSION = 3L
        const val OTHER_ACCOUNT_SESSION = 4L
        const val PAYEE = "ABC SHOP"
        const val OTHER_SPELLING = "ABC SHOP PVT LTD"
        const val THIRD_SPELLING = "ABC*ORDER"
        const val OTHER_PAYEE = "CITY CABS"
        const val ALL_FROM = Long.MIN_VALUE
        const val ALL_TO = Long.MAX_VALUE
    }
}
