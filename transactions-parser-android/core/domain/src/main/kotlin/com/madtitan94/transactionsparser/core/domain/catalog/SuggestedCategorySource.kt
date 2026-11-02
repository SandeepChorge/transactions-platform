package com.madtitan94.transactionsparser.core.domain.catalog

import kotlinx.coroutines.flow.Flow

/**
 * The public suggestion list. Not the user's categories — those stay in Room, and this source
 * never writes them.
 */
interface SuggestedCategorySource {
    fun observe(): Flow<CategoryCatalog>
}
