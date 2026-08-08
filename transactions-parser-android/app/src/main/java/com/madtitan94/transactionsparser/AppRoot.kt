package com.madtitan94.transactionsparser

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.madtitan94.transactionsparser.core.designsystem.components.LoadingIndicator
import com.madtitan94.transactionsparser.feature.auth.presentation.LoginRoot
import com.madtitan94.transactionsparser.feature.categories.presentation.navigation.CategoriesRoute
import com.madtitan94.transactionsparser.feature.categories.presentation.navigation.categoriesGraph
import com.madtitan94.transactionsparser.feature.profile.presentation.navigation.ProfileRoute
import com.madtitan94.transactionsparser.feature.profile.presentation.navigation.profileGraph
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

private val BOTTOM_BAR_ITEMS = listOf(
    BottomBarItem(SessionsHistoryRoute, SessionsHistoryRoute::class, R.string.tab_statements, Icons.AutoMirrored.Filled.ReceiptLong),
    BottomBarItem(UploadRoute, UploadRoute::class, R.string.tab_upload, Icons.Default.UploadFile),
    BottomBarItem(CategoriesRoute, CategoriesRoute::class, R.string.tab_categories, Icons.Default.Category),
    BottomBarItem(ProfileRoute, ProfileRoute::class, R.string.tab_profile, Icons.Default.Person)
)

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                BOTTOM_BAR_ITEMS.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any { destination ->
                        destination.hasRoute(item.routeClass)
                    } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.labelRes)) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = SessionsHistoryRoute,
            modifier = Modifier.padding(padding)
        ) {
            sessionsGraph(navController)
            uploadGraph(
                onOpenSession = { sessionId ->
                    navController.navigate(SessionDetailRoute(sessionId))
                }
            )
            categoriesGraph()
            profileGraph()
        }
    }
}
