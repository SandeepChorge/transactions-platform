package com.madtitan94.transactionsparser.feature.categories.presentation

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeCategoryDataSource : CategoryLocalDataSource {
        val categories = MutableStateFlow<List<Category>>(emptyList())
        val linkedCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
        private var nextId = 1L

        override fun observeAll(): Flow<List<Category>> = categories

        override fun observeLinkedPayeeCounts(): Flow<Map<Long, Int>> = linkedCounts

        override suspend fun insert(name: String): Result<Long, DataError.Local> {
            if (categories.value.any { it.name.equals(name, ignoreCase = true) }) {
                return Result.Error(DataError.Local.DUPLICATE)
            }
            val id = nextId++
            categories.value += Category(id = id, name = name)
            return Result.Success(id)
        }

        override suspend fun rename(id: Long, name: String): EmptyResult<DataError.Local> {
            categories.value = categories.value.map { if (it.id == id) it.copy(name = name) else it }
            return Result.Success(Unit)
        }

        override suspend fun delete(id: Long): EmptyResult<DataError.Local> {
            categories.value = categories.value.filterNot { it.id == id }
            return Result.Success(Unit)
        }

        override suspend fun linkedPayeeCount(id: Long): Result<Int, DataError.Local> =
            Result.Success(linkedCounts.value[id] ?: 0)
    }

    @Test
    fun `adding a category updates state and closes dialog`() = runTest {
        val dataSource = FakeCategoryDataSource()
        val viewModel = CategoriesViewModel(dataSource)

        viewModel.state.test {
            skipItems(1)

            viewModel.onAction(CategoriesAction.OnAddClick)
            assertThat(awaitItem().dialog).isNotNull().isInstanceOf(CategoryDialog.Add::class)

            viewModel.onAction(CategoriesAction.OnDialogNameChange("Food"))
            skipItems(1)

            viewModel.onAction(CategoriesAction.OnDialogConfirm)

            val updated = expectMostRecentItem()
            assertThat(updated.dialog).isNull()
            assertThat(updated.categories.single().name).isEqualTo("Food")
        }
    }

    @Test
    fun `blank category name shows validation error`() = runTest {
        val viewModel = CategoriesViewModel(FakeCategoryDataSource())

        viewModel.state.test {
            skipItems(1)
            viewModel.onAction(CategoriesAction.OnAddClick)
            skipItems(1)

            viewModel.onAction(CategoriesAction.OnDialogConfirm)

            val dialog = expectMostRecentItem().dialog as CategoryDialog.Add
            assertThat(dialog.error).isNotNull().isInstanceOf(
                com.madtitan94.transactionsparser.core.presentation.UiText.StringResource::class
            )
        }
    }

    @Test
    fun `delete is blocked for categories with linked payees`() = runTest {
        val dataSource = FakeCategoryDataSource()
        dataSource.insert("Groceries")
        dataSource.linkedCounts.value = mapOf(1L to 3)
        val viewModel = CategoriesViewModel(dataSource)

        viewModel.events.test {
            viewModel.onAction(CategoriesAction.OnDeleteClick(1L))
            assertThat(awaitItem()).isInstanceOf(CategoriesEvent.ShowMessage::class)
        }
        // Category still exists and no confirm dialog opened.
        assertThat(viewModel.state.value.dialog).isNull()
        assertThat(dataSource.categories.value.size).isEqualTo(1)
    }

    @Test
    fun `unlinked category can be deleted after confirmation`() = runTest {
        val dataSource = FakeCategoryDataSource()
        dataSource.insert("Unused")
        val viewModel = CategoriesViewModel(dataSource)

        viewModel.onAction(CategoriesAction.OnDeleteClick(1L))
        assertThat(viewModel.state.value.dialog).isNotNull().isInstanceOf(CategoryDialog.ConfirmDelete::class)

        viewModel.onAction(CategoriesAction.OnDialogConfirm)

        assertThat(dataSource.categories.value.isEmpty()).isEqualTo(true)
        assertThat(viewModel.state.value.dialog).isNull()
    }
}
