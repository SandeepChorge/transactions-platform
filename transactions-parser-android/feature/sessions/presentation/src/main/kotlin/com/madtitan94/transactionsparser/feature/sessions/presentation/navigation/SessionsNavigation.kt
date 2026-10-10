package com.madtitan94.transactionsparser.feature.sessions.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavBackStackEntry
import androidx.navigation.FloatingWindow
import com.madtitan94.transactionsparser.feature.sessions.presentation.detail.SessionDetailAction
import com.madtitan94.transactionsparser.feature.sessions.presentation.detail.SessionDetailViewModel
import com.madtitan94.transactionsparser.feature.sessions.presentation.payee.PayeeDetailAction
import com.madtitan94.transactionsparser.feature.sessions.presentation.payee.PayeeDetailViewModel
import org.koin.androidx.compose.koinViewModel
import com.madtitan94.transactionsparser.feature.sessions.presentation.detail.SessionDetailRoot
import com.madtitan94.transactionsparser.feature.sessions.presentation.history.SessionsHistoryRoot
import com.madtitan94.transactionsparser.feature.sessions.presentation.payee.PayeeDetailRoot
import com.madtitan94.transactionsparser.feature.sessions.presentation.payee.PayeeDirectoryRoot
import com.madtitan94.transactionsparser.feature.sessions.presentation.search.TransactionSearchRoot
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

/** The Payees tab — the directory that [PayeeDetailRoute] is opened from. */
@Serializable
data object PayeeDirectoryRoute

/**
 * Global search over every transaction the account holds.
 *
 * A destination of its own rather than a mode of an existing list. Search is reached from the
 * dashboard, ranges across every statement, and holds a keyboard the whole time it is open — folding
 * it into a tab would put a text field permanently above a screen that is mostly read, and pressing
 * back would have to mean two different things depending on whether anything had been typed.
 */
@Serializable
data object TransactionSearchRoute

fun NavGraphBuilder.sessionsGraph(navController: NavController) {
    composable<SessionsHistoryRoute> {
        SessionsHistoryRoot(
            onOpenSession = { sessionId -> navController.navigate(SessionDetailRoute(sessionId)) },
            onOpenUploadHistory = { navController.navigate(UploadHistoryRoute) }
        )
    }
    composable<SessionDetailRoute> { entry ->
        val viewModel: SessionDetailViewModel = koinViewModel()
        OnMappingDestinationLeft(navController, entry) {
            viewModel.onAction(SessionDetailAction.OnLeaveMapping)
        }
        SessionDetailRoot(
            viewModel = viewModel,
            onBack = { navController.navigateUp() },
            onOpenPayee = { normalizedPayee, rawPayee ->
                navController.navigate(PayeeDetailRoute(normalizedPayee, rawPayee))
            }
        )
    }
    composable<PayeeDirectoryRoute> {
        PayeeDirectoryRoot(
            onOpenPayee = { normalizedPayee, rawPayee ->
                navController.navigate(PayeeDetailRoute(normalizedPayee, rawPayee))
            }
        )
    }
    composable<PayeeDetailRoute> { entry ->
        val viewModel: PayeeDetailViewModel = koinViewModel()
        OnMappingDestinationLeft(navController, entry) {
            viewModel.onAction(PayeeDetailAction.OnLeaveMapping)
        }
        PayeeDetailRoot(onBack = { navController.navigateUp() }, viewModel = viewModel)
    }
    composable<UploadHistoryRoute> {
        UploadHistoryRoot(onBack = { navController.navigateUp() })
    }
    composable<TransactionSearchRoute> {
        TransactionSearchRoot(
            onBack = { navController.navigateUp() },
            onOpenPayee = { normalizedPayee, rawPayee ->
                navController.navigate(PayeeDetailRoute(normalizedPayee, rawPayee))
            }
        )
    }
}

/**
 * A mapping draft belongs to one visit, even when Navigation retains the destination's ViewModel.
 * Observe destination changes rather than lifecycle stops: backgrounding, rotation and dialogs
 * are not departures from the mapping screen.
 */
@Composable
internal fun OnMappingDestinationLeft(
    navController: NavController,
    entry: NavBackStackEntry,
    onLeave: () -> Unit
) {
    val currentOnLeave by rememberUpdatedState(onLeave)
    DisposableEffect(navController, entry) {
        var wasCurrent = navController.currentBackStackEntry?.id == entry.id
        val listener = NavController.OnDestinationChangedListener { controller, destination, _ ->
            if (destination !is FloatingWindow) {
                val isCurrent = controller.currentBackStackEntry?.id == entry.id
                if (wasCurrent && !isCurrent) currentOnLeave()
                wasCurrent = isCurrent
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
}
