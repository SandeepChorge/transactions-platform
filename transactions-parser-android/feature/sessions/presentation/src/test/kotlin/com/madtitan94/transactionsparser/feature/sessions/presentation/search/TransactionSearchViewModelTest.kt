package com.madtitan94.transactionsparser.feature.sessions.presentation.search

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.madtitan94.transactionsparser.core.domain.datasource.TransactionSearchLocalDataSource
import com.madtitan94.transactionsparser.core.domain.model.SEARCH_RESULT_LIMIT
import com.madtitan94.transactionsparser.core.domain.model.SearchQuery
import com.madtitan94.transactionsparser.core.domain.model.TransactionSearchPage
import com.madtitan94.transactionsparser.core.domain.model.TransactionSearchResult
import com.madtitan94.transactionsparser.core.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The search screen's own logic: what is asked, when, and what the screen says while it waits.
 *
 * A [StandardTestDispatcher] rather than the unconfined one used elsewhere in this project, because
 * the behaviour under test *is* the passage of time. Under `UnconfinedTestDispatcher` the debounce
 * would be skipped rather than exercised, and the one thing worth proving — that seven keystrokes
 * produce one query — would pass whether or not the debounce existed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionSearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    /**
     * Records every query it was asked, because the count is the assertion for the debounce and the
     * last one is the assertion for the switch: a stale query still delivering its rows is
     * indistinguishable from a fresh one unless the queries themselves are counted.
     */
    private class FakeSearch(
        page: TransactionSearchPage = TransactionSearchPage.Empty
    ) : TransactionSearchLocalDataSource {
        val asked = mutableListOf<SearchQuery>()
        val page = MutableStateFlow(page)

        override fun observeSearch(query: SearchQuery): Flow<TransactionSearchPage> {
            asked += query
            // Mirrors the real data source, which short-circuits a blank query rather than scanning.
            return if (query.isBlank) flowOf(TransactionSearchPage.Empty) else page
        }
    }

    private fun result(id: Long, label: String = "Swiggy") = TransactionSearchResult(
        id = id,
        dateTimeUtcMillis = 0L,
        label = label,
        statementName = "SWIGGY BANGALORE",
        normalizedName = "swiggy bangalore",
        categoryName = "Food",
        amountPaise = 125_000L,
        type = TransactionType.DEBIT,
        isExcluded = false,
        isDuplicate = false
    )

    private fun type(viewModel: TransactionSearchViewModel, text: String) {
        text.indices.forEach { index ->
            viewModel.onAction(TransactionSearchAction.OnQueryChange(text.substring(0, index + 1)))
        }
    }

    @Test
    fun `a word typed at speed produces one query, not one per keystroke`() = runTest(dispatcher) {
        val search = FakeSearch()
        val viewModel = TransactionSearchViewModel(search)

        viewModel.state.test {
            awaitItem()
            type(viewModel, "swiggy")
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        // The empty initial value is asked for too — what matters is that the six characters of
        // "swiggy" collapse into the one query for the whole word.
        assertThat(search.asked.map { it.text }.filter { it.isNotEmpty() }).isEqualTo(listOf("swiggy"))
    }

    @Test
    fun `a pause between words asks twice`() = runTest(dispatcher) {
        val search = FakeSearch()
        val viewModel = TransactionSearchViewModel(search)

        viewModel.state.test {
            awaitItem()
            type(viewModel, "swi")
            advanceTimeBy(500)
            type(viewModel, "swiggy")
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(search.asked.map { it.text }.filter { it.isNotEmpty() })
            .isEqualTo(listOf("swi", "swiggy"))
    }

    @Test
    fun `a one-character query is never sent to the database`() = runTest(dispatcher) {
        val search = FakeSearch()
        val viewModel = TransactionSearchViewModel(search)

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(TransactionSearchAction.OnQueryChange("s"))
            advanceUntilIdle()
            // Idle rather than empty-result: the screen offers to explain what can be searched
            // instead of reporting that a search the user has not finished typing found nothing.
            assertThat(expectMostRecentItem().isIdle).isTrue()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(search.asked.count { !it.isBlank }).isEqualTo(0)
    }

    @Test
    fun `the screen says it is searching while the query it is showing is stale`() =
        runTest(dispatcher) {
            val search = FakeSearch(TransactionSearchPage(listOf(result(1L)), totalCount = 1))
            val viewModel = TransactionSearchViewModel(search)

            viewModel.state.test {
                awaitItem()
                viewModel.onAction(TransactionSearchAction.OnQueryChange("swiggy"))
                advanceUntilIdle()
                assertThat(expectMostRecentItem().isSearching).isFalse()

                // Typing on makes what is on screen an answer to a question the user has moved past.
                // `runCurrent` lets the combine recompute without letting the debounce elapse, which
                // is exactly the window this state exists to describe.
                viewModel.onAction(TransactionSearchAction.OnQueryChange("swiggy b"))
                runCurrent()
                assertThat(expectMostRecentItem().isSearching).isTrue()

                advanceUntilIdle()
                assertThat(expectMostRecentItem().isSearching).isFalse()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `stale rows stay on screen while the next query runs`() = runTest(dispatcher) {
        // Blanking the list on every keystroke makes a search that is working look like one that
        // keeps failing. The spinner already says the rows are being replaced.
        val search = FakeSearch(TransactionSearchPage(listOf(result(1L)), totalCount = 1))
        val viewModel = TransactionSearchViewModel(search)

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(TransactionSearchAction.OnQueryChange("swiggy"))
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onAction(TransactionSearchAction.OnQueryChange("swiggy b"))
            runCurrent()
            val whileSearching = expectMostRecentItem()
            assertThat(whileSearching.isSearching).isTrue()
            assertThat(whileSearching.rows.size).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no matches is reported as no matches, not as an empty screen`() = runTest(dispatcher) {
        val search = FakeSearch(TransactionSearchPage.Empty)
        val viewModel = TransactionSearchViewModel(search)

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(TransactionSearchAction.OnQueryChange("nothing here"))
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.isEmptyResult).isTrue()
            assertThat(state.isIdle).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a capped result set reports the count it is not showing`() = runTest(dispatcher) {
        // Counting the rows would report the limit: an account with 512 matches and one with 200
        // would read identically, and the user would have no reason to narrow the search.
        val rows = (1L..SEARCH_RESULT_LIMIT).map { result(it) }
        val search = FakeSearch(TransactionSearchPage(rows, totalCount = 512))
        val viewModel = TransactionSearchViewModel(search)

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(TransactionSearchAction.OnQueryChange("a".repeat(2)))
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.isTruncated).isTrue()
            assertThat(state.totalCount).isEqualTo(512)
            assertThat(state.rows.size).isEqualTo(SEARCH_RESULT_LIMIT)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing returns the screen to its idle state`() = runTest(dispatcher) {
        val search = FakeSearch(TransactionSearchPage(listOf(result(1L)), totalCount = 1))
        val viewModel = TransactionSearchViewModel(search)

        viewModel.state.test {
            awaitItem()
            viewModel.onAction(TransactionSearchAction.OnQueryChange("swiggy"))
            advanceUntilIdle()
            expectMostRecentItem()

            viewModel.onAction(TransactionSearchAction.OnClearQuery)
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.query).isEqualTo("")
            assertThat(state.isIdle).isTrue()
            assertThat(state.isSearching).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an amount typed as rupees reaches the data source as a paise window`() =
        runTest(dispatcher) {
            // The parsing is tested on its own; what this pins is that the ViewModel hands the
            // parsed query down rather than the raw text, which is the only way the amount branch
            // of the query ever fires.
            val search = FakeSearch()
            val viewModel = TransactionSearchViewModel(search)

            viewModel.state.test {
                awaitItem()
                viewModel.onAction(TransactionSearchAction.OnQueryChange("1250"))
                advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }

            val asked = search.asked.last { !it.isBlank }
            assertThat(asked.amountFromPaise).isEqualTo(125_000L)
            assertThat(asked.amountToPaise).isEqualTo(125_099L)
        }
}
