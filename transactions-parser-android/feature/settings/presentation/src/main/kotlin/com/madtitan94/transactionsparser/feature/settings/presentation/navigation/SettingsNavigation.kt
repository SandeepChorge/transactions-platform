package com.madtitan94.transactionsparser.feature.settings.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.madtitan94.transactionsparser.feature.settings.presentation.SettingsRoot
import com.madtitan94.transactionsparser.feature.settings.presentation.deleted.RecentlyDeletedRoot
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

@Serializable
data object RecentlyDeletedRoute

/**
 * [onOpenProfile] is a callback rather than a direct navigation call because Profile lives in
 * another feature module — the same shape `uploadGraph` uses to reach session detail, which keeps
 * this module from depending on one it has nothing else to say to.
 *
 * [appVersion] is passed in because it belongs to the application, not to this feature: a library
 * module has no `BuildConfig.VERSION_NAME` of the app it happens to be installed in.
 */
fun NavGraphBuilder.settingsGraph(
    navController: NavController,
    appVersion: String,
    onOpenProfile: () -> Unit
) {
    composable<SettingsRoute> {
        SettingsRoot(
            appVersion = appVersion,
            onOpenProfile = onOpenProfile,
            onOpenRecentlyDeleted = { navController.navigate(RecentlyDeletedRoute) }
        )
    }
    composable<RecentlyDeletedRoute> {
        RecentlyDeletedRoot(onBack = { navController.navigateUp() })
    }
}
