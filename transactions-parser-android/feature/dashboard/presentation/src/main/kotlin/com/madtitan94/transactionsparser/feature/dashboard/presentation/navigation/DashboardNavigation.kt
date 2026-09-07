package com.madtitan94.transactionsparser.feature.dashboard.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.madtitan94.transactionsparser.feature.dashboard.presentation.DashboardRoot
import com.madtitan94.transactionsparser.feature.dashboard.presentation.builder.DashboardBuilderRoot
import com.madtitan94.transactionsparser.feature.dashboard.presentation.insight.CategoryInsightRoot
import com.madtitan94.transactionsparser.feature.dashboard.presentation.manage.DefaultDashboardRoot
import com.madtitan94.transactionsparser.feature.dashboard.presentation.manage.ManageDashboardsRoot
import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute

/** The gallery: switch dashboards on and off, reorder them, and reach the builder. */
@Serializable
data object ManageDashboardsRoute

/** Which dashboard Home opens on. Its own destination, reached from Settings. */
@Serializable
data object DefaultDashboardRoute

/**
 * The builder, creating when [dashboardId] is null and editing when it is not.
 *
 * One destination rather than two because the screen is identical either way — the id only decides
 * whether the checklist starts empty or from what is already saved.
 */
@Serializable
data class DashboardBuilderRoute(val dashboardId: String? = null) {
    companion object {
        /**
         * The key navigation stores [dashboardId] under, which is the property's own name.
         *
         * Named here rather than left implicit because the ViewModel reads the argument by key
         * instead of through `toRoute`: `toRoute` decodes via `android.os.Bundle`, which is not
         * available in a JVM unit test, and this phase's builder logic is worth more covered than
         * uncovered. Keeping the constant on the route is what stops the two drifting apart.
         */
        const val ID_ARG = "dashboardId"
    }
}

/**
 * One category, opened from a ranked row, a donut slice, or the Categories tab.
 *
 * [categoryId] carries [UNCATEGORISED] rather than a null for the unmapped bucket. Type-safe routes
 * have no built-in `NavType` for a nullable `Long`, and a sentinel is honest here because category
 * ids come from an `AUTOINCREMENT` column and are always positive — nothing real can collide with
 * it. [DateRange.AllTime] already uses the same device for a bound that has no value.
 *
 * [categoryName] travels with the id so the header can be drawn on the first frame, before any
 * query has returned. Looking it up would leave the title blank for as long as the read took, on a
 * screen the user reached *by tapping that very name*.
 */
@Serializable
data class CategoryInsightRoute(
    val categoryId: Long,
    val categoryName: String? = null
) {
    companion object {
        /** The unmapped bucket: spend whose payee is not mapped, so it has no category row. */
        const val UNCATEGORISED = -1L

        /** See `DashboardBuilderRoute.ID_ARG` for why the ViewModel reads arguments by key. */
        const val ID_ARG = "categoryId"
        const val NAME_ARG = "categoryName"
    }
}

/**
 * [onOpenPayee] and [onOpenCategories] are callbacks rather than direct navigation because both
 * destinations live in other feature modules — the same shape `settingsGraph` uses to reach Profile,
 * which keeps this module from depending on ones it has nothing else to say to.
 *
 * The three settings destinations below are this module's own, so they navigate directly. Settings
 * reaches them by route rather than by owning them: the screens are about dashboards and every
 * string and model they need is here.
 */
fun NavGraphBuilder.dashboardGraph(
    navController: NavController,
    onOpenPayee: (normalizedPayee: String, rawPayee: String) -> Unit,
    onOpenCategories: () -> Unit
) {
    composable<DashboardRoute> {
        DashboardRoot(
            onOpenPayee = onOpenPayee,
            onOpenCategories = onOpenCategories,
            onManageDashboards = { navController.navigate(ManageDashboardsRoute) },
            onOpenCategoryInsight = { id, name ->
                navController.navigate(
                    CategoryInsightRoute(id ?: CategoryInsightRoute.UNCATEGORISED, name)
                )
            }
        )
    }
    composable<ManageDashboardsRoute> {
        ManageDashboardsRoot(
            onBack = { navController.navigateUp() },
            onBuildDashboard = { navController.navigate(DashboardBuilderRoute()) },
            onEditDashboard = { id -> navController.navigate(DashboardBuilderRoute(id)) }
        )
    }
    composable<DefaultDashboardRoute> {
        DefaultDashboardRoot(onBack = { navController.navigateUp() })
    }
    composable<DashboardBuilderRoute> {
        DashboardBuilderRoot(onClose = { navController.navigateUp() })
    }
    composable<CategoryInsightRoute> {
        CategoryInsightRoot(
            onBack = { navController.navigateUp() },
            onOpenPayee = onOpenPayee
        )
    }
}
