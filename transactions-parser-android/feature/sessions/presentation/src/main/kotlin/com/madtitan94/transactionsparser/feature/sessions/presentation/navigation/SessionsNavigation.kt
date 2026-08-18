package com.madtitan94.transactionsparser.feature.sessions.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.madtitan94.transactionsparser.feature.sessions.presentation.detail.SessionDetailRoot
import com.madtitan94.transactionsparser.feature.sessions.presentation.history.SessionsHistoryRoot
import com.madtitan94.transactionsparser.feature.sessions.presentation.payee.PayeeDetailRoot
import com.madtitan94.transactionsparser.feature.sessions.presentation.uploadhistory.UploadHistoryRoot
import kotlinx.serialization.Serializable

@Serializable
data object SessionsHistoryRoute

@Serializable
data class SessionDetailRoute(val sessionId: Long)

/**
 * Keyed on the statement name rather than a payee id: every card has one, mapped or not, so the
 * screen is reachable from all three tabs and can offer to map an unmapped payee. [rawPayee] rides
 * along so the title reads correctly before the first row loads.
 */
@Serializable
data class PayeeDetailRoute(val normalizedPayee: String, val rawPayee: String)

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
        SessionDetailRoot(
            onBack = { navController.navigateUp() },
            onOpenPayee = { normalizedPayee, rawPayee ->
                navController.navigate(PayeeDetailRoute(normalizedPayee, rawPayee))
            }
        )
    }
    composable<PayeeDetailRoute> {
        PayeeDetailRoot(onBack = { navController.navigateUp() })
    }
    composable<UploadHistoryRoute> {
        UploadHistoryRoot(onBack = { navController.navigateUp() })
    }
}
