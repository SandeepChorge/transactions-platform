package com.madtitan94.transactionsparser

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.madtitan94.transactionsparser.core.designsystem.components.AppBottomBar
import com.madtitan94.transactionsparser.core.designsystem.components.AppBottomBarCentreAction
import com.madtitan94.transactionsparser.core.designsystem.components.AppBottomBarItem
import com.madtitan94.transactionsparser.core.designsystem.components.LoadingIndicator
import com.madtitan94.transactionsparser.feature.auth.presentation.LoginRoot
import com.madtitan94.transactionsparser.feature.categories.presentation.navigation.CategoriesRoute
import com.madtitan94.transactionsparser.feature.categories.presentation.navigation.categoriesGraph
import com.madtitan94.transactionsparser.feature.dashboard.presentation.navigation.CategoryInsightRoute
import com.madtitan94.transactionsparser.feature.dashboard.presentation.navigation.DashboardRoute
import com.madtitan94.transactionsparser.feature.dashboard.presentation.navigation.DefaultDashboardRoute
import com.madtitan94.transactionsparser.feature.dashboard.presentation.navigation.ManageDashboardsRoute
import com.madtitan94.transactionsparser.feature.dashboard.presentation.navigation.dashboardGraph
import com.madtitan94.transactionsparser.feature.profile.presentation.navigation.ProfileRoute
import com.madtitan94.transactionsparser.feature.profile.presentation.navigation.profileGraph
import com.madtitan94.transactionsparser.feature.settings.presentation.navigation.SettingsRoute
import com.madtitan94.transactionsparser.feature.settings.presentation.navigation.settingsGraph
import com.madtitan94.transactionsparser.feature.sessions.presentation.navigation.PayeeDetailRoute
import com.madtitan94.transactionsparser.feature.sessions.presentation.navigation.PayeeDirectoryRoute
import com.madtitan94.transactionsparser.feature.sessions.presentation.navigation.SessionDetailRoute
import com.madtitan94.transactionsparser.feature.sessions.presentation.navigation.SessionsHistoryRoute
import com.madtitan94.transactionsparser.feature.sessions.presentation.navigation.sessionsGraph
import com.madtitan94.transactionsparser.feature.upload.presentation.navigation.UploadRoute
import com.madtitan94.transactionsparser.feature.upload.presentation.navigation.uploadGraph
import org.koin.androidx.compose.koinViewModel
import kotlin.reflect.KClass

@Composable
fun AppRoot(
    viewModel: MainViewModel = koinViewModel()
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    when (authState) {
        AuthState.Loading -> LoadingIndicator()
        AuthState.LoggedOut -> LoginRoot()
        AuthState.LoggedIn -> MainScaffold()
    }
}

private data class BottomBarItem(
    val route: Any,
    val routeClass: KClass<*>,
    val labelRes: Int,
    val icon: ImageVector
)

/**
 * The four destinations either side of the centre action, in the design's own order.
 *
 * Upload is not among them. It became the raised ⊕ in the middle, which is an action rather than a
 * destination — you go there, add a statement and come back — so it carries no selected state and
 * does not belong in a list whose whole purpose is deciding which tab is lit.
 *
 * Categories is not among them either: five slots, and `design/DashboardSpec.dc.html` spends them
 * on Home, Statements, Add, Payees and You. It moved under You, which is now the one place
 * account-level setup lives.
 */
private val BOTTOM_BAR_ITEMS = listOf(
    BottomBarItem(DashboardRoute, DashboardRoute::class, R.string.tab_home, Icons.Default.Home),
    BottomBarItem(SessionsHistoryRoute, SessionsHistoryRoute::class, R.string.tab_statements, Icons.AutoMirrored.Filled.ReceiptLong),
    BottomBarItem(PayeeDirectoryRoute, PayeeDirectoryRoute::class, R.string.tab_payees, Icons.Default.Storefront),
    // Profile is not a tab of its own — it is reached from You, which is now the single place
    // account-level actions live (profile, categories, export, recovery, logout).
    BottomBarItem(SettingsRoute, SettingsRoute::class, R.string.tab_you, Icons.Default.Person)
)

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    fun switchTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            AppBottomBar {
                BOTTOM_BAR_ITEMS.forEachIndexed { index, item ->
                    val selected = currentDestination?.hierarchy?.any { destination ->
                        destination.hasRoute(item.routeClass)
                    } == true
                    AppBottomBarItem(
                        selected = selected,
                        onClick = { switchTab(item.route) },
                        icon = item.icon,
                        label = stringResource(item.labelRes)
                    )
                    // The centre action is inserted between the second and third destination
                    // rather than appended, because it is the middle column of a five-column grid.
                    if (index == 1) {
                        AppBottomBarCentreAction(
                            // Upload is pushed onto the current tab rather than switched to, so
                            // coming back from it returns to whatever the user was doing. Switching
                            // would make Home the place every upload ends.
                            onClick = { navController.navigate(UploadRoute) },
                            icon = Icons.Default.Add,
                            contentDescription = stringResource(R.string.tab_add)
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = DashboardRoute,
            modifier = Modifier.padding(padding)
        ) {
            dashboardGraph(
                navController = navController,
                onOpenPayee = { normalizedPayee, rawPayee ->
                    navController.navigate(PayeeDetailRoute(normalizedPayee, rawPayee))
                },
                onOpenCategories = { navController.navigate(CategoriesRoute) }
            )
            sessionsGraph(navController)
            uploadGraph(
                onOpenSession = { sessionId ->
                    navController.navigate(SessionDetailRoute(sessionId))
                }
            )
            categoriesGraph(
                onOpenInsight = { categoryId, categoryName ->
                    navController.navigate(CategoryInsightRoute(categoryId, categoryName))
                }
            )
            profileGraph(navController)
            settingsGraph(
                navController = navController,
                appVersion = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE,
                onOpenProfile = { navController.navigate(ProfileRoute) },
                onOpenCategories = { navController.navigate(CategoriesRoute) },
                onOpenManageDashboards = { navController.navigate(ManageDashboardsRoute) },
                onOpenDefaultDashboard = { navController.navigate(DefaultDashboardRoute) }
            )
        }
    }
}
