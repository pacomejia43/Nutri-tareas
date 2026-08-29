package com.nutritareas.app.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutritareas.app.R
import com.nutritareas.app.ui.update.UpdateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(context.applicationContext as Application),
    )
    val updateViewModel: UpdateViewModel = viewModel(
        factory = UpdateViewModel.factory(context.applicationContext as Application),
    )
    val uiState by viewModel.uiState.collectAsState()
    val updateState by updateViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onInfoMessageShown()
        }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            SectionTitle(stringResource(R.string.api_key_label))
            ApiKeySection(uiState = uiState, viewModel = viewModel)

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            SectionTitle(stringResource(R.string.model_label))
            ModelSection(uiState = uiState, viewModel = viewModel)

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            SectionTitle(stringResource(R.string.about_title))
            Text(stringResource(R.string.current_version, uiState.currentVersionName), modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { updateViewModel.checkForUpdate(silent = false) }, enabled = !updateState.isChecking) {
                    Text(stringResource(R.string.check_updates))
                }
                if (updateState.isChecking) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.checking_updates), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!updateState.isChecking) {
                val statusText = updateState.infoMessage ?: updateState.errorMessage
                if (statusText != null) {
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (updateState.errorMessage != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ApiKeySection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    if (uiState.isEditingApiKey) {
        Column {
            OutlinedTextField(
                value = uiState.apiKeyInput,
                onValueChange = viewModel::onApiKeyInputChange,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                placeholder = { Text(stringResource(R.string.api_key_placeholder)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
            )
            Text(
                stringResource(R.string.api_key_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Row {
                Button(onClick = viewModel::onSaveApiKey, enabled = uiState.apiKeyInput.isNotBlank()) {
                    Text(stringResource(R.string.save))
                }
                if (uiState.hasStoredApiKey) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = viewModel::onCancelEditingApiKey) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.api_key_saved), modifier = Modifier.weight(1f))
            TextButton(onClick = viewModel::onStartEditingApiKey) { Text(stringResource(R.string.save)) }
            TextButton(onClick = viewModel::onClearApiKey) { Text(stringResource(R.string.api_key_clear)) }
        }
    }
}

@Composable
private fun ModelSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        ModelRadioOption(
            label = stringResource(R.string.model_opus),
            selected = uiState.selectedModelOption == ModelOption.OPUS,
            onClick = { viewModel.onModelOptionSelected(ModelOption.OPUS) },
        )
        ModelRadioOption(
            label = stringResource(R.string.model_sonnet),
            selected = uiState.selectedModelOption == ModelOption.SONNET,
            onClick = { viewModel.onModelOptionSelected(ModelOption.SONNET) },
        )
        ModelRadioOption(
            label = stringResource(R.string.model_haiku),
            selected = uiState.selectedModelOption == ModelOption.HAIKU,
            onClick = { viewModel.onModelOptionSelected(ModelOption.HAIKU) },
        )
        ModelRadioOption(
            label = stringResource(R.string.model_custom),
            selected = uiState.selectedModelOption == ModelOption.CUSTOM,
            onClick = { viewModel.onModelOptionSelected(ModelOption.CUSTOM) },
        )
        if (uiState.selectedModelOption == ModelOption.CUSTOM) {
            OutlinedTextField(
                value = uiState.customModelId,
                onValueChange = viewModel::onCustomModelIdChange,
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 4.dp),
                label = { Text(stringResource(R.string.model_custom_label)) },
                singleLine = true,
            )
        }
    }
}

@Composable
private fun ModelRadioOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}
