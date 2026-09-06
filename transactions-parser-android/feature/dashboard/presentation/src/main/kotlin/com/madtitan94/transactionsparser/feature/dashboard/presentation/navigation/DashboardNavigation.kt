package com.madtitan94.transactionsparser.feature.dashboard.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.madtitan94.transactionsparser.feature.dashboard.presentation.DashboardRoot
import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute

/**
 * [onOpenPayee] and [onOpenCategories] are callbacks rather than direct navigation because both
 * destinations live in other feature modules — the same shape `settingsGraph` uses to reach Profile,
 * which keeps this module from depending on ones it has nothing else to say to.
 */
fun NavGraphBuilder.dashboardGraph(
    onOpenPayee: (normalizedPayee: String, rawPayee: String) -> Unit,
    onOpenCategories: () -> Unit
) {
    composable<DashboardRoute> {
        DashboardRoot(
            onOpenPayee = onOpenPayee,
            onOpenCategories = onOpenCategories
        )
    }
}
