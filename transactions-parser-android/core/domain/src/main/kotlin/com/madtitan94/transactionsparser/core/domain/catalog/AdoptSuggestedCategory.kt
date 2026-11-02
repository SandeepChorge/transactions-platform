package com.madtitan94.transactionsparser.core.domain.catalog

import com.madtitan94.transactionsparser.core.domain.datasource.CategoryLocalDataSource
import com.madtitan94.transactionsparser.core.domain.model.Category
import com.madtitan94.transactionsparser.core.domain.util.DataError
import com.madtitan94.transactionsparser.core.domain.util.Result
import kotlinx.coroutines.flow.first

/**
 * Turns a suggestion, or a name typed in the new-category dialog, into one of the account's
 * categories.
 *
 * Matched without regard to case, the same way a backup restore matches, because the unique index
 * is exact and the list is not: "Food" beside "food" would read as a bug. A live match is selected.
 * A soft-deleted match is restored, which is also what keeps the unique index from rejecting the
 * insert while Recently deleted still holds the name. Otherwise the trimmed name is inserted.
 */
class AdoptSuggestedCategory(
    private val categories: CategoryLocalDataSource
) {
    suspend fun adopt(rawName: String): Result<Long, DataError.Local> {
        val name = rawName.trim()
        if (name.isEmpty()) return Result.Error(DataError.Local.UNKNOWN)

        val live = categories.observeAll().first()
        live.firstOrNull { it.sameName(name) }?.let { return Result.Success(it.id) }

        val deleted = categories.observeDeleted().first()
        deleted.firstOrNull { it.sameName(name) }?.let { match ->
            return when (val restored = categories.restore(match.id)) {
                is Result.Error -> restored
                is Result.Success -> Result.Success(match.id)
            }
        }
        return categories.insert(name)
    }
}

/** Same comparison backup restore uses when it reuses a category the account already has. */
private fun Category.sameName(other: String): Boolean = name.lowercase() == other.lowercase()
