package com.madtitan94.transactionsparser.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.madtitan94.transactionsparser.core.database.entity.CategoryEntity
import com.madtitan94.transactionsparser.core.database.entity.PayeeEntity
import com.madtitan94.transactionsparser.core.database.entity.PayeeIdentifierEntity
import com.madtitan94.transactionsparser.core.database.entity.SessionEntity
import com.madtitan94.transactionsparser.core.database.entity.TransactionEntity
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The four category-scoped queries behind the insight screen.
 *
 * They are the account aggregates with a category added to the `WHERE`, and that one clause is
 * where the mistakes live. A category filter that quietly drops the unmapped bucket, or one that
 * ranks a merged payee's spellings as separate rows, produces a plausible screen rather than an
 * error — which is why these run against real Room rather than a fake.
 */
@RunWith(AndroidJUnit4::class)
class CategoryInsightQueriesTest {

    private lateinit var database: TransactionsDatabase

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            TransactionsDatabase::class.java
        ).build()
        listOf(COMPLETED_SESSION to "COMPLETED", CANCELLED_SESSION to "CANCELLED").forEach {
            (id, status) ->
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
    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        LocalDateTime.of(year, month, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private suspend fun insert(
        millis: Long,
        amountPaise: Long,
        payee: String,
        type: String = "DEBIT",
        payeeId: Long? = null,
        sessionId: Long = COMPLETED_SESSION,
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
                    isDuplicate = false,
                    isExcluded = isExcluded
                )
            )
        )
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

    private suspend fun dayTotals(categoryId: Long?, from: Long = ALL_FROM, to: Long = ALL_TO) =
        database.transactionDao().observeCategoryDayTotals(OWNER, categoryId, from, to).first()

    private suspend fun monthTotals(categoryId: Long?, from: Long = ALL_FROM, to: Long = ALL_TO) =
        database.transactionDao().observeCategoryMonthTotals(OWNER, categoryId, from, to).first()

    private suspend fun topPayees(
        categoryId: Long?,
        limit: Int = 10,
        from: Long = ALL_FROM,
        to: Long = ALL_TO
    ) = database.transactionDao()
        .observeCategoryTopPayees(OWNER, categoryId, from, to, limit)
        .first()

    private suspend fun share(categoryId: Long?, from: Long = ALL_FROM, to: Long = ALL_TO) =
        database.transactionDao().observeCategoryShare(OWNER, categoryId, from, to).first()
            .toCategoryShare()

    // ---- Category scoping -------------------------------------------------------------------

    @Test
    fun dayTotalsCountOnlyTheAskedForCategory() = runTest {
        val food = category("Food")
        val travel = category("Travel")
        val zomato = payee("Zomato", food, "ZOMATO")
        val cabs = payee("City Cabs", travel, "CITY CABS")

        insert(at(2026, 6, 10), 1_000, "ZOMATO", payeeId = zomato)
        insert(at(2026, 6, 10), 5_000, "CITY CABS", payeeId = cabs)
        insert(at(2026, 6, 11), 2_000, "ZOMATO", payeeId = zomato)

        val days = dayTotals(food)

        assertThat(days).hasSize(2)
        assertThat(days[0].countedTotalPaise).isEqualTo(2_000L)
        assertThat(days[1].countedTotalPaise).isEqualTo(1_000L)
    }

    /**
     * A null id asks for the unmapped bucket, and it has to be a real answer rather than an empty
     * one — it is the slice the user most needs to open.
     */
    @Test
    fun aNullCategoryIsTheUnmappedBucketRatherThanNoFilter() = runTest {
        val food = category("Food")
        val zomato = payee("Zomato", food, "ZOMATO")

        insert(at(2026, 6, 10), 1_000, "ZOMATO", payeeId = zomato)
        insert(at(2026, 6, 10), 3_000, "SOME NEW SHOP")

        val unmapped = dayTotals(null)

        assertThat(unmapped).hasSize(1)
        assertThat(unmapped[0].countedTotalPaise).isEqualTo(3_000L)
    }

    /**
     * A row imported before its payee existed keeps a null `payeeId` forever, because `assignPayee`
     * is session-scoped. Resolving through `payee_identifiers` is what keeps it inside its own
     * category instead of dropping it into the unmapped bucket.
     */
    @Test
    fun aRowWithNoStampedPayeeStillCountsUnderItsCategory() = runTest {
        val food = category("Food")
        payee("Zomato", food, "ZOMATO")

        insert(at(2026, 6, 10), 4_000, "ZOMATO", payeeId = null)

        assertThat(dayTotals(food)).hasSize(1)
        assertThat(dayTotals(food)[0].countedTotalPaise).isEqualTo(4_000L)
        assertThat(dayTotals(null)).isEmpty()
    }

    @Test
    fun creditsAreNotCategorySpend() = runTest {
        val food = category("Food")
        val zomato = payee("Zomato", food, "ZOMATO")

        insert(at(2026, 6, 10), 1_000, "ZOMATO", payeeId = zomato)
        insert(at(2026, 6, 11), 90_000, "ZOMATO", payeeId = zomato, type = "CREDIT")

        // A refund landing back from a merchant is not a month of extra spending under its
        // category, and netting it off would understate what was actually spent.
        assertThat(share(food).totalPaise).isEqualTo(1_000L)
    }

    @Test
    fun excludedRowsAndCancelledStatementsAreLeftOut() = runTest {
        val food = category("Food")
        val zomato = payee("Zomato", food, "ZOMATO")

        insert(at(2026, 6, 10), 1_000, "ZOMATO", payeeId = zomato)
        insert(at(2026, 6, 11), 7_000, "ZOMATO", payeeId = zomato, isExcluded = true)
        insert(
            at(2026, 6, 12),
            9_000,
            "ZOMATO",
            payeeId = zomato,
            sessionId = CANCELLED_SESSION
        )

        assertThat(share(food).totalPaise).isEqualTo(1_000L)
    }

    @Test
    fun anotherAccountsSpendIsInvisible() = runTest {
        val food = category("Food")
        val zomato = payee("Zomato", food, "ZOMATO")

        insert(at(2026, 6, 10), 1_000, "ZOMATO", payeeId = zomato)
        insert(
            at(2026, 6, 10),
            50_000,
            "ZOMATO",
            sessionId = OTHER_ACCOUNT_SESSION,
            ownerId = OTHER_OWNER
        )

        assertThat(share(food).totalPaise).isEqualTo(1_000L)
        assertThat(share(food).accountTotalPaise).isEqualTo(1_000L)
    }

    // ---- Ranked payees ----------------------------------------------------------------------

    /** The case this app exists to get right, asked again inside a category. */
    @Test
    fun aMergedPayeeTotalsAsOneRowInsideItsCategory() = runTest {
        val food = category("Food")
        val zomato = payee("Zomato", food, "ZOMATO", "ZOMATO BANGALORE", "ZOMATO*ORDER")

        insert(at(2026, 6, 10), 1_000, "ZOMATO", payeeId = zomato)
        insert(at(2026, 6, 11), 2_000, "ZOMATO BANGALORE", payeeId = zomato)
        insert(at(2026, 6, 12), 3_000, "ZOMATO*ORDER", payeeId = zomato)

        val rows = topPayees(food)

        assertThat(rows).hasSize(1)
        assertThat(rows[0].totalPaise).isEqualTo(6_000L)
        assertThat(rows[0].transactionCount).isEqualTo(3)
        assertThat(rows[0].alias).isEqualTo("Zomato")
    }

    /** Unmapped names keep their own rows, under the name the statement printed. */
    @Test
    fun theUnmappedBucketRanksByStatementName() = runTest {
        insert(at(2026, 6, 10), 5_000, "SHOP A")
        insert(at(2026, 6, 11), 1_000, "SHOP B")
        insert(at(2026, 6, 12), 2_000, "SHOP A")

        val rows = topPayees(null)

        // Two rows, largest first, so "₹5,770 has no name on it" is not a dead end.
        assertThat(rows).hasSize(2)
        assertThat(rows[0].statementName).isEqualTo("SHOP A")
        assertThat(rows[0].totalPaise).isEqualTo(7_000L)
        assertThat(rows[0].payeeId).isNull()
        assertThat(rows[1].statementName).isEqualTo("SHOP B")
    }

    // ---- Share ------------------------------------------------------------------------------

    @Test
    fun theShareRowCarriesTheCategoryAndTheAccountTogether() = runTest {
        val food = category("Food")
        val travel = category("Travel")
        val zomato = payee("Zomato", food, "ZOMATO")
        val cabs = payee("City Cabs", travel, "CITY CABS")

        insert(at(2026, 6, 10), 2_500, "ZOMATO", payeeId = zomato)
        insert(at(2026, 6, 11), 7_500, "CITY CABS", payeeId = cabs)

        val share = share(food)

        // Both figures out of one pass, so the header's "25% of all spend" cannot be assembled from
        // two different moments and disagree with the dashboard the user just came from.
        assertThat(share.totalPaise).isEqualTo(2_500L)
        assertThat(share.accountTotalPaise).isEqualTo(10_000L)
        assertThat(share.sharePercent).isEqualTo(25)
    }

    @Test
    fun theShareCountsMergedPayeesOnceAndUnmappedNamesSeparately() = runTest {
        val food = category("Food")
        val zomato = payee("Zomato", food, "ZOMATO", "ZOMATO*ORDER")

        insert(at(2026, 6, 10), 1_000, "ZOMATO", payeeId = zomato)
        insert(at(2026, 6, 11), 1_000, "ZOMATO*ORDER", payeeId = zomato)

        insert(at(2026, 6, 12), 1_000, "SHOP A")
        insert(at(2026, 6, 13), 1_000, "SHOP B")

        assertThat(share(food).payeeCount).isEqualTo(1)
        assertThat(share(null).payeeCount).isEqualTo(2)
    }

    @Test
    fun anEmptyRangeYieldsZeroesRatherThanNoRow() = runTest {
        val food = category("Food")

        val share = share(food, from = at(2026, 1, 1), to = at(2026, 2, 1))

        // An aggregate over no rows returns NULL, which cannot bind to a non-null column. An empty
        // month is a normal state and has to read as zeroes rather than crash.
        assertThat(share.totalPaise).isEqualTo(0L)
        assertThat(share.accountTotalPaise).isEqualTo(0L)
        assertThat(share.payeeCount).isEqualTo(0)
        assertThat(share.sharePercent).isNull()
    }

    // ---- Months -----------------------------------------------------------------------------

    @Test
    fun monthTotalsBucketByCalendarMonthInUtc() = runTest {
        val food = category("Food")
        val zomato = payee("Zomato", food, "ZOMATO")

        insert(at(2026, 6, 1, hour = 0), 1_000, "ZOMATO", payeeId = zomato)
        insert(at(2026, 6, 30, hour = 23), 2_000, "ZOMATO", payeeId = zomato)
        insert(at(2026, 7, 1, hour = 0), 4_000, "ZOMATO", payeeId = zomato)

        val months = monthTotals(food)

        // Two buckets, newest first, and the row at the last hour of June stays in June.
        assertThat(months).hasSize(2)
        assertThat(months[0].startMillis).isEqualTo(at(2026, 7, 1, hour = 0))
        assertThat(months[0].countedTotalPaise).isEqualTo(4_000L)
        assertThat(months[1].startMillis).isEqualTo(at(2026, 6, 1, hour = 0))
        assertThat(months[1].countedTotalPaise).isEqualTo(3_000L)
    }

    /**
     * A category the user has since deleted still names the spend that was mapped to it. Dropping
     * that mapping would move real spend into the unmapped bucket and overstate how much of the
     * account has no name on it.
     */
    @Test
    fun spendUnderADeletedCategoryStillBelongsToIt() = runTest {
        val food = category("Food")
        val zomato = payee("Zomato", food, "ZOMATO")
        insert(at(2026, 6, 10), 1_000, "ZOMATO", payeeId = zomato)

        database.categoryDao().softDelete(OWNER, food, deletedAtMillis = 1L)

        assertThat(share(food).totalPaise).isEqualTo(1_000L)
        assertThat(share(null).totalPaise).isEqualTo(0L)
    }

    private companion object {
        const val OWNER = "google-sub-a"
        const val OTHER_OWNER = "google-sub-b"
        const val COMPLETED_SESSION = 1L
        const val CANCELLED_SESSION = 2L
        const val OTHER_ACCOUNT_SESSION = 3L
        const val ALL_FROM = Long.MIN_VALUE
        const val ALL_TO = Long.MAX_VALUE
    }
}
