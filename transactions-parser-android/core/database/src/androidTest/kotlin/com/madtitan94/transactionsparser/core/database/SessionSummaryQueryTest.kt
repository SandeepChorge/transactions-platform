package com.madtitan94.transactionsparser.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
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

/**
 * The session history card is the only place the user sees whether an import worked at all, and
 * everything it says comes out of one grouped query. Excluding rows in the JOIN rather than in the
 * aggregates once collapsed an all-duplicate import to "0 of 0 transactions mapped", which reads
 * exactly like an upload that found nothing — so the counts are pinned here.
 */
@RunWith(AndroidJUnit4::class)
class SessionSummaryQueryTest {

    private lateinit var database: TransactionsDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            TransactionsDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insertSession(id: Long, ownerId: String = OWNER, status: String = PENDING) {
        database.sessionDao().insert(
            SessionEntity(
                id = id,
                ownerId = ownerId,
                fileName = "statement-$id.pdf",
                source = "PHONEPE",
                uploadedAtMillis = id,
                periodStartMillis = null,
                periodEndMillis = null,
                status = status
            )
        )
    }

    private suspend fun insertTransaction(
        sessionId: Long = SESSION_ID,
        payeeId: Long? = null,
        isDuplicate: Boolean = false,
        isExcluded: Boolean = false
    ) {
        database.transactionDao().insertAll(
            listOf(
                TransactionEntity(
                    ownerId = OWNER,
                    sessionId = sessionId,
                    dateTimeUtcMillis = 0L,
                    rawPayee = "ABC SHOP",
                    normalizedPayee = "ABC SHOP",
                    amountPaise = 1_000,
                    type = "DEBIT",
                    transactionRef = null,
                    utr = null,
                    payeeId = payeeId,
                    isDuplicate = isDuplicate,
                    isExcluded = isExcluded
                )
            )
        )
    }

    /** A payee needs a category to hang off and a statement name to be reachable by. */
    private suspend fun insertPayee(): Long {
        val categoryId = database.categoryDao().insert(CategoryEntity(ownerId = OWNER, name = "Food"))
        val payeeId = database.payeeDao().insert(
            PayeeEntity(ownerId = OWNER, alias = "Corner shop", categoryId = categoryId)
        )
        database.payeeIdentifierDao().insert(
            PayeeIdentifierEntity(
                ownerId = OWNER,
                payeeId = payeeId,
                rawName = "ABC SHOP",
                normalizedName = "ABC SHOP"
            )
        )
        return payeeId
    }

    private suspend fun summary() =
        database.sessionDao().observeSummaries(OWNER, PENDING).first().single()

    @Test
    fun anAllDuplicateSessionStillReportsWhatItImported() = runTest {
        insertSession(SESSION_ID)
        repeat(3) { insertTransaction(isDuplicate = true, isExcluded = true) }

        val row = summary()

        // The import worked — three rows landed. None of them are countable, which is the
        // difference the card needs in order to explain itself.
        assertThat(row.transactionCount).isEqualTo(3)
        assertThat(row.countedCount).isEqualTo(0)
        assertThat(row.mappedCount).isEqualTo(0)
    }

    @Test
    fun excludedRowsCountTowardTheTotalButNotTowardProgress() = runTest {
        insertSession(SESSION_ID)
        val payeeId = insertPayee()
        insertTransaction(payeeId = payeeId)
        insertTransaction()
        insertTransaction(payeeId = payeeId, isDuplicate = true, isExcluded = true)

        val row = summary()

        assertThat(row.transactionCount).isEqualTo(3)
        assertThat(row.countedCount).isEqualTo(2)
        // The excluded row is mapped, but it isn't counted — so it isn't progress either.
        assertThat(row.mappedCount).isEqualTo(1)
    }

    @Test
    fun aSessionWithNoTransactionsAggregatesToZerosRatherThanFailing() = runTest {
        insertSession(SESSION_ID)

        val row = summary()

        // The LEFT JOIN yields one all-NULL row here; without IFNULL the SUMs would come back
        // null and fail to bind to the non-null Int columns.
        assertThat(row.transactionCount).isEqualTo(0)
        assertThat(row.countedCount).isEqualTo(0)
        assertThat(row.mappedCount).isEqualTo(0)
    }

    @Test
    fun oneSessionsRowsNeverLeakIntoAnothersCounts() = runTest {
        insertSession(SESSION_ID)
        insertSession(OTHER_SESSION_ID)
        insertTransaction(sessionId = SESSION_ID)
        repeat(4) { insertTransaction(sessionId = OTHER_SESSION_ID) }

        val rows = database.sessionDao().observeSummaries(OWNER, PENDING).first()

        assertThat(rows.single { it.session.id == SESSION_ID }.transactionCount).isEqualTo(1)
        assertThat(rows.single { it.session.id == OTHER_SESSION_ID }.transactionCount).isEqualTo(4)
    }

    @Test
    fun summariesStayScopedToOneAccount() = runTest {
        insertSession(SESSION_ID, ownerId = "other-account")

        assertThat(database.sessionDao().observeSummaries(OWNER, PENDING).first()).isEmpty()
    }

    private companion object {
        const val OWNER = "google-sub-a"
        const val SESSION_ID = 1L
        const val OTHER_SESSION_ID = 2L
        const val PENDING = "PENDING"
    }
}
