package com.madtitan94.transactionsparser.feature.categories.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.madtitan94.transactionsparser.feature.categories.presentation.CategoriesRoot
import kotlinx.serialization.Serializable

@Serializable
data object CategoriesRoute

/**
 * [onOpenInsight] is a callback rather than direct navigation because the insight screen lives in
 * `:feature:dashboard` — the same shape this module's neighbours use to reach destinations they do
 * not own.
 */
fun NavGraphBuilder.categoriesGraph(
    onOpenInsight: (categoryId: Long, categoryName: String) -> Unit
) {
    composable<CategoriesRoute> {
        CategoriesRoot(onOpenInsight = onOpenInsight)
    }
}
