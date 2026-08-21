package com.madtitan94.transactionsparser.feature.settings.presentation.deleted

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.madtitan94.transactionsparser.core.domain.datasource.CategoryLocalDataSource
import com.madtitan94.transactionsparser.core.domain.model.Category
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
 * Restore is a write that happens on a single tap, so the thing worth pinning is that the tap
 * alone never performs it — only a confirmed prompt does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecentlyDeletedViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `tapping restore only asks, and restores nothing yet`() = runTest {
        val categories = FakeCategoryDataSource(deleted = listOf(Category(1L, "Groceries")))
        val viewModel = RecentlyDeletedViewModel(categories)

        viewModel.onAction(RecentlyDeletedAction.OnRestoreClick(Category(1L, "Groceries")))

        assertThat(viewModel.state.value.pendingRestore)
            .isEqualTo(RestorePrompt(1L, "Groceries"))
        assertThat(categories.restored).isEmpty()
    }

    @Test
    fun `confirming restores the category the prompt named`() = runTest {
        val categories = FakeCategoryDataSource(
            deleted = listOf(Category(1L, "Groceries"), Category(2L, "Travel"))
        )
        val viewModel = RecentlyDeletedViewModel(categories)

        viewModel.onAction(RecentlyDeletedAction.OnRestoreClick(Category(2L, "Travel")))
        viewModel.onAction(RecentlyDeletedAction.OnConfirmRestore)

        assertThat(categories.restored).containsExactly(2L)
        assertThat(viewModel.state.value.pendingRestore).isNull()
    }

    @Test
    fun `dismissing leaves the category deleted`() = runTest {
        val categories = FakeCategoryDataSource(deleted = listOf(Category(1L, "Groceries")))
        val viewModel = RecentlyDeletedViewModel(categories)

        viewModel.onAction(RecentlyDeletedAction.OnRestoreClick(Category(1L, "Groceries")))
        viewModel.onAction(RecentlyDeletedAction.OnDismissRestore)

        assertThat(viewModel.state.value.pendingRestore).isNull()
        assertThat(categories.restored).isEmpty()
    }

    /** Confirming a prompt whose row has gone would restore nothing and still report success. */
    @Test
    fun `a prompt is dropped when its category leaves the list`() = runTest {
        val categories = FakeCategoryDataSource(deleted = listOf(Category(1L, "Groceries")))
        val viewModel = RecentlyDeletedViewModel(categories)

        viewModel.onAction(RecentlyDeletedAction.OnRestoreClick(Category(1L, "Groceries")))
        categories.deleted.value = emptyList()

        assertThat(viewModel.state.value.pendingRestore).isNull()

        viewModel.onAction(RecentlyDeletedAction.OnConfirmRestore)

        assertThat(categories.restored).isEmpty()
    }

    private class FakeCategoryDataSource(deleted: List<Category>) : CategoryLocalDataSource {
        val deleted = MutableStateFlow(deleted)
        val restored = mutableListOf<Long>()

        override fun observeDeleted(): Flow<List<Category>> = deleted

        override suspend fun restore(id: Long): EmptyResult<DataError.Local> {
            restored += id
            this.deleted.value = deleted.value.filterNot { it.id == id }
            return Result.Success(Unit)
        }

        override fun observeAll(): Flow<List<Category>> = MutableStateFlow(emptyList())
        override fun observeLinkedPayeeCounts(): Flow<Map<Long, Int>> = MutableStateFlow(emptyMap())
        override suspend fun insert(name: String) = Result.Success(0L)
        override suspend fun rename(id: Long, name: String) = Result.Success(Unit)
        override suspend fun delete(id: Long) = Result.Success(Unit)
        override suspend fun linkedPayeeCount(id: Long) = Result.Success(0)
    }
}
