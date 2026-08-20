package com.madtitan94.transactionsparser.feature.profile.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.madtitan94.transactionsparser.feature.profile.presentation.ProfileRoot
import kotlinx.serialization.Serializable

@Serializable
data object ProfileRoute

fun NavGraphBuilder.profileGraph(navController: NavController) {
    composable<ProfileRoute> {
        ProfileRoot(onBack = { navController.navigateUp() })
    }
}
