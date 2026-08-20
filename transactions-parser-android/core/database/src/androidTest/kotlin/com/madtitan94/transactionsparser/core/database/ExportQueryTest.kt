package com.madtitan94.transactionsparser.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.madtitan94.transactionsparser.core.database.entity.CategoryEntity
import com.madtitan94.transactionsparser.core.database.entity.PayeeEntity
import com.madtitan94.transactionsparser.core.database.entity.SessionEntity
import com.madtitan94.transactionsparser.core.database.entity.TransactionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * The export query is three `LEFT JOIN`s across four tables. An inner join anywhere in it would
 * silently drop every unmapped payee from the file — the rows the user is most likely to be
 * looking for — and nothing above the DAO would notice.
 */
@RunWith(AndroidJUnit4::class)
class ExportQueryTest {

    private lateinit var database: TransactionsDatabase

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            TransactionsDatabase::class.java
        ).build()
        database.sessionDao().insert(session(SESSION_ID, "june.pdf"))
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun session(id: Long, fileName: String, ownerId: String = OWNER) = SessionEntity(
        id = id,
        ownerId = ownerId,
        fileName = fileName,
        source = "PHONEPE",
        uploadedAtMillis = 0L,
        periodStartMillis = null,
        periodEndMillis = null,
        status = "COMPLETED"
    )

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        LocalDateTime.of(year, month, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private suspend fun insert(
        millis: Long = at(2026, 6, 1),
        payee: String = "SWIGGY",
        amountPaise: Long = 25_800L,
        payeeId: Long? = null,
        sessionId: Long = SESSION_ID,
        isDuplicate: Boolean = false,
        isExcluded: Boolean = false,
        isDeleted: Boolean = false,
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
                    type = "DEBIT",
                    transactionRef = "REF-$payee",
                    utr = null,
                    payeeId = payeeId,
                    isDuplicate = isDuplicate,
                    isExcluded = isExcluded,
                    isDeleted = isDeleted
                )
            )
        )
    }

    private suspend fun mapping(alias: String, category: String, payee: String): Long {
        val categoryId = database.categoryDao().insert(CategoryEntity(ownerId = OWNER, name = category))
        return database.payeeDao().insert(
            PayeeEntity(
                ownerId = OWNER,
                rawName = payee,
                normalizedName = payee,
                alias = alias,
                categoryId = categoryId
            )
        )
    }

    @Test
    fun aMappedRowCarriesItsAliasCategoryAndStatement() = runTest {
        val payeeId = mapping(alias = "Swiggy", category = "Food", payee = "SWIGGY")
        insert(payeeId = payeeId)

        val row = database.transactionDao().exportRows(OWNER).single()

        assertThat(row.alias).isEqualTo("Swiggy")
        assertThat(row.category).isEqualTo("Food")
        assertThat(row.statementFileName).isEqualTo("june.pdf")
        assertThat(row.amountPaise).isEqualTo(25_800L)
    }

    @Test
    fun anUnmappedRowStillExportsWithEmptyAliasAndCategory() = runTest {
        insert(payee = "UNKNOWN SHOP", payeeId = null)

        val row = database.transactionDao().exportRows(OWNER).single()

        // The whole point of the LEFT JOINs: no mapping must never mean no row.
        assertThat(row.rawPayee).isEqualTo("UNKNOWN SHOP")
        assertThat(row.alias).isNull()
        assertThat(row.category).isNull()
    }

    @Test
    fun anExcludedRowIsExportedWithItsFlagsRatherThanBeingFilteredOut() = runTest {
        insert(isDuplicate = true, isExcluded = true)

        val row = database.transactionDao().exportRows(OWNER).single()

        assertThat(row.isDuplicate).isEqualTo(true)
        assertThat(row.isExcluded).isEqualTo(true)
    }

    @Test
    fun aSoftDeletedRowIsLeftOutOfTheExport() = runTest {
        insert(payee = "KEPT")
        insert(payee = "REMOVED", isDeleted = true)

        val payees = database.transactionDao().exportRows(OWNER).map { it.rawPayee }

        assertThat(payees).containsExactly("KEPT")
    }

    @Test
    fun anotherAccountsRowsNeverReachThisExport() = runTest {
        database.sessionDao().insert(session(OTHER_SESSION_ID, "other.pdf", ownerId = OTHER_OWNER))
        insert(payee = "MINE")
        insert(payee = "THEIRS", sessionId = OTHER_SESSION_ID, ownerId = OTHER_OWNER)

        val payees = database.transactionDao().exportRows(OWNER).map { it.rawPayee }

        assertThat(payees).containsExactly("MINE")
    }

    @Test
    fun rowsComeBackNewestFirstSoTheFileOpensOnRecentActivity() = runTest {
        insert(millis = at(2026, 6, 1), payee = "OLDEST")
        insert(millis = at(2026, 6, 30), payee = "NEWEST")
        insert(millis = at(2026, 6, 15), payee = "MIDDLE")

        val payees = database.transactionDao().exportRows(OWNER).map { it.rawPayee }

        assertThat(payees).containsExactly("NEWEST", "MIDDLE", "OLDEST")
    }

    @Test
    fun anAccountWithNoRowsExportsNothingRatherThanFailing() = runTest {
        assertThat(database.transactionDao().exportRows(OWNER).size).isEqualTo(0)
    }

    private companion object {
        const val OWNER = "google-sub-a"
        const val OTHER_OWNER = "google-sub-b"
        const val SESSION_ID = 1L
        const val OTHER_SESSION_ID = 2L
    }
}
