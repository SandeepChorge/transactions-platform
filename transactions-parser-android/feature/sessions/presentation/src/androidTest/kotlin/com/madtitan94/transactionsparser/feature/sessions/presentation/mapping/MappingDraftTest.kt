package com.madtitan94.transactionsparser.feature.sessions.presentation.mapping

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.madtitan94.transactionsparser.core.domain.analytics.NoOpAnalyticsTracker
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
import com.madtitan94.transactionsparser.core.domain.util.Result
import com.madtitan94.transactionsparser.feature.sessions.presentation.detail.SessionDetailAction
import com.madtitan94.transactionsparser.feature.sessions.presentation.detail.SessionDetailViewModel
import com.madtitan94.transactionsparser.feature.sessions.presentation.payee.PayeeDetailAction
import com.madtitan94.transactionsparser.feature.sessions.presentation.payee.PayeeDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** The same edit contract must hold in both mapping entry points, including retained ViewModels. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class MappingDraftTest(private val detail: Boolean) {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private class Form(detail: Boolean, val payees: FakePayees, categories: FakeCategories) {
        private val session = if (!detail) SessionDetailViewModel(
            SavedStateHandle(mapOf("sessionId" to 1L)), FakeSessions(), FakeTransactions(),
            payees, categories, NoOpAnalyticsTracker
        ) else null
        private val payee = if (detail) PayeeDetailViewModel(
            SavedStateHandle(mapOf("normalizedPayee" to KEY, "rawPayee" to RAW)),
            FakeTransactions(), payees, categories
        ) else null
        val alias get() = session?.state?.value?.groups?.single()?.aliasInput ?: payee!!.state.value.aliasInput
        val category get() = if (session != null) session.state.value.groups.single().selectedCategoryId else payee!!.state.value.selectedCategoryId
        val canSave get() = session?.state?.value?.groups?.single()?.canSave ?: payee!!.state.value.canSave
        val hasPrompt get() = (session?.state?.value?.mergePrompt ?: payee?.state?.value?.mergePrompt) != null
        fun name(value: String) { session?.onAction(SessionDetailAction.OnAliasChange(KEY, value)); payee?.onAction(PayeeDetailAction.OnAliasChange(value)) }
        fun category(value: Long) { session?.onAction(SessionDetailAction.OnCategorySelect(KEY, value)); payee?.onAction(PayeeDetailAction.OnCategorySelect(value)) }
        fun leave() { session?.onAction(SessionDetailAction.OnLeaveMapping); payee?.onAction(PayeeDetailAction.OnLeaveMapping) }
        fun save() { session?.onAction(SessionDetailAction.OnSaveClick(KEY)); payee?.onAction(PayeeDetailAction.OnSaveMapping) }
    }

    @Test
    fun parsedNameStartsFilledButCategoryStillRequiresAChoice() = runTest {
        val form = Form(detail, FakePayees(), FakeCategories())
        assertThat(form.alias).isEqualTo(RAW)
        assertThat(form.category).isNull()
        assertThat(form.canSave).isFalse()
        form.category(7L)
        assertThat(form.canSave).isTrue()
        form.save()
        assertThat(form.payees.saved).isEqualTo(Payee(1L, RAW, 7L))
    }

    @Test
    fun clearSurvivesCategoryChangesAndDataEmissionsUntilLeaving() = runTest {
        val categories = FakeCategories()
        val form = Form(detail, FakePayees(), categories)
        form.name("")
        form.category(7L)
        categories.items.value += Category(8L, "Travel")
        assertThat(form.alias).isEqualTo("")
        assertThat(form.category).isEqualTo(7L)
        assertThat(form.canSave).isFalse()
        form.save()
        assertThat(form.payees.saved).isNull()
        form.leave()
        assertThat(form.alias).isEqualTo(RAW)
        assertThat(form.category).isNull()
    }

    @Test
    fun unsavedAliasIsDiscardedButSavedAliasSurvivesTheNextVisit() = runTest {
        val form = Form(detail, FakePayees(), FakeCategories())
        form.name("Custom name")
        form.category(7L)
        form.leave()
        assertThat(form.alias).isEqualTo(RAW)
        assertThat(form.category).isNull()
        form.name("Custom name")
        form.category(7L)
        form.save()
        form.leave()
        assertThat(form.alias).isEqualTo("Custom name")
        assertThat(form.category).isEqualTo(7L)
    }

    @Test
    fun previousMappingsRetainTheirAliasAndCategory() = runTest {
        val payees = FakePayees().apply { known.value = Payee(1L, "Saved name", 7L) }
        val form = Form(detail, payees, FakeCategories())
        assertThat(form.alias).isEqualTo("Saved name")
        assertThat(form.category).isEqualTo(7L)
        form.name("")
        form.leave()
        assertThat(form.alias).isEqualTo("Saved name")
        assertThat(form.category).isEqualTo(7L)
    }

    @Test
    fun defaultNameCollisionStillRequiresConfirmationAndLeavingDismissesIt() = runTest {
        val payees = FakePayees().apply { aliasOwner = Payee(2L, RAW, 7L) }
        val form = Form(detail, payees, FakeCategories())
        form.category(7L)
        form.save()
        assertThat(form.hasPrompt).isTrue()
        assertThat(payees.saved).isNull()
        form.leave()
        assertThat(form.hasPrompt).isFalse()
    }

    private class FakePayees : PayeeLocalDataSource {
        val known = MutableStateFlow<Payee?>(null)
        var saved: Payee? = null
        var aliasOwner: Payee? = null
        override fun observeByIdentifier() = known.map { it?.let { mapOf(KEY to it) } ?: emptyMap() }
        override fun observeByNormalizedName(normalizedName: String) = known
        override suspend fun findByNormalizedName(normalizedName: String) = Result.Success(known.value)
        override fun observeAll() = known.map { listOfNotNull(it) }
        override fun observeDirectory() = flowOf(emptyList<PayeeDirectoryEntry>())
        override fun observeLinkedIdentifiers(normalizedName: String) = flowOf(emptyList<PayeeIdentifier>())
        override suspend fun findByAlias(alias: String) = Result.Success(aliasOwner)
        override suspend fun saveMapping(rawName: String, normalizedName: String, alias: String, categoryId: Long): Result.Success<Long> {
            saved = Payee(1L, alias, categoryId)
            known.value = saved
            return Result.Success(1L)
        }
        override suspend fun linkToPayee(rawName: String, normalizedName: String, targetPayeeId: Long) = Result.Success(Unit)
    }

    private class FakeCategories : CategoryLocalDataSource {
        val items = MutableStateFlow(listOf(Category(7L, "Food")))
        override fun observeAll() = items
        override fun observeDeleted() = flowOf(emptyList<Category>())
        override fun observeLinkedPayeeCounts() = flowOf(emptyMap<Long, Int>())
        override suspend fun insert(name: String) = Result.Success(8L)
        override suspend fun rename(id: Long, name: String) = Result.Success(Unit)
        override suspend fun delete(id: Long) = Result.Success(Unit)
        override suspend fun restore(id: Long) = Result.Success(Unit)
        override suspend fun linkedPayeeCount(id: Long) = Result.Success(0)
    }

    private class FakeSessions : SessionLocalDataSource {
        override fun observeSummaries(status: SessionStatus) = flowOf(emptyList<SessionSummary>())
        override suspend fun getById(id: Long) = Result.Success(StatementSession(id, "statement.pdf", StatementSource.PHONEPE, 0L, null, null, SessionStatus.PENDING))
        override suspend fun insert(session: StatementSession) = Result.Success(1L)
        override suspend fun updateStatus(id: Long, status: SessionStatus) = Result.Success(Unit)
    }

    private class FakeTransactions : TransactionLocalDataSource {
        override fun observeBySession(sessionId: Long) = flowOf(listOf(Transaction(1L, sessionId, 0L, RAW, KEY, 100L, TransactionType.DEBIT, null, null, null)))
        override fun observePagedByPayee(normalizedPayee: String, includeLinkedNames: Boolean) = flowOf(PagingData.empty<Transaction>())
        override fun observePayeeTotals(normalizedPayee: String, includeLinkedNames: Boolean) = flowOf(PayeeTotals())
        override fun observePayeeDayTotals(normalizedPayee: String, includeLinkedNames: Boolean) = flowOf(emptyList<PeriodTotal>())
        override fun observePayeeMonthTotals(normalizedPayee: String, includeLinkedNames: Boolean) = flowOf(emptyList<PeriodTotal>())
        override suspend fun insertAll(transactions: List<Transaction>) = Result.Success(Unit)
        override suspend fun findDuplicateKeys(candidates: List<Transaction>) = Result.Success(emptyList<TransactionKey>())
        override suspend fun setExcluded(id: Long, isExcluded: Boolean) = Result.Success(Unit)
        override suspend fun setDuplicatesExcluded(sessionId: Long, normalizedPayee: String, isExcluded: Boolean) = Result.Success(Unit)
        override suspend fun assignPayee(sessionId: Long, normalizedPayee: String, payeeId: Long) = Result.Success(Unit)
        override suspend fun unmappedCount(sessionId: Long) = Result.Success(1)
        override suspend fun exportRows() = Result.Success(emptyList<TransactionExportRow>())
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "payeeDetail={0}")
        fun forms() = listOf(arrayOf(false), arrayOf(true))

        private const val RAW = "Corner SHOP"
        private const val KEY = "CORNER SHOP"
    }
}
