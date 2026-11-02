package com.madtitan94.transactionsparser.feature.categories.presentation

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
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
        val deleted = MutableStateFlow<List<Category>>(emptyList())
        val linkedCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
        private var nextId = 1L

        override fun observeAll(): Flow<List<Category>> = categories

        override fun observeDeleted(): Flow<List<Category>> = deleted

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
            val target = categories.value.firstOrNull { it.id == id } ?: return Result.Success(Unit)
            categories.value = categories.value - target
            deleted.value += target
            return Result.Success(Unit)
        }

        override suspend fun restore(id: Long): EmptyResult<DataError.Local> {
            val target = deleted.value.firstOrNull { it.id == id } ?: return Result.Success(Unit)
            deleted.value = deleted.value - target
            categories.value += target
            return Result.Success(Unit)
        }

        override suspend fun linkedPayeeCount(id: Long): Result<Int, DataError.Local> =
            Result.Success(linkedCounts.value[id] ?: 0)
    }

    private fun viewModel(
        dataSource: FakeCategoryDataSource,
        catalog: CategoryCatalog = CategoryCatalog(version = 1, categories = emptyList()),
        analytics: RecordingAnalytics = RecordingAnalytics()
    ) = CategoriesViewModel(
        dataSource,
        FakeCatalog(catalog),
        AdoptSuggestedCategory(dataSource),
        analytics
    ) to analytics

    @Test
    fun `adding a category updates state and closes dialog`() = runTest {
        val dataSource = FakeCategoryDataSource()
        val viewModel = viewModel(dataSource).first

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
        val viewModel = viewModel(FakeCategoryDataSource()).first

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
        val viewModel = viewModel(dataSource).first

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
        val viewModel = viewModel(dataSource).first

        viewModel.onAction(CategoriesAction.OnDeleteClick(1L))
        assertThat(viewModel.state.value.dialog).isNotNull().isInstanceOf(CategoryDialog.ConfirmDelete::class)

        viewModel.onAction(CategoriesAction.OnDialogConfirm)

        assertThat(dataSource.categories.value.isEmpty()).isEqualTo(true)
        assertThat(viewModel.state.value.dialog).isNull()
    }

    @Test
    fun `an empty account shows suggestions and hides them once a category exists`() = runTest {
        val dataSource = FakeCategoryDataSource()
        val (viewModel, _) = viewModel(dataSource, catalog = sampleCatalog)

        assertThat(viewModel.state.value.showSuggestions).isTrue()
        assertThat(viewModel.state.value.suggestions.map { it.name }).isEqualTo(listOf("Food", "Groceries"))

        dataSource.insert("Rent")

        assertThat(viewModel.state.value.showSuggestions).isFalse()
        assertThat(viewModel.state.value.categories.map { it.name }).isEqualTo(listOf("Rent"))
    }

    @Test
    fun `tapping a suggestion adopts it and records the catalog key`() = runTest {
        val dataSource = FakeCategoryDataSource()
        val (viewModel, analytics) = viewModel(dataSource, catalog = sampleCatalog)

        viewModel.onAction(CategoriesAction.OnAdoptSuggestion("Food"))

        assertThat(dataSource.categories.value.map { it.name }).isEqualTo(listOf("Food"))
        assertThat(viewModel.state.value.showSuggestions).isFalse()
        val event = analytics.events.single() as AnalyticsEvent.SuggestedCategoryAdopted
        assertThat(event.categoryKey).isEqualTo("food")
        assertThat(event.screen).isEqualTo(AnalyticsParams.SCREEN_CATEGORIES)
        assertThat(event.addedAll).isFalse()
        assertThat(event.catalogVersion).isEqualTo(3)
        assertThat(event.params.values.filterIsInstance<String>()).isEqualTo(listOf("food", "categories"))
    }

    @Test
    fun `add all adopts every suggestion as one event each`() = runTest {
        val dataSource = FakeCategoryDataSource()
        val (viewModel, analytics) = viewModel(dataSource, catalog = sampleCatalog)

        viewModel.onAction(CategoriesAction.OnAdoptAllSuggestions)

        assertThat(dataSource.categories.value.map { it.name }).isEqualTo(listOf("Food", "Groceries"))
        assertThat(viewModel.state.value.showSuggestions).isFalse()
        assertThat(analytics.events.map { (it as AnalyticsEvent.SuggestedCategoryAdopted).categoryKey })
            .isEqualTo(listOf("food", "groceries"))
        assertThat(analytics.events.all { (it as AnalyticsEvent.SuggestedCategoryAdopted).addedAll }).isTrue()
    }

    @Test
    fun `a typed category is not an adoption event`() = runTest {
        val dataSource = FakeCategoryDataSource()
        val (viewModel, analytics) = viewModel(dataSource, catalog = sampleCatalog)

        viewModel.onAction(CategoriesAction.OnAddClick)
        viewModel.onAction(CategoriesAction.OnDialogNameChange("School fees"))
        viewModel.onAction(CategoriesAction.OnDialogConfirm)

        assertThat(dataSource.categories.value.map { it.name }).isEqualTo(listOf("School fees"))
        assertThat(analytics.events).isEmpty()
    }

    private class RecordingAnalytics : AnalyticsTracker {
        val events = mutableListOf<AnalyticsEvent>()
        override fun track(event: AnalyticsEvent) {
            events += event
        }
    }

    private class FakeCatalog(private val catalog: CategoryCatalog) : SuggestedCategorySource {
        override fun observe(): Flow<CategoryCatalog> = MutableStateFlow(catalog)
    }

    private val sampleCatalog = CategoryCatalog(
        version = 3,
        categories = listOf(
            SuggestedCategory(key = "food", name = "Food", sortOrder = 0),
            SuggestedCategory(key = "groceries", name = "Groceries", sortOrder = 1)
        )
    )
}
