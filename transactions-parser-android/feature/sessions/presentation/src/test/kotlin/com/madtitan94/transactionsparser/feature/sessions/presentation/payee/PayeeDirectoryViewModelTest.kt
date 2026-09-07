package com.madtitan94.transactionsparser.feature.sessions.presentation.payee

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.madtitan94.transactionsparser.core.domain.datasource.PayeeLocalDataSource
import com.madtitan94.transactionsparser.core.domain.model.Payee
import com.madtitan94.transactionsparser.core.domain.model.PayeeDirectoryEntry
import com.madtitan94.transactionsparser.core.domain.model.PayeeIdentifier
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The directory's search and filtering are in the ViewModel rather than in SQL, so this is where
 * they are provable. The DAO's own correctness — merged payees counted once, unclaimed names
 * listed — is covered by `PayeeDirectoryQueryTest` against real Room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PayeeDirectoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private class FakePayeeDataSource : PayeeLocalDataSource {
        val directory = MutableStateFlow<List<PayeeDirectoryEntry>>(emptyList())

        override fun observeDirectory(): Flow<List<PayeeDirectoryEntry>> = directory

        override fun observeByIdentifier(): Flow<Map<String, Payee>> = MutableStateFlow(emptyMap())
        override fun observeByNormalizedName(normalizedName: String): Flow<Payee?> = MutableStateFlow(null)
        override suspend fun findByNormalizedName(normalizedName: String) = Result.Success(null)
        override fun observeAll(): Flow<List<Payee>> = MutableStateFlow(emptyList())
        override fun observeLinkedIdentifiers(normalizedName: String): Flow<List<PayeeIdentifier>> =
            MutableStateFlow(emptyList())

        override suspend fun findByAlias(alias: String) = Result.Success(null)
        override suspend fun saveMapping(
            rawName: String,
            normalizedName: String,
            alias: String,
            categoryId: Long
        ): Result<Long, DataError.Local> = Result.Success(0L)

        override suspend fun linkToPayee(
            rawName: String,
            normalizedName: String,
            targetPayeeId: Long
        ): EmptyResult<DataError.Local> = Result.Success(Unit)
    }

    private fun entry(
        payeeId: Long?,
        label: String,
        statementName: String = label,
        categoryName: String? = "Food",
        identifierCount: Int = 1,
        totalPaise: Long = 1_000_00,
        transactionCount: Int = 1
    ) = PayeeDirectoryEntry(
        payeeId = payeeId,
        label = label,
        statementName = statementName,
        normalizedName = statementName,
        categoryName = categoryName,
        identifierCount = identifierCount,
        totalPaise = totalPaise,
        transactionCount = transactionCount
    )

    private fun viewModel(entries: List<PayeeDirectoryEntry>): PayeeDirectoryViewModel {
        val payees = FakePayeeDataSource()
        payees.directory.value = entries
        return PayeeDirectoryViewModel(payees)
    }

    @Test
    fun `an empty account is a different state from a search that found nothing`() = runTest {
        viewModel(emptyList()).state.test {
            val state = expectMostRecentItem()
            assertThat(state.isEmptyAccount).isTrue()
            assertThat(state.isEmptyResult).isFalse()
        }
    }

    @Test
    fun `a search with no matches is not mistaken for an empty account`() = runTest {
        val vm = viewModel(listOf(entry(1L, "Corner Shop")))
        vm.onAction(PayeeDirectoryAction.OnQueryChange("nothing like this"))

        vm.state.test {
            val state = expectMostRecentItem()
            assertThat(state.isEmptyAccount).isFalse()
            assertThat(state.isEmptyResult).isTrue()
        }
    }

    /**
     * The point of matching on both names: a payee renamed "Dinner" is still the one the statement
     * calls "SWIGGY", and someone searching for either has to find them.
     */
    @Test
    fun `a renamed payee is found by the name the statement printed`() = runTest {
        val vm = viewModel(listOf(entry(1L, "Dinner", statementName = "SWIGGY BANGALORE")))
        vm.onAction(PayeeDirectoryAction.OnQueryChange("swiggy"))

        vm.state.test {
            assertThat(expectMostRecentItem().rows.map { it.displayName }).containsExactly("Dinner")
        }
    }

    @Test
    fun `search ignores case and surrounding whitespace`() = runTest {
        val vm = viewModel(listOf(entry(1L, "Corner Shop"), entry(2L, "Petrol")))
        vm.onAction(PayeeDirectoryAction.OnQueryChange("  CORNER "))

        vm.state.test {
            assertThat(expectMostRecentItem().rows.map { it.displayName }).containsExactly("Corner Shop")
        }
    }

    @Test
    fun `the unmapped filter keeps only the names nobody has claimed`() = runTest {
        val vm = viewModel(
            listOf(
                entry(1L, "Corner Shop"),
                entry(null, "RANDOM UPI STRING", categoryName = null, identifierCount = 0)
            )
        )
        vm.onAction(PayeeDirectoryAction.OnFilterChange(PayeeDirectoryFilter.UNMAPPED))

        vm.state.test {
            val state = expectMostRecentItem()
            assertThat(state.rows.map { it.displayName }).containsExactly("RANDOM UPI STRING")
            assertThat(state.rows.single().isUnmapped).isTrue()
        }
    }

    /** A chip that counted only what the chip itself shows would read 0 the moment it was tapped. */
    @Test
    fun `the chip counts describe the whole directory rather than the filtered view`() = runTest {
        val vm = viewModel(
            listOf(
                entry(1L, "Corner Shop"),
                entry(2L, "Petrol"),
                entry(null, "RANDOM UPI STRING", categoryName = null, identifierCount = 0)
            )
        )
        vm.onAction(PayeeDirectoryAction.OnFilterChange(PayeeDirectoryFilter.UNMAPPED))
        vm.onAction(PayeeDirectoryAction.OnQueryChange("random"))

        vm.state.test {
            val state = expectMostRecentItem()
            assertThat(state.rows).containsExactly(state.rows.single())
            assertThat(state.totalCount).isEqualTo(3)
            assertThat(state.unmappedCount).isEqualTo(1)
            assertThat(state.mappedCount).isEqualTo(2)
        }
    }

    @Test
    fun `the search and the filter narrow together rather than replacing each other`() = runTest {
        val vm = viewModel(
            listOf(
                entry(1L, "Corner Shop"),
                entry(null, "CORNER STALL", categoryName = null, identifierCount = 0)
            )
        )
        vm.onAction(PayeeDirectoryAction.OnQueryChange("corner"))
        vm.onAction(PayeeDirectoryAction.OnFilterChange(PayeeDirectoryFilter.MAPPED))

        vm.state.test {
            assertThat(expectMostRecentItem().rows.map { it.displayName }).containsExactly("Corner Shop")
        }
    }

    /** One name is just this payee's name — only the extras earn a line under the title. */
    @Test
    fun `only the names beyond the first are offered to the row`() = runTest {
        val vm = viewModel(
            listOf(
                entry(1L, "Corner Shop", identifierCount = 3),
                entry(2L, "Petrol", identifierCount = 1)
            )
        )

        vm.state.test {
            val rows = expectMostRecentItem().rows
            assertThat(rows.first { it.displayName == "Corner Shop" }.otherNameCount).isEqualTo(2)
            assertThat(rows.first { it.displayName == "Petrol" }.otherNameCount).isNull()
        }
    }

    @Test
    fun `a payee with nothing countable is listed at zero rather than dropped`() = runTest {
        val vm = viewModel(listOf(entry(1L, "Refunded Shop", totalPaise = 0L, transactionCount = 0)))

        vm.state.test {
            val row = expectMostRecentItem().rows.single()
            assertThat(row.displayName).isEqualTo("Refunded Shop")
            assertThat(row.totalLabel).isEqualTo("₹0")
            assertThat(row.transactionCount).isEqualTo(0)
        }
    }
}
