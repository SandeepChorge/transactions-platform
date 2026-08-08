package com.madtitan94.transactionsparser.feature.profile.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.madtitan94.transactionsparser.core.designsystem.components.LoadingIndicator
import com.madtitan94.transactionsparser.core.designsystem.theme.TransactionsParserTheme
import com.madtitan94.transactionsparser.core.presentation.ObserveAsEvents
import com.madtitan94.transactionsparser.feature.profile.domain.Gender
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun ProfileRoot(
    viewModel: ProfileViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ProfileEvent.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(event.message.asString(context))
            }
        }
    }

    ProfileScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: ProfileState,
    snackbarHostState: SnackbarHostState,
    onAction: (ProfileAction) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.profile_title)) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { onAction(ProfileAction.OnNameChange(it)) },
                label = { Text(stringResource(R.string.profile_name_label)) },
                enabled = state.isEditing,
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(it.asString()) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.mobile,
                onValueChange = { onAction(ProfileAction.OnMobileChange(it)) },
                label = { Text(stringResource(R.string.profile_mobile_label)) },
                enabled = state.isEditing,
                isError = state.mobileError != null,
                supportingText = state.mobileError?.let { { Text(it.asString()) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.email,
                onValueChange = {},
                label = { Text(stringResource(R.string.profile_email_label)) },
                enabled = false,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            GenderDropdown(
                selected = state.gender,
                enabled = state.isEditing,
                onSelect = { onAction(ProfileAction.OnGenderSelect(it)) }
            )

            if (state.isEditing) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onAction(ProfileAction.OnSaveClick) },
                        enabled = !state.isSaving,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.profile_save))
                    }
                    OutlinedButton(
                        onClick = { onAction(ProfileAction.OnCancelEdit) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.profile_cancel))
                    }
                }
            } else {
                Button(
                    onClick = { onAction(ProfileAction.OnEditClick) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Text(
                        text = stringResource(R.string.profile_edit),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            OutlinedButton(
                onClick = { onAction(ProfileAction.OnLogoutClick) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Text(
                    text = stringResource(R.string.profile_logout),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenderDropdown(
    selected: Gender?,
    enabled: Boolean,
    onSelect: (Gender?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (selected) {
        Gender.MALE -> stringResource(R.string.gender_male)
        Gender.FEMALE -> stringResource(R.string.gender_female)
        Gender.OTHER -> stringResource(R.string.gender_other)
        null -> stringResource(R.string.gender_unset)
    }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.profile_gender_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled)
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.gender_unset)) },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            Gender.entries.forEach { gender ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (gender) {
                                Gender.MALE -> stringResource(R.string.gender_male)
                                Gender.FEMALE -> stringResource(R.string.gender_female)
                                Gender.OTHER -> stringResource(R.string.gender_other)
                            }
                        )
                    },
                    onClick = {
                        onSelect(gender)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    TransactionsParserTheme {
        ProfileScreen(
            state = ProfileState(
                isLoading = false,
                email = "sandeep@codengine.co",
                name = "Sandeep",
                mobile = "9766789876"
            ),
            snackbarHostState = SnackbarHostState(),
            onAction = {}
        )
    }
}
