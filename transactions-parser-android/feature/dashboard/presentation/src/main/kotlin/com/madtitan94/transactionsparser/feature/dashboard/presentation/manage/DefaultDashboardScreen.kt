package com.madtitan94.transactionsparser.feature.dashboard.presentation.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madtitan94.transactionsparser.core.designsystem.components.LoadingIndicator
import com.madtitan94.transactionsparser.core.designsystem.theme.AppDimens
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTheme
import com.madtitan94.transactionsparser.core.designsystem.theme.AppTypography
import com.madtitan94.transactionsparser.feature.dashboard.presentation.R
import com.madtitan94.transactionsparser.feature.dashboard.presentation.dashboardName
import org.koin.androidx.compose.koinViewModel

@Composable
fun DefaultDashboardRoot(
    onBack: () -> Unit,
    viewModel: DashboardLayoutViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DefaultDashboardScreen(
        state = state,
        onBack = onBack,
        onAction = viewModel::onAction
    )
}

/**
 * Which dashboard Home opens on.
 *
 * A screen of its own rather than a star on the manage list, which is where
 * `design/DashboardSpec.dc.html` puts it. Two controls on one row — a switch that hides a dashboard
 * and a star that opens on it — read as one control with two states, and the pair has an
 * unrepresentable combination: starring something you have just switched off.
 *
 * Only the switched-on dashboards are offered, for the same reason. There is nothing to warn about
 * and no invalid choice to make, because the invalid ones are not on the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultDashboardScreen(
    state: DashboardLayoutState,
    onBack: () -> Unit,
    onAction: (DashboardLayoutAction) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dash_default_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.dash_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            LoadingIndicator(Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.screenHorizontalPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.dash_default_note),
                style = AppTypography.body,
                color = AppTheme.colors.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            state.dashboards.filter { it.isEnabled }.forEach { entry ->
                val selected = entry.key == state.defaultKey
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = {
                                onAction(DashboardLayoutAction.OnDefaultSelected(entry.key))
                            }
                        )
                        .heightIn(min = 48.dp)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // The whole row is the target; the button would otherwise be a second, smaller
                    // one sitting inside it.
                    RadioButton(selected = selected, onClick = null)
                    Text(
                        text = dashboardName(entry.definition),
                        style = AppTypography.row,
                        color = AppTheme.colors.textPrimary,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }
    }
}
