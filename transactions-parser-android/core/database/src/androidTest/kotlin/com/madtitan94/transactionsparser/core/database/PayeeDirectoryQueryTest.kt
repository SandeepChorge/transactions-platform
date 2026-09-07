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

/**
 * `PayeeDao.observeDirectory` is a hand-written `UNION ALL` over two halves that are found by
 * different routes, and the failure modes it is written to avoid are all silent ones: a merged
 * payee counted twice, a payee with no countable spend vanishing entirely, an unclaimed name
 * showing up as its own payee. None of them throws, so none of them is catchable above the DAO.
 */
@RunWith(AndroidJUnit4::class)
class PayeeDirectoryQueryTest {

    private lateinit var database: TransactionsDatabase

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            TransactionsDatabase::class.java
        ).build()
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
        // The other account gets a session of its own: `sessions` is joined on owner as well as id,
        // so borrowing this account's would make its rows uncountable for the wrong reason.
        database.sessionDao().insert(
            SessionEntity(
                id = OTHER_SESSION_ID,
                ownerId = OTHER_OWNER,
                fileName = "june.pdf",
                source = "PHONEPE",
                uploadedAtMillis = 0L,
                periodStartMillis = null,
                periodEndMillis = null,
                status = "COMPLETED"
            )
        )
        database.categoryDao().insert(CategoryEntity(id = CATEGORY_ID, ownerId = OWNER, name = "Food"))
    }

    @After
    fun tearDown() = database.close()

    private suspend fun directory(ownerId: String = OWNER) =
        database.payeeDao().observeDirectory(ownerId).first()

    /** Creates a payee owning [names], the first of which the directory should show. */
    private suspend fun payee(alias: String, vararg names: String): Long {
        val payeeId = database.payeeDao().insert(
            PayeeEntity(ownerId = OWNER, alias = alias, categoryId = CATEGORY_ID)
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

    private suspend fun spend(
        payee: String,
        amountPaise: Long,
        payeeId: Long? = null,
        type: String = "DEBIT",
        isExcluded: Boolean = false,
        ownerId: String = OWNER,
        sessionId: Long = SESSION_ID
    ) {
        database.transactionDao().insertAll(
            listOf(
                TransactionEntity(
                    ownerId = ownerId,
                    sessionId = sessionId,
                    dateTimeUtcMillis = 0L,
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

    @Test
    fun anEmptyAccountHasAnEmptyDirectory() = runTest {
        assertThat(directory()).isEmpty()
    }

    /**
     * The bug this whole query is shaped around. Joining `payee_identifiers` alongside the
     * transactions would fan each row out once per name and double this payee's total — and it
     * would do it to exactly the merged payees the app exists to get right.
     */
    @Test
    fun aMergedPayeeTotalsOnceRatherThanOncePerName() = runTest {
        payee("Corner Shop", "ABC SHOP", "ABC SHOP PVT LTD")
        spend("ABC SHOP", 1_000)
        spend("ABC SHOP PVT LTD", 3_000)

        val rows = directory()

        assertThat(rows).hasSize(1)
        assertThat(rows.single().totalPaise).isEqualTo(4_000L)
        assertThat(rows.single().transactionCount).isEqualTo(2)
        assertThat(rows.single().identifierCount).isEqualTo(2)
    }

    /**
     * A payee whose every transaction is excluded is precisely who the user opens the directory to
     * find. Grouping transactions could not produce this row; starting from `payees` is what does.
     */
    @Test
    fun aPayeeWithNothingCountableStillHasARowAtZero() = runTest {
        payee("Refunded Shop", "REFUND CO")
        spend("REFUND CO", 5_000, isExcluded = true)

        val rows = directory()

        assertThat(rows).hasSize(1)
        assertThat(rows.single().alias).isEqualTo("Refunded Shop")
        assertThat(rows.single().totalPaise).isEqualTo(0L)
        assertThat(rows.single().transactionCount).isEqualTo(0)
    }

    @Test
    fun aPayeeCreatedBeforeAnyStatementWasImportedIsListed() = runTest {
        payee("Future Shop", "FUTURE CO")

        val rows = directory()

        assertThat(rows.map { it.alias }).containsExactly("Future Shop")
        assertThat(rows.single().totalPaise).isEqualTo(0L)
    }

    /** Unmapped money is money — the names nobody has claimed are the directory's to-do list. */
    @Test
    fun anUnclaimedNameIsItsOwnRowWithNoPayeeOrCategory() = runTest {
        spend("RANDOM UPI STRING", 2_500)

        val rows = directory()

        assertThat(rows).hasSize(1)
        val row = rows.single()
        assertThat(row.payeeId).isNull()
        assertThat(row.alias).isNull()
        assertThat(row.categoryName).isNull()
        assertThat(row.statementName).isEqualTo("RANDOM UPI STRING")
        assertThat(row.identifierCount).isEqualTo(0)
        assertThat(row.totalPaise).isEqualTo(2_500L)
    }

    /**
     * A name is claimed by an identifier, whether or not the transaction carries the stamp. A row
     * imported before its payee was mapped keeps a null `payeeId`, and must not surface as an
     * unmapped name beside the payee that already owns it.
     */
    @Test
    fun aClaimedNameNeverAppearsAsUnmappedEvenWithNoStampOnTheRow() = runTest {
        payee("Corner Shop", "ABC SHOP")
        spend("ABC SHOP", 1_000, payeeId = null)

        val rows = directory()

        assertThat(rows).hasSize(1)
        assertThat(rows.single().alias).isEqualTo("Corner Shop")
        assertThat(rows.single().totalPaise).isEqualTo(1_000L)
    }

    /** A refund arriving from a shop is not spend at it — every other payee aggregate agrees. */
    @Test
    fun creditsCountTowardsNeitherHalfOfTheDirectory() = runTest {
        payee("Corner Shop", "ABC SHOP")
        spend("ABC SHOP", 1_000)
        spend("ABC SHOP", 900, type = "CREDIT")
        spend("REFUND SENDER", 400, type = "CREDIT")

        val rows = directory()

        val shop = rows.single { it.alias == "Corner Shop" }
        assertThat(shop.totalPaise).isEqualTo(1_000L)
        assertThat(shop.transactionCount).isEqualTo(1)
        // The credit-only name has no countable spend, so it is not a group at all.
        assertThat(rows.none { it.statementName == "REFUND SENDER" }).isEqualTo(true)
    }

    @Test
    fun rowsRankByTotalWithNamedAndUnclaimedInterleaved() = runTest {
        payee("Small Shop", "SMALL CO")
        spend("SMALL CO", 500)
        spend("BIG UNKNOWN", 9_000)
        payee("Mid Shop", "MID CO")
        spend("MID CO", 2_000)

        assertThat(directory().map { it.statementName })
            .containsExactly("BIG UNKNOWN", "MID CO", "SMALL CO")
    }

    @Test
    fun aSoftDeletedPayeeLeavesTheDirectory() = runTest {
        val payeeId = payee("Gone Shop", "GONE CO")
        database.payeeDao().update(
            PayeeEntity(
                id = payeeId,
                ownerId = OWNER,
                alias = "Gone Shop",
                categoryId = CATEGORY_ID,
                isDeleted = true
            )
        )

        // The name is still claimed by an identifier, so it does not reappear as unmapped either.
        assertThat(directory()).isEmpty()
    }

    @Test
    fun anotherAccountsPayeesAndNamesAreNotListed() = runTest {
        payee("Corner Shop", "ABC SHOP")
        spend("ABC SHOP", 1_000)
        spend("OTHER ACCOUNT NAME", 7_000, ownerId = OTHER_OWNER, sessionId = OTHER_SESSION_ID)

        assertThat(directory().map { it.statementName }).containsExactly("ABC SHOP")
        assertThat(directory(OTHER_OWNER).map { it.statementName }).containsExactly("OTHER ACCOUNT NAME")
    }

    private companion object {
        const val OWNER = "google-sub-a"
        const val OTHER_OWNER = "google-sub-b"
        const val SESSION_ID = 1L
        const val OTHER_SESSION_ID = 2L
        const val CATEGORY_ID = 1L
    }
}
