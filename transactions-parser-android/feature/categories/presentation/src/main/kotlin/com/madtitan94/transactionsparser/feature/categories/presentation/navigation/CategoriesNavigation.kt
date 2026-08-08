package com.madtitan94.transactionsparser.feature.categories.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.madtitan94.transactionsparser.feature.categories.presentation.CategoriesRoot
import kotlinx.serialization.Serializable

@Serializable
data object CategoriesRoute

fun NavGraphBuilder.categoriesGraph() {
    composable<CategoriesRoute> {
        CategoriesRoot()
    }
}
