package com.madtitan94.transactionsparser.feature.sessions.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.madtitan94.transactionsparser.feature.sessions.presentation.detail.SessionDetailRoot
import com.madtitan94.transactionsparser.feature.sessions.presentation.history.SessionsHistoryRoot
import com.madtitan94.transactionsparser.feature.sessions.presentation.uploadhistory.UploadHistoryRoot
import kotlinx.serialization.Serializable

@Serializable
data object SessionsHistoryRoute

@Serializable
data class SessionDetailRoute(val sessionId: Long)

@Serializable
data object UploadHistoryRoute

fun NavGraphBuilder.sessionsGraph(navController: NavController) {
    composable<SessionsHistoryRoute> {
        SessionsHistoryRoot(
            onOpenSession = { sessionId -> navController.navigate(SessionDetailRoute(sessionId)) },
            onOpenUploadHistory = { navController.navigate(UploadHistoryRoute) }
        )
    }
    composable<SessionDetailRoute> {
        SessionDetailRoot(onBack = { navController.navigateUp() })
    }
    composable<UploadHistoryRoute> {
        UploadHistoryRoot(onBack = { navController.navigateUp() })
    }
}
