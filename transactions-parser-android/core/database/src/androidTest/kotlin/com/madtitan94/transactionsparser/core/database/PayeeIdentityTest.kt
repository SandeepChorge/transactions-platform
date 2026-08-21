package com.madtitan94.transactionsparser.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.madtitan94.transactionsparser.core.database.account.ActiveAccountProvider
import com.madtitan94.transactionsparser.core.database.account.LegacyDataClaimer
import com.madtitan94.transactionsparser.core.database.datasource.RoomPayeeDataSource
import com.madtitan94.transactionsparser.core.database.entity.CategoryEntity
import com.madtitan94.transactionsparser.core.database.entity.SessionEntity
import com.madtitan94.transactionsparser.core.database.entity.TransactionEntity
import com.madtitan94.transactionsparser.core.database.migration.LEGACY_OWNER_ID
import com.madtitan94.transactionsparser.core.domain.datasource.SessionStorage
import com.madtitan94.transactionsparser.core.domain.model.UserSession
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A payee is now the person, and `payee_identifiers` holds what statements call them. These pin
 * the behaviour that split buys us: a name resolves through the identifier table, one name means
 * one payee per account, and neither crosses an account boundary.
 */
@RunWith(AndroidJUnit4::class)
class PayeeIdentityTest {

    private lateinit var database: TransactionsDatabase
    private lateinit var sessionStorage: FakeSessionStorage
    private lateinit var payees: RoomPayeeDataSource

    private class FakeSessionStorage : SessionStorage {
        val session = MutableStateFlow<UserSession?>(null)

        fun signIn(googleId: String) {
            session.value = UserSession(googleId, "Test", "test@example.com", null)
        }

        override fun observeSession(): Flow<UserSession?> = session

        override suspend fun save(session: UserSession): EmptyResult<DataError.Local> {
            this.session.value = session
            return Result.Success(Unit)
        }

        override suspend fun clear(): EmptyResult<DataError.Local> {
            session.value = null
            return Result.Success(Unit)
        }
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            TransactionsDatabase::class.java
        ).build()
        sessionStorage = FakeSessionStorage()
        payees = RoomPayeeDataSource(
            database = database,
            dao = database.payeeDao(),
            identifiers = database.payeeIdentifierDao(),
            activeAccount = ActiveAccountProvider(sessionStorage)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun savingAMappingCreatesThePayeeAndTheIdentifierThatFindsItAgain() = runTest {
        sessionStorage.signIn(OWNER_A)
        val categoryId = insertCategory(OWNER_A, "Food")

        val payeeId = (
            payees.saveMapping("Blinkit", "BLINKIT", "Groceries app", categoryId) as Result.Success
            ).data

        val found = (payees.findByNormalizedName("BLINKIT") as Result.Success).data
        assertThat(found).isNotNull()
        assertThat(found!!.id).isEqualTo(payeeId)
        assertThat(found.alias).isEqualTo("Groceries app")
        assertThat(payees.observeByIdentifier().first().keys.toList()).containsExactly("BLINKIT")
    }

    @Test
    fun savingTheSameNameAgainEditsThePayeeRatherThanCreatingASecondOne() = runTest {
        sessionStorage.signIn(OWNER_A)
        val food = insertCategory(OWNER_A, "Food")
        val rent = insertCategory(OWNER_A, "Rent")

        val first = (payees.saveMapping("Blinkit", "BLINKIT", "Groceries app", food) as Result.Success).data
        val second = (payees.saveMapping("Blinkit", "BLINKIT", "Blinkit", rent) as Result.Success).data

        assertThat(second).isEqualTo(first)
        val byName = payees.observeByIdentifier().first()
        assertThat(byName.keys.toList()).containsExactly("BLINKIT")
        assertThat(byName.getValue("BLINKIT").alias).isEqualTo("Blinkit")
        assertThat(byName.getValue("BLINKIT").categoryId).isEqualTo(rent)
    }

    @Test
    fun twoAccountsCanEachOwnTheSameStatementNameWithoutSeeingEachOther() = runTest {
        sessionStorage.signIn(OWNER_A)
        payees.saveMapping("Blinkit", "BLINKIT", "Groceries app", insertCategory(OWNER_A, "Food"))

        sessionStorage.signIn(OWNER_B)
        assertThat((payees.findByNormalizedName("BLINKIT") as Result.Success).data).isNull()
        assertThat(payees.observeByIdentifier().first()).isEmpty()

        // The unique index is per account, so this is a new payee, not a conflict.
        payees.saveMapping("Blinkit", "BLINKIT", "Quick commerce", insertCategory(OWNER_B, "Food"))
        assertThat(
            (payees.findByNormalizedName("BLINKIT") as Result.Success).data!!.alias
        ).isEqualTo("Quick commerce")

        sessionStorage.signIn(OWNER_A)
        assertThat(
            (payees.findByNormalizedName("BLINKIT") as Result.Success).data!!.alias
        ).isEqualTo("Groceries app")
    }

    /**
     * Identifiers carry their own ownerId, so claiming legacy data has to move them too — a payee
     * handed to the signing-in account while its names stayed behind would silently stop
     * auto-mapping, and the only symptom would be a re-import asking to map a known payee again.
     */
    @Test
    fun claimingLegacyDataMovesIdentifiersAlongWithTheirPayees() = runTest {
        val categoryId = insertCategory(LEGACY_OWNER_ID, "Food")
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO payees (ownerId, alias, categoryId, isDeleted) " +
                "VALUES('$LEGACY_OWNER_ID','Groceries app',$categoryId,0)"
        )
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO payee_identifiers (ownerId, payeeId, rawName, normalizedName) " +
                "VALUES('$LEGACY_OWNER_ID',(SELECT id FROM payees WHERE ownerId='$LEGACY_OWNER_ID')," +
                "'Blinkit','BLINKIT')"
        )

        sessionStorage.signIn(OWNER_A)
        LegacyDataClaimer(database, database.legacyOwnershipDao()).claimFor(OWNER_A)

        val found = (payees.findByNormalizedName("BLINKIT") as Result.Success).data
        assertThat(found).isNotNull()
        assertThat(found!!.alias).isEqualTo("Groceries app")
    }

    @Test
    fun linkingAnUnmappedNameAddsItToTheExistingPayeeWithoutCreatingAnother() = runTest {
        sessionStorage.signIn(OWNER_A)
        val categoryId = insertCategory(OWNER_A, "Food")
        val payeeId = (
            payees.saveMapping("Blinkit", "BLINKIT", "Groceries app", categoryId) as Result.Success
            ).data

        payees.linkToPayee("Blinkit Commerce", "BLINKIT COMMERCE", payeeId)

        // Both names now answer to the one payee, and no second payee was created.
        assertThat((payees.findByNormalizedName("BLINKIT COMMERCE") as Result.Success).data!!.id)
            .isEqualTo(payeeId)
        assertThat(payees.observeAll().first().map { it.alias }).containsExactly("Groceries app")
        assertThat(payees.observeByIdentifier().first().keys.toList().sorted())
            .containsExactly("BLINKIT", "BLINKIT COMMERCE")
    }

    /**
     * The merge the whole phase is for: two payees the user has been treating as one. Every one
     * of the loser's identifiers and transactions must land on the winner, and the loser's row
     * must go — a leftover payee with no identifiers is unreachable but still counts in lists.
     */
    @Test
    fun mergingMovesEveryIdentifierAndTransactionOntoTheTargetAndDeletesTheSource() = runTest {
        sessionStorage.signIn(OWNER_A)
        val categoryId = insertCategory(OWNER_A, "Food")
        val keeper = (
            payees.saveMapping("Blinkit", "BLINKIT", "Groceries app", categoryId) as Result.Success
            ).data
        val doomed = (
            payees.saveMapping("Blinkit Ltd", "BLINKIT LTD", "Blinkit", categoryId) as Result.Success
            ).data
        // A second name already on the doomed payee, so the merge has to move more than one.
        payees.linkToPayee("Blinkit Pvt", "BLINKIT PVT", doomed)
        insertSession()
        insertTransaction("BLINKIT LTD", payeeId = doomed)
        insertTransaction("BLINKIT PVT", payeeId = doomed)

        payees.linkToPayee("Blinkit Ltd", "BLINKIT LTD", keeper)

        assertThat(payees.observeAll().first().map { it.id }).containsExactly(keeper)
        assertThat(payees.observeByIdentifier().first().values.map { it.id }.distinct())
            .containsExactly(keeper)
        assertThat(payees.observeByIdentifier().first().keys.toList().sorted())
            .containsExactly("BLINKIT", "BLINKIT LTD", "BLINKIT PVT")
        assertThat(payeeIdsOnTransactions()).containsExactly(keeper, keeper)
    }

    /**
     * Post-merge auto-mapping: a re-imported statement still printing the old spelling resolves
     * to the payee that absorbed it, which is what stops the merge from being undone by the next
     * upload.
     */
    @Test
    fun aMergedAwayNameStillAutoMapsStraightToTheSurvivingPayee() = runTest {
        sessionStorage.signIn(OWNER_A)
        val categoryId = insertCategory(OWNER_A, "Food")
        val keeper = (
            payees.saveMapping("Blinkit", "BLINKIT", "Groceries app", categoryId) as Result.Success
            ).data
        payees.saveMapping("Blinkit Ltd", "BLINKIT LTD", "Blinkit", categoryId)

        payees.linkToPayee("Blinkit Ltd", "BLINKIT LTD", keeper)

        val resolved = (payees.findByNormalizedName("BLINKIT LTD") as Result.Success).data!!
        assertThat(resolved.id).isEqualTo(keeper)
        assertThat(resolved.alias).isEqualTo("Groceries app")
    }

    @Test
    fun linkingANameToThePayeeThatAlreadyOwnsItChangesNothing() = runTest {
        sessionStorage.signIn(OWNER_A)
        val categoryId = insertCategory(OWNER_A, "Food")
        val payeeId = (
            payees.saveMapping("Blinkit", "BLINKIT", "Groceries app", categoryId) as Result.Success
            ).data

        payees.linkToPayee("Blinkit", "BLINKIT", payeeId)

        assertThat(payees.observeAll().first().map { it.id }).containsExactly(payeeId)
        assertThat(payees.observeByIdentifier().first().keys.toList()).containsExactly("BLINKIT")
    }

    @Test
    fun theLinkedNamesOfAPayeeAreReachableFromAnyOneOfThem() = runTest {
        sessionStorage.signIn(OWNER_A)
        val categoryId = insertCategory(OWNER_A, "Food")
        val payeeId = (
            payees.saveMapping("Blinkit", "BLINKIT", "Groceries app", categoryId) as Result.Success
            ).data
        payees.linkToPayee("Blinkit Ltd", "BLINKIT LTD", payeeId)

        // Asked from either name, the answer is the same set — that is what the filter chips show.
        assertThat(payees.observeLinkedIdentifiers("BLINKIT").first().map { it.normalizedName })
            .containsExactly("BLINKIT", "BLINKIT LTD")
        assertThat(payees.observeLinkedIdentifiers("BLINKIT LTD").first().map { it.rawName })
            .containsExactly("Blinkit", "Blinkit Ltd")
        // An unmapped name owns nothing, so the section has nothing to show.
        assertThat(payees.observeLinkedIdentifiers("UNKNOWN SHOP").first()).isEmpty()
    }

    @Test
    fun anAliasIsFoundWhicheverWayItIsCasedButOnlyWithinTheAccount() = runTest {
        sessionStorage.signIn(OWNER_A)
        payees.saveMapping("Blinkit", "BLINKIT", "Groceries app", insertCategory(OWNER_A, "Food"))

        assertThat((payees.findByAlias("groceries APP") as Result.Success).data).isNotNull()
        assertThat((payees.findByAlias("Groceries") as Result.Success).data).isNull()

        sessionStorage.signIn(OWNER_B)
        assertThat((payees.findByAlias("Groceries app") as Result.Success).data).isNull()
    }

    private suspend fun insertCategory(ownerId: String, name: String): Long =
        database.categoryDao().insert(CategoryEntity(ownerId = ownerId, name = name))

    private suspend fun insertSession() {
        database.sessionDao().insert(
            SessionEntity(
                id = SESSION_ID,
                ownerId = OWNER_A,
                fileName = "june.pdf",
                source = "PHONEPE",
                uploadedAtMillis = 0L,
                periodStartMillis = null,
                periodEndMillis = null,
                status = "PENDING"
            )
        )
    }

    private suspend fun insertTransaction(normalizedPayee: String, payeeId: Long) {
        database.transactionDao().insertAll(
            listOf(
                TransactionEntity(
                    ownerId = OWNER_A,
                    sessionId = SESSION_ID,
                    dateTimeUtcMillis = 0L,
                    rawPayee = normalizedPayee,
                    normalizedPayee = normalizedPayee,
                    amountPaise = 1_000L,
                    type = "DEBIT",
                    transactionRef = null,
                    utr = null,
                    payeeId = payeeId
                )
            )
        )
    }

    private suspend fun payeeIdsOnTransactions(): List<Long> =
        database.transactionDao().observeBySession(OWNER_A, SESSION_ID).first()
            .mapNotNull { it.payeeId }

    private companion object {
        const val OWNER_A = "google-sub-a"
        const val OWNER_B = "google-sub-b"
        const val SESSION_ID = 1L
    }
}
