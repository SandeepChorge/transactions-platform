package com.madtitan94.transactionsparser.core.domain.catalog

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.madtitan94.transactionsparser.core.domain.datasource.CategoryLocalDataSource
import com.madtitan94.transactionsparser.core.domain.model.Category
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.EmptyResult
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AdoptSuggestedCategoryTest {

    @Test
    fun `a live match is selected and nothing is inserted`() = runTest {
        val categories = FakeCategories(live = listOf(Category(id = 4L, name = "food")))
        val adopted = AdoptSuggestedCategory(categories).adopt("  Food ")

        assertThat(adopted).isEqualTo(Result.Success(4L))
        assertThat(categories.inserted).isEmpty()
        assertThat(categories.restored).isEmpty()
    }

    @Test
    fun `a soft-deleted match is restored`() = runTest {
        val categories = FakeCategories(deleted = listOf(Category(id = 9L, name = "Rent")))
        val adopted = AdoptSuggestedCategory(categories).adopt("rent")

        assertThat(adopted).isEqualTo(Result.Success(9L))
        assertThat(categories.restored).isEqualTo(listOf(9L))
        assertThat(categories.inserted).isEmpty()
        assertThat(categories.live.value.map { it.id }).isEqualTo(listOf(9L))
    }

    @Test
    fun `a live match wins when a different case also sits in recently deleted`() = runTest {
        val categories = FakeCategories(
            live = listOf(Category(id = 1L, name = "food")),
            deleted = listOf(Category(id = 2L, name = "Food"))
        )

        val adopted = AdoptSuggestedCategory(categories).adopt("FOOD")

        assertThat(adopted).isEqualTo(Result.Success(1L))
        assertThat(categories.restored).isEmpty()
    }

    @Test
    fun `otherwise the trimmed name is inserted`() = runTest {
        val categories = FakeCategories()
        val adopted = AdoptSuggestedCategory(categories).adopt("  EMI/Loans  ")

        assertThat(adopted).isEqualTo(Result.Success(1L))
        assertThat(categories.inserted).isEqualTo(listOf("EMI/Loans"))
    }

    @Test
    fun `a blank name is refused`() = runTest {
        val categories = FakeCategories()
        val adopted = AdoptSuggestedCategory(categories).adopt("   ")

        assertThat(adopted).isInstanceOf(Result.Error::class)
        assertThat((adopted as Result.Error).error).isEqualTo(DataError.Local.UNKNOWN)
        assertThat(categories.inserted).isEmpty()
    }

    @Test
    fun `several names can be adopted one after another`() = runTest {
        val categories = FakeCategories()
        val useCase = AdoptSuggestedCategory(categories)

        assertThat(useCase.adopt("Food")).isEqualTo(Result.Success(1L))
        assertThat(useCase.adopt("Groceries")).isEqualTo(Result.Success(2L))
        assertThat(categories.inserted).isEqualTo(listOf("Food", "Groceries"))
        assertThat(categories.live.value.map { it.name }).isEqualTo(listOf("Food", "Groceries"))
    }

    private class FakeCategories(
        live: List<Category> = emptyList(),
        deleted: List<Category> = emptyList()
    ) : CategoryLocalDataSource {
        val live = MutableStateFlow(live)
        val deleted = MutableStateFlow(deleted)
        val inserted = mutableListOf<String>()
        val restored = mutableListOf<Long>()
        private var nextId = 1L

        override fun observeAll(): Flow<List<Category>> = live
        override fun observeDeleted(): Flow<List<Category>> = deleted
        override fun observeLinkedPayeeCounts(): Flow<Map<Long, Int>> = MutableStateFlow(emptyMap())

        override suspend fun insert(name: String): Result<Long, DataError.Local> {
            inserted += name
            val id = nextId++
            live.value += Category(id = id, name = name)
            return Result.Success(id)
        }

        override suspend fun rename(id: Long, name: String): EmptyResult<DataError.Local> =
            Result.Success(Unit)

        override suspend fun delete(id: Long): EmptyResult<DataError.Local> = Result.Success(Unit)

        override suspend fun restore(id: Long): EmptyResult<DataError.Local> {
            val match = deleted.value.firstOrNull { it.id == id }
                ?: return Result.Error(DataError.Local.NOT_FOUND)
            deleted.value = deleted.value.filterNot { it.id == id }
            live.value += match
            restored += id
            return Result.Success(Unit)
        }

        override suspend fun linkedPayeeCount(id: Long): Result<Int, DataError.Local> =
            Result.Success(0)
    }
}
