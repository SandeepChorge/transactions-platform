package com.madtitan94.transactionsparser.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
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
 * The Payee Detail aggregates are raw SQL — day flooring, `start of month`, and conditional sums
 * that decide what the user is told they spent. Nothing above the DAO can catch a mistake in them.
 */
@RunWith(AndroidJUnit4::class)
class PayeeDetailQueriesTest {

    private lateinit var database: TransactionsDatabase

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            TransactionsDatabase::class.java
        ).build()
        // transactions.sessionId is a foreign key, so the rows need a session to hang off.
        database.sessionDao().insert(
            SessionEntity(
                id = SESSION_ID,
                ownerId = OWNER,
                fileName = "june.pdf",
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
        isDuplicate: Boolean = false,
        isExcluded: Boolean = false,
        ownerId: String = OWNER
    ) {
        database.transactionDao().insertAll(
            listOf(
                TransactionEntity(
                    ownerId = ownerId,
                    sessionId = SESSION_ID,
                    dateTimeUtcMillis = millis,
                    rawPayee = payee,
                    normalizedPayee = payee,
                    amountPaise = amountPaise,
                    type = "DEBIT",
                    transactionRef = null,
                    utr = null,
                    payeeId = null,
                    isDuplicate = isDuplicate,
                    isExcluded = isExcluded
                )
            )
        )
    }

    @Test
    fun totalsCountOnlyIncludedRowsButTheRangeCoversThemAll() =
        runTest {
            insert(at(2026, 6, 1), 1_000)
            insert(at(2026, 6, 10), 2_000)
            insert(at(2026, 6, 30), 5_000, isDuplicate = true, isExcluded = true)

            val totals = database.transactionDao().observePayeeTotals(OWNER, PAYEE).first()!!

            assertThat(totals.countedTotalPaise).isEqualTo(3_000L)
            assertThat(totals.countedCount).isEqualTo(2)
            assertThat(totals.transactionCount).isEqualTo(3)
            assertThat(totals.duplicateCount).isEqualTo(1)
            assertThat(totals.excludedDuplicateCount).isEqualTo(1)
            // An excluded row still happened, so it still bounds the period shown.
            assertThat(totals.firstMillis).isEqualTo(at(2026, 6, 1))
            assertThat(totals.lastMillis).isEqualTo(at(2026, 6, 30))
        }

    @Test
    fun aPayeeWithNoRowsAggregatesToNullsRatherThanFailing() = runTest {
        val totals = database.transactionDao().observePayeeTotals(OWNER, "NOBODY").first()!!

        assertThat(totals.countedTotalPaise).isNull()
        assertThat(totals.transactionCount).isEqualTo(0)
        assertThat(totals.firstMillis).isNull()
    }

    @Test
    fun rowsOnTheSameDayShareABucketRegardlessOfTheHour() = runTest {
        insert(at(2026, 6, 10, hour = 0, minute = 1), 1_000)
        insert(at(2026, 6, 10, hour = 23, minute = 59), 2_000)
        insert(at(2026, 6, 11, hour = 0, minute = 0), 4_000)

        val days = database.transactionDao().observePayeeDayTotals(OWNER, PAYEE).first()

        // Newest first, and each bucket starts at midnight of its own day.
        assertThat(days.map { it.startMillis })
            .containsExactly(at(2026, 6, 11, hour = 0), at(2026, 6, 10, hour = 0))
        assertThat(days.map { it.countedTotalPaise }).containsExactly(4_000L, 3_000L)
        assertThat(days.map { it.countedCount }).containsExactly(1, 2)
    }

    @Test
    fun aMidnightRowBelongsToItsOwnDayNotTheOneBefore() = runTest {
        insert(at(2026, 6, 11, hour = 0, minute = 0), 1_000)

        val days = database.transactionDao().observePayeeDayTotals(OWNER, PAYEE).first()

        assertThat(days.single().startMillis).isEqualTo(at(2026, 6, 11, hour = 0))
    }

    @Test
    fun monthsBucketOnCalendarBoundariesIncludingAcrossAYear() = runTest {
        insert(at(2025, 12, 31, hour = 23), 1_000)
        insert(at(2026, 1, 1, hour = 0), 2_000)
        insert(at(2026, 1, 31, hour = 23), 4_000)

        val months = database.transactionDao().observePayeeMonthTotals(OWNER, PAYEE).first()

        assertThat(months.map { it.startMillis })
            .containsExactly(at(2026, 1, 1, hour = 0), at(2025, 12, 1, hour = 0))
        assertThat(months.map { it.countedTotalPaise }).containsExactly(6_000L, 1_000L)
    }

    @Test
    fun anExcludedRowDropsOutOfSubtotalsButKeepsItsBucket() =
        runTest {
            insert(at(2026, 6, 10), 1_000, isDuplicate = true, isExcluded = true)

            val days = database.transactionDao().observePayeeDayTotals(OWNER, PAYEE).first()
            val months = database.transactionDao().observePayeeMonthTotals(OWNER, PAYEE).first()

            // The header still appears — its rows are on screen — but sums to nothing.
            assertThat(days.single().countedTotalPaise).isEqualTo(0L)
            assertThat(days.single().countedCount).isEqualTo(0)
            assertThat(months.single().countedTotalPaise).isEqualTo(0L)
        }

    @Test
    fun everyPayeeQueryIsScopedToOneAccount() = runTest {
        insert(at(2026, 6, 10), 1_000, ownerId = "other-account")

        assertThat(database.transactionDao().observePayeeTotals(OWNER, PAYEE).first()!!.transactionCount)
            .isEqualTo(0)
        assertThat(database.transactionDao().observePayeeDayTotals(OWNER, PAYEE).first()).isEmpty()
        assertThat(database.transactionDao().observePayeeMonthTotals(OWNER, PAYEE).first()).isEmpty()
    }

    @Test
    fun anotherPayeesRowsNeverLandInTheseTotals() = runTest {
        insert(at(2026, 6, 10), 1_000)
        insert(at(2026, 6, 10), 9_000, payee = "SOMEONE ELSE")

        val totals = database.transactionDao().observePayeeTotals(OWNER, PAYEE).first()!!

        assertThat(totals.countedTotalPaise).isEqualTo(1_000L)
        assertThat(totals.transactionCount).isEqualTo(1)
    }

    private companion object {
        const val OWNER = "google-sub-a"
        const val SESSION_ID = 1L
        const val PAYEE = "ABC SHOP"
    }
}
