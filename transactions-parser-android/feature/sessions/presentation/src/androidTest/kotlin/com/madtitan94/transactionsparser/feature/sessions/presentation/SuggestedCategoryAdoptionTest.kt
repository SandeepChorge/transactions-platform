package com.madtitan94.transactionsparser.feature.sessions.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.madtitan94.transactionsparser.core.domain.analytics.AnalyticsEvent
import com.madtitan94.transactionsparser.core.domain.analytics.AnalyticsParams
import com.madtitan94.transactionsparser.core.domain.analytics.AnalyticsTracker
import com.madtitan94.transactionsparser.core.domain.catalog.AdoptSuggestedCategory
import com.madtitan94.transactionsparser.core.domain.catalog.CategoryCatalog
import com.madtitan94.transactionsparser.core.domain.catalog.SuggestedCategory
import com.madtitan94.transactionsparser.core.domain.catalog.SuggestedCategorySource
import com.madtitan94.transactionsparser.core.domain.datasource.CategoryLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.PayeeLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.SessionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.datasource.TransactionLocalDataSource
import com.madtitan94.transactionsparser.core.domain.model.Category
import com.madtitan94.transactionsparser.core.domain.model.Payee
import com.madtitan94.transactionsparser.core.domain.model.PayeeDirectoryEntry
import com.madtitan94.transactionsparser.core.domain.model.PayeeIdentifier
import com.madtitan94.transactionsparser.core.domain.model.PayeeTotals
import com.madtitan94.transactionsparser.core.domain.model.PeriodTotal
import com.madtitan94.transactionsparser.core.domain.model.SessionStatus
import com.madtitan94.transactionsparser.core.domain.model.SessionSummary
import com.madtitan94.transactionsparser.core.domain.model.StatementSession
import com.madtitan94.transactionsparser.core.domain.model.StatementSource
import com.madtitan94.transactionsparser.core.domain.model.Transaction
import com.madtitan94.transactionsparser.core.domain.model.TransactionExportRow
import com.madtitan94.transactionsparser.core.domain.model.TransactionKey
import com.madtitan94.transactionsparser.core.domain.model.TransactionType
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result
import com.madtitan94.transactionsparser.feature.sessions.presentation.detail.SessionDetailAction
import com.madtitan94.transactionsparser.feature.sessions.presentation.detail.SessionDetailViewModel
import com.madtitan94.transactionsparser.feature.sessions.presentation.payee.PayeeDetailAction
import com.madtitan94.transactionsparser.feature.sessions.presentation.payee.PayeeDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The mapping and payee-detail ViewModels read their route through Android's Bundle, so this sits
 * with the other instrumented mapping tests rather than on the JVM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SuggestedCategoryAdoptionTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun mappingShowsSuggestionsOnlyWhileTheAccountHasNone() = runTest {
        val categories = FakeCategories()
        val viewModel = session(categories)

        assertThat(viewModel.state.value.showSuggestions).isTrue()

        categories.insert("Rent")

        assertThat(viewModel.state.value.showSuggestions).isFalse()
    }

    @Test
    fun mappingChipAdoptsTheCategorySelectsItAndRecordsTheKey() = runTest {
        val categories = FakeCategories()
        val analytics = RecordingAnalytics()
        val viewModel = session(categories, analytics)

        viewModel.onAction(SessionDetailAction.OnAdoptSuggestion(KEY, "Food"))

        assertThat(categories.live.value.map { it.name }).isEqualTo(listOf("Food"))
        assertThat(viewModel.state.value.showSuggestions).isFalse()
        assertThat(viewModel.state.value.groups.single().selectedCategoryId).isEqualTo(1L)
        val event = analytics.events.single() as AnalyticsEvent.SuggestedCategoryAdopted
        assertThat(event.categoryKey).isEqualTo("food")
        assertThat(event.screen).isEqualTo(AnalyticsParams.SCREEN_MAPPING)
        assertThat(event.addedAll).isFalse()
        assertThat(event.catalogVersion).isEqualTo(3)
        assertThat(event.params.values.filterIsInstance<String>().none { it == "Food" }).isTrue()
    }

    @Test
    fun mappingAddAllDoesNotSelectACategoryAndDoesNotSendDisplayNames() = runTest {
        val categories = FakeCategories()
        val analytics = RecordingAnalytics()
        val viewModel = session(categories, analytics)

        viewModel.onAction(SessionDetailAction.OnAdoptAllSuggestions)

        assertThat(categories.live.value.map { it.name }).isEqualTo(listOf("Food", "Groceries"))
        assertThat(viewModel.state.value.groups.single().selectedCategoryId).isNull()
        assertThat(analytics.events.map { (it as AnalyticsEvent.SuggestedCategoryAdopted).addedAll })
            .isEqualTo(listOf(true, true))
        assertThat(analytics.events.flatMap { it.params.values.filterIsInstance<String>() })
            .isEqualTo(listOf("food", "mapping", "groceries", "mapping"))
    }

    @Test
    fun categoryTypedOnTheMappingFormIsNotAnAdoptionEvent() = runTest {
        val categories = FakeCategories()
        val analytics = RecordingAnalytics()
        val viewModel = session(categories, analytics)

        viewModel.onAction(SessionDetailAction.OnAddCategoryClick(KEY))
        viewModel.onAction(SessionDetailAction.OnNewCategoryNameChange("School fees"))
        viewModel.onAction(SessionDetailAction.OnCreateCategory)

        assertThat(categories.live.value.map { it.name }).isEqualTo(listOf("School fees"))
        assertThat(viewModel.state.value.groups.single().selectedCategoryId).isEqualTo(1L)
        assertThat(analytics.events).isEmpty()
    }

    @Test
    fun payeeDetailShowsSuggestionsOnlyWhileTheAccountHasNone() = runTest {
        val categories = FakeCategories()
        val viewModel = payee(categories)

        assertThat(viewModel.state.value.showSuggestions).isTrue()

        categories.insert("Rent")

        assertThat(viewModel.state.value.showSuggestions).isFalse()
    }

    @Test
    fun payeeDetailChipAdoptsTheCategoryAndRecordsTheKey() = runTest {
        val categories = FakeCategories()
        val analytics = RecordingAnalytics()
        val viewModel = payee(categories, analytics)

        viewModel.onAction(PayeeDetailAction.OnAdoptSuggestion("Groceries"))

        assertThat(viewModel.state.value.selectedCategoryId).isEqualTo(1L)
        assertThat(viewModel.state.value.showSuggestions).isFalse()
        val event = analytics.events.single() as AnalyticsEvent.SuggestedCategoryAdopted
        assertThat(event.categoryKey).isEqualTo("groceries")
        assertThat(event.screen).isEqualTo(AnalyticsParams.SCREEN_PAYEE_DETAIL)
        assertThat(event.addedAll).isFalse()
    }

    @Test
    fun payeeDetailCanCreateATypedCategoryWithoutAnAdoptionEvent() = runTest {
        val categories = FakeCategories()
        val analytics = RecordingAnalytics()
        val viewModel = payee(categories, analytics)

        viewModel.onAction(PayeeDetailAction.OnAddCategoryClick)
        viewModel.onAction(PayeeDetailAction.OnNewCategoryNameChange("School fees"))
        viewModel.onAction(PayeeDetailAction.OnCreateCategory)

        assertThat(viewModel.state.value.creatingCategory).isFalse()
        assertThat(viewModel.state.value.selectedCategoryId).isEqualTo(1L)
        assertThat(categories.live.value.map { it.name }).isEqualTo(listOf("School fees"))
        assertThat(analytics.events).isEmpty()
    }

    private fun session(
        categories: FakeCategories,
        analytics: RecordingAnalytics = RecordingAnalytics()
    ) = SessionDetailViewModel(
        SavedStateHandle(mapOf("sessionId" to 1L)),
        FakeSessions(),
        FakeTransactions(),
        FakePayees(),
        categories,
        FakeCatalog,
        AdoptSuggestedCategory(categories),
        analytics
    )

    private fun payee(
        categories: FakeCategories,
        analytics: RecordingAnalytics = RecordingAnalytics()
    ) = PayeeDetailViewModel(
        SavedStateHandle(mapOf("normalizedPayee" to KEY, "rawPayee" to RAW)),
        FakeTransactions(),
        FakePayees(),
        categories,
        FakeCatalog,
        AdoptSuggestedCategory(categories),
        analytics
    )

    private class RecordingAnalytics : AnalyticsTracker {
        val events = mutableListOf<AnalyticsEvent>()
        override fun track(event: AnalyticsEvent) {
            events += event
        }
    }

    private object FakeCatalog : SuggestedCategorySource {
        override fun observe(): Flow<CategoryCatalog> = MutableStateFlow(sampleCatalog)
    }

    private class FakeCategories : CategoryLocalDataSource {
        val live = MutableStateFlow<List<Category>>(emptyList())
        private var nextId = 1L

        override fun observeAll(): Flow<List<Category>> = live
        override fun observeDeleted(): Flow<List<Category>> = flowOf(emptyList())
        override fun observeLinkedPayeeCounts(): Flow<Map<Long, Int>> = flowOf(emptyMap())

        override suspend fun insert(name: String): Result<Long, DataError.Local> {
            val id = nextId++
            live.value += Category(id = id, name = name)
            return Result.Success(id)
        }

        override suspend fun rename(id: Long, name: String): EmptyResult<DataError.Local> = Result.Success(Unit)
        override suspend fun delete(id: Long): EmptyResult<DataError.Local> = Result.Success(Unit)
        override suspend fun restore(id: Long): EmptyResult<DataError.Local> = Result.Success(Unit)
        override suspend fun linkedPayeeCount(id: Long): Result<Int, DataError.Local> = Result.Success(0)
    }

    private class FakePayees : PayeeLocalDataSource {
        override fun observeByIdentifier(): Flow<Map<String, Payee>> = flowOf(emptyMap())
        override fun observeByNormalizedName(normalizedName: String): Flow<Payee?> = flowOf(null)
        override suspend fun findByNormalizedName(normalizedName: String): Result<Payee?, DataError.Local> =
            Result.Success(null)
        override fun observeAll(): Flow<List<Payee>> = flowOf(emptyList())
        override fun observeDirectory(): Flow<List<PayeeDirectoryEntry>> = flowOf(emptyList())
        override fun observeLinkedIdentifiers(normalizedName: String): Flow<List<PayeeIdentifier>> =
            flowOf(emptyList())
        override suspend fun findByAlias(alias: String): Result<Payee?, DataError.Local> = Result.Success(null)
        override suspend fun saveMapping(
            rawName: String,
            normalizedName: String,
            alias: String,
            categoryId: Long
        ): Result<Long, DataError.Local> = Result.Success(1L)
        override suspend fun linkToPayee(
            rawName: String,
            normalizedName: String,
            targetPayeeId: Long
        ): EmptyResult<DataError.Local> = Result.Success(Unit)
    }

    private class FakeSessions : SessionLocalDataSource {
        override fun observeSummaries(status: SessionStatus): Flow<List<SessionSummary>> = flowOf(emptyList())
        override suspend fun getById(id: Long): Result<StatementSession?, DataError.Local> = Result.Success(
            StatementSession(id, "statement.pdf", StatementSource.PHONEPE, 0L, null, null, SessionStatus.PENDING)
        )
        override suspend fun insert(session: StatementSession): Result<Long, DataError.Local> = Result.Success(1L)
        override suspend fun updateStatus(id: Long, status: SessionStatus): EmptyResult<DataError.Local> =
            Result.Success(Unit)
    }

    private class FakeTransactions : TransactionLocalDataSource {
        override fun observeBySession(sessionId: Long): Flow<List<Transaction>> = flowOf(
            listOf(Transaction(1L, sessionId, 0L, RAW, KEY, 100L, TransactionType.DEBIT, null, null, null))
        )
        override fun observePagedByPayee(normalizedPayee: String, includeLinkedNames: Boolean) =
            flowOf(PagingData.empty<Transaction>())
        override fun observePayeeTotals(normalizedPayee: String, includeLinkedNames: Boolean) = flowOf(PayeeTotals())
        override fun observePayeeDayTotals(normalizedPayee: String, includeLinkedNames: Boolean) =
            flowOf(emptyList<PeriodTotal>())
        override fun observePayeeMonthTotals(normalizedPayee: String, includeLinkedNames: Boolean) =
            flowOf(emptyList<PeriodTotal>())
        override suspend fun insertAll(transactions: List<Transaction>): EmptyResult<DataError.Local> =
            Result.Success(Unit)
        override suspend fun findDuplicateKeys(candidates: List<Transaction>) =
            Result.Success(emptyList<TransactionKey>())
        override suspend fun setExcluded(id: Long, isExcluded: Boolean): EmptyResult<DataError.Local> =
            Result.Success(Unit)
        override suspend fun setDuplicatesExcluded(
            sessionId: Long,
            normalizedPayee: String,
            isExcluded: Boolean
        ): EmptyResult<DataError.Local> = Result.Success(Unit)
        override suspend fun assignPayee(
            sessionId: Long,
            normalizedPayee: String,
            payeeId: Long
        ): EmptyResult<DataError.Local> = Result.Success(Unit)
        override suspend fun unmappedCount(sessionId: Long): Result<Int, DataError.Local> = Result.Success(1)
        override suspend fun exportRows(): Result<List<TransactionExportRow>, DataError.Local> =
            Result.Success(emptyList())
    }

    private companion object {
        const val RAW = "Corner SHOP"
        const val KEY = "CORNER SHOP"
        val sampleCatalog = CategoryCatalog(
            version = 3,
            categories = listOf(
                SuggestedCategory(key = "food", name = "Food", sortOrder = 0),
                SuggestedCategory(key = "groceries", name = "Groceries", sortOrder = 1)
            )
        )
    }
}
