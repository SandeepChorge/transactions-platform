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
import com.madtitan94.transactionsparser.core.domain.model.SearchQuery
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The two Phase 9 queries, against real Room.
 *
 * Both are the kind that fail quietly. A search whose `LIKE` escaping is wrong returns the whole
 * account and looks like a very broad match; an anomaly query whose baseline overlaps the period it
 * is judging returns fewer callouts rather than an error, and a null-category join that uses `=`
 * instead of `IS` returns none at all from the unmapped bucket. None of that shows up against a
 * fake, so these run against SQLite.
 */
@RunWith(AndroidJUnit4::class)
class SearchAndAnomalyQueriesTest {

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
        utr: String? = null,
        transactionRef: String? = null,
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
                    normalizedPayee = payee.lowercase(),
                    amountPaise = amountPaise,
                    type = type,
                    transactionRef = transactionRef,
                    utr = utr,
                    payeeId = payeeId,
                    isDuplicate = false,
                    isExcluded = isExcluded
                )
            )
        )
    }

    private suspend fun category(name: String): Long =
        database.categoryDao().insert(CategoryEntity(ownerId = OWNER, name = name))

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

    private suspend fun search(typed: String): List<String> {
        val query = SearchQuery.parse(typed)
        return database.transactionDao().observeSearch(
            ownerId = OWNER,
            pattern = query.likePattern(),
            amountFromPaise = query.amountFromPaise,
            amountToPaise = query.amountToPaise,
            limit = 100
        ).first().map { it.rawPayee }
    }

    private suspend fun searchCount(typed: String): Int {
        val query = SearchQuery.parse(typed)
        return database.transactionDao().observeSearchCount(
            ownerId = OWNER,
            pattern = query.likePattern(),
            amountFromPaise = query.amountFromPaise,
            amountToPaise = query.amountToPaise
        ).first()
    }

    // ---- Search -----------------------------------------------------------------------------

    @Test
    fun searchMatchesTheStatementNameCaseInsensitively() = runTest {
        insert(at(2026, 5, 4), 125_000L, "SWIGGY BANGALORE")
        insert(at(2026, 5, 5), 40_000L, "UBER")

        assertThat(search("swiggy")).isEqualTo(listOf("SWIGGY BANGALORE"))
    }

    @Test
    fun searchMatchesTheAliasTheUserGaveThePayee() = runTest {
        // A user who renamed SWIGGY*ORDER to "Dinner" thinks of it as Dinner. A search that only
        // knew the bank's spelling would fail on the one name they remember.
        val food = category("Food")
        val id = payee("Dinner", food, "swiggy*order")
        insert(at(2026, 5, 4), 125_000L, "SWIGGY*ORDER", payeeId = id)

        assertThat(search("dinner")).isEqualTo(listOf("SWIGGY*ORDER"))
    }

    @Test
    fun searchMatchesUtrAndReference() = runTest {
        insert(at(2026, 5, 4), 125_000L, "SWIGGY", utr = "412345678901")
        insert(at(2026, 5, 5), 40_000L, "UBER", transactionRef = "REF-99881")

        assertThat(search("456789")).isEqualTo(listOf("SWIGGY"))
        assertThat(search("99881")).isEqualTo(listOf("UBER"))
    }

    @Test
    fun aRupeeFigureMatchesEveryPaiseInsideThatRupee() = runTest {
        insert(at(2026, 5, 4), 125_037L, "SWIGGY")
        insert(at(2026, 5, 5), 125_100L, "UBER")

        // ₹1,250.37 is inside the rupee the user typed; ₹1,251.00 is the next one along.
        assertThat(search("1250")).isEqualTo(listOf("SWIGGY"))
    }

    @Test
    fun typingThePaiseNarrowsToTheExactAmount() = runTest {
        insert(at(2026, 5, 4), 125_037L, "SWIGGY")
        insert(at(2026, 5, 5), 125_099L, "UBER")

        assertThat(search("1250.37")).isEqualTo(listOf("SWIGGY"))
    }

    @Test
    fun anAmountAndATextMatchBothCount() = runTest {
        // 1250 is a plausible amount and a plausible fragment of a reference, and nothing in a
        // search box says which the user meant, so both rows come back.
        insert(at(2026, 5, 5), 125_000L, "SWIGGY")
        insert(at(2026, 5, 4), 40_000L, "UBER", transactionRef = "REF-1250-X")

        assertThat(search("1250").toSet()).isEqualTo(setOf("SWIGGY", "UBER"))
    }

    @Test
    fun aTypedWildcardDoesNotMatchEveryRow() = runTest {
        // Unescaped, this LIKE pattern would return the whole account and present it as a result.
        insert(at(2026, 5, 4), 125_000L, "SWIGGY")
        insert(at(2026, 5, 5), 40_000L, "100% PURE")

        assertThat(search("100%")).isEqualTo(listOf("100% PURE"))
    }

    @Test
    fun searchFindsExcludedRowsAndCancelledStatements() = runTest {
        // The row a user is hunting for is very often the one the app decided not to count, and the
        // search is the only route to the screen where that decision is reversed.
        insert(at(2026, 5, 4), 125_000L, "SWIGGY EXCLUDED", isExcluded = true)
        insert(at(2026, 5, 3), 90_000L, "SWIGGY CANCELLED", sessionId = CANCELLED_SESSION)

        assertThat(search("swiggy")).hasSize(2)
    }

    @Test
    fun searchNeverCrossesAccounts() = runTest {
        insert(at(2026, 5, 4), 125_000L, "SWIGGY", ownerId = OTHER_OWNER, sessionId = OTHER_ACCOUNT_SESSION)

        assertThat(search("swiggy")).isEmpty()
    }

    @Test
    fun searchIsOrderedNewestFirst() = runTest {
        insert(at(2026, 5, 4), 10_000L, "SWIGGY OLD")
        insert(at(2026, 7, 4), 10_000L, "SWIGGY NEW")

        assertThat(search("swiggy")).isEqualTo(listOf("SWIGGY NEW", "SWIGGY OLD"))
    }

    @Test
    fun theCountIsOverEveryMatchRatherThanOverTheFetchedRows() = runTest {
        // Counting the returned rows would report the limit, and an account with hundreds of
        // matches would read the same as one with a handful.
        repeat(5) { index -> insert(at(2026, 5, index + 1), 10_000L, "SWIGGY $index") }

        val query = SearchQuery.parse("swiggy")
        val page = database.transactionDao().observeSearch(
            ownerId = OWNER,
            pattern = query.likePattern(),
            amountFromPaise = null,
            amountToPaise = null,
            limit = 2
        ).first()

        assertThat(page).hasSize(2)
        assertThat(searchCount("swiggy")).isEqualTo(5)
    }

    // ---- Anomalies --------------------------------------------------------------------------

    private suspend fun anomalies(
        allCategories: Boolean = true,
        categoryId: Long? = null,
        multiplier: Double = 3.0,
        minSampleCount: Int = 3,
        minAmountPaise: Long = 50_000L
    ) = database.transactionDao().observeAnomalies(
        ownerId = OWNER,
        fromMillis = at(2026, 5, 1, hour = 0),
        toMillisExclusive = at(2026, 6, 1, hour = 0),
        baselineFromMillis = at(2026, 2, 1, hour = 0),
        baselineToMillisExclusive = at(2026, 5, 1, hour = 0),
        allCategories = allCategories,
        categoryId = categoryId,
        multiplier = multiplier,
        minSampleCount = minSampleCount,
        minAmountPaise = minAmountPaise,
        limit = 10
    ).first()

    /** Four ordinary ₹1,000 charges in February–April, enough to be a habit. */
    private suspend fun ordinaryHistory(payee: String, payeeId: Long?) {
        listOf(at(2026, 2, 5), at(2026, 3, 5), at(2026, 3, 20), at(2026, 4, 5))
            .forEach { insert(it, 100_000L, payee, payeeId = payeeId) }
    }

    @Test
    fun aChargeWellAboveTheCategorysUsualIsFlagged() = runTest {
        val food = category("Food")
        val id = payee("Swiggy", food, "swiggy")
        ordinaryHistory("SWIGGY", id)
        insert(at(2026, 5, 12), 500_000L, "SWIGGY", payeeId = id)

        val found = anomalies()

        assertThat(found).hasSize(1)
        assertThat(found.single().amountPaise).isEqualTo(500_000L)
        assertThat(found.single().baselineMeanPaise).isEqualTo(100_000L)
        assertThat(found.single().baselineSampleCount).isEqualTo(4)
    }

    @Test
    fun theChargeUnderExaminationIsNotPartOfItsOwnBaseline() = runTest {
        // If it were, a large enough charge would raise the average it is judged against far enough
        // to hide itself — the failure mode this window's non-overlap exists to prevent.
        val food = category("Food")
        val id = payee("Swiggy", food, "swiggy")
        ordinaryHistory("SWIGGY", id)
        insert(at(2026, 5, 12), 500_000L, "SWIGGY", payeeId = id)

        // The mean is 4 × ₹1,000 / 4, not 5 × (4 × ₹1,000 + ₹5,000) / 5 = ₹1,800.
        assertThat(anomalies().single().baselineMeanPaise).isEqualTo(100_000L)
    }

    @Test
    fun aThinBaselineMakesNoClaim() = runTest {
        // A mean of one prior charge is not a habit, and a category with one would flag its second
        // charge every time. This is what keeps a freshly imported account quiet.
        val food = category("Food")
        val id = payee("Swiggy", food, "swiggy")
        insert(at(2026, 4, 5), 100_000L, "SWIGGY", payeeId = id)
        insert(at(2026, 5, 12), 500_000L, "SWIGGY", payeeId = id)

        assertThat(anomalies(minSampleCount = 3)).isEmpty()
    }

    @Test
    fun aLargeRatioOnSmallMoneyIsNotNews() = runTest {
        // Four times a ₹12 usual is arithmetic, not a finding. Without the floor the callouts fill
        // up with the smallest charges in the account, which also have the noisiest averages.
        val food = category("Food")
        val id = payee("Chai", food, "chai")
        listOf(at(2026, 2, 5), at(2026, 3, 5), at(2026, 3, 20), at(2026, 4, 5))
            .forEach { insert(it, 1_200L, "CHAI", payeeId = id) }
        insert(at(2026, 5, 12), 12_000L, "CHAI", payeeId = id)

        assertThat(anomalies(minAmountPaise = 50_000L)).isEmpty()
    }

    @Test
    fun theUnmappedBucketHasAnomaliesLikeAnyOtherCategory() = runTest {
        // Joining the baseline on `=` rather than `IS` silently drops every unmapped row, because
        // NULL = NULL is NULL — and unmapped spend is the part of the account least under the
        // user's eye, so it is the last place a callout should be impossible.
        ordinaryHistory("UNKNOWN SHOP", payeeId = null)
        insert(at(2026, 5, 12), 500_000L, "UNKNOWN SHOP")

        val found = anomalies()

        assertThat(found).hasSize(1)
        assertThat(found.single().categoryId).isNull()
    }

    @Test
    fun oneCategoryCanBeAskedAboutOnItsOwn() = runTest {
        val food = category("Food")
        val travel = category("Travel")
        val swiggy = payee("Swiggy", food, "swiggy")
        val cabs = payee("City Cabs", travel, "city cabs")
        ordinaryHistory("SWIGGY", swiggy)
        ordinaryHistory("CITY CABS", cabs)
        insert(at(2026, 5, 12), 500_000L, "SWIGGY", payeeId = swiggy)
        insert(at(2026, 5, 13), 600_000L, "CITY CABS", payeeId = cabs)

        assertThat(anomalies(allCategories = false, categoryId = food).map { it.rawPayee })
            .isEqualTo(listOf("SWIGGY"))
    }

    @Test
    fun askingAboutTheUnmappedBucketDoesNotReturnEveryCategory() = runTest {
        val food = category("Food")
        val swiggy = payee("Swiggy", food, "swiggy")
        ordinaryHistory("SWIGGY", swiggy)
        ordinaryHistory("UNKNOWN SHOP", payeeId = null)
        insert(at(2026, 5, 12), 500_000L, "SWIGGY", payeeId = swiggy)
        insert(at(2026, 5, 13), 500_000L, "UNKNOWN SHOP")

        assertThat(anomalies(allCategories = false, categoryId = null).map { it.rawPayee })
            .isEqualTo(listOf("UNKNOWN SHOP"))
    }

    @Test
    fun creditsAreNeverFlagged() = runTest {
        // A salary landing is not an unusual charge; it is not a charge at all.
        val food = category("Food")
        val id = payee("Swiggy", food, "swiggy")
        ordinaryHistory("SWIGGY", id)
        insert(at(2026, 5, 12), 900_000L, "SWIGGY", type = "CREDIT", payeeId = id)

        assertThat(anomalies()).isEmpty()
    }

    @Test
    fun excludedRowsAndCancelledStatementsAreNotFlagged() = runTest {
        // Callouts are an aggregate claim, not a search result — they follow the same counting
        // rules the dashboard does, or the banner would contradict the totals beneath it.
        val food = category("Food")
        val id = payee("Swiggy", food, "swiggy")
        ordinaryHistory("SWIGGY", id)
        insert(at(2026, 5, 12), 500_000L, "SWIGGY", payeeId = id, isExcluded = true)
        insert(at(2026, 5, 13), 500_000L, "SWIGGY", payeeId = id, sessionId = CANCELLED_SESSION)

        assertThat(anomalies()).isEmpty()
    }

    @Test
    fun theBiggestMultipleIsReportedFirst() = runTest {
        // Ordered by how unusual the charge is, not by how large it is: a ₹5,000 charge in a
        // ₹100 category is the more surprising of the two.
        val food = category("Food")
        val travel = category("Travel")
        val swiggy = payee("Swiggy", food, "swiggy")
        val cabs = payee("City Cabs", travel, "city cabs")
        listOf(at(2026, 2, 5), at(2026, 3, 5), at(2026, 3, 20), at(2026, 4, 5)).forEach {
            insert(it, 100_000L, "SWIGGY", payeeId = swiggy)
            insert(it, 20_000L, "CITY CABS", payeeId = cabs)
        }
        insert(at(2026, 5, 12), 400_000L, "SWIGGY", payeeId = swiggy)
        insert(at(2026, 5, 13), 200_000L, "CITY CABS", payeeId = cabs)

        // Cabs is 10× its usual; Swiggy is 4× despite being twice the money.
        assertThat(anomalies().map { it.rawPayee }).isEqualTo(listOf("CITY CABS", "SWIGGY"))
    }

    @Test
    fun anomaliesNeverCrossAccounts() = runTest {
        insert(
            at(2026, 5, 12),
            500_000L,
            "THEIRS",
            ownerId = OTHER_OWNER,
            sessionId = OTHER_ACCOUNT_SESSION
        )

        assertThat(anomalies()).isEmpty()
    }

    private companion object {
        const val OWNER = "google-sub-a"
        const val OTHER_OWNER = "google-sub-b"
        const val COMPLETED_SESSION = 1L
        const val CANCELLED_SESSION = 2L
        const val OTHER_ACCOUNT_SESSION = 3L
    }
}
