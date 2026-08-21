package com.madtitan94.transactionsparser.feature.settings.presentation.deleted

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madtitan94.transactionsparser.core.designsystem.components.AppAlertDialog
import com.madtitan94.transactionsparser.core.designsystem.components.EmptyState
import com.madtitan94.transactionsparser.core.designsystem.components.ListRow
import com.madtitan94.transactionsparser.core.designsystem.components.LoadingIndicator
import com.madtitan94.transactionsparser.core.presentation.ObserveAsEvents
import com.madtitan94.transactionsparser.feature.settings.presentation.R
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun RecentlyDeletedRoot(
    onBack: () -> Unit,
    viewModel: RecentlyDeletedViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is RecentlyDeletedEvent.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(event.message.asString(context))
            }
        }
    }

    RecentlyDeletedScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentlyDeletedScreen(
    state: RecentlyDeletedState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAction: (RecentlyDeletedAction) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recently_deleted_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.recently_deleted_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            state.isLoading -> LoadingIndicator(Modifier.padding(padding))

            state.categories.isEmpty() -> EmptyState(
                title = stringResource(R.string.recently_deleted_empty_title),
                description = stringResource(R.string.recently_deleted_empty_description),
                modifier = Modifier.padding(padding)
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(state.categories, key = { it.id }) { category ->
                    ListRow(
                        title = category.name,
                        supporting = stringResource(R.string.recently_deleted_category),
                        dimmed = true,
                        trailing = {
                            TextButton(
                                onClick = { onAction(RecentlyDeletedAction.OnRestoreClick(category)) }
                            ) {
                                Text(stringResource(R.string.recently_deleted_restore))
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }

        state.pendingRestore?.let { prompt ->
            AppAlertDialog(
                title = stringResource(R.string.recently_deleted_restore),
                message = stringResource(R.string.recently_deleted_restore_confirm, prompt.name),
                confirmLabel = stringResource(R.string.recently_deleted_restore),
                dismissLabel = stringResource(R.string.settings_cancel),
                onConfirm = { onAction(RecentlyDeletedAction.OnConfirmRestore) },
                onDismiss = { onAction(RecentlyDeletedAction.OnDismissRestore) }
            )
        }
    }
}
