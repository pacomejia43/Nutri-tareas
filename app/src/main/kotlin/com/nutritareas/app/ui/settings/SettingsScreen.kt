package com.nutritareas.app.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.nutritareas.app.data.settings.AssistantProvider
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
            SectionTitle(stringResource(R.string.provider_section_title))
            Text(
                stringResource(R.string.provider_section_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            ProviderToggle(activeProvider = uiState.activeProvider, onSelect = viewModel::onProviderSelected)

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            SectionTitle(stringResource(R.string.provider_claude))
            ApiKeySection(
                isEditing = uiState.isEditingClaudeKey,
                keyInput = uiState.claudeKeyInput,
                onKeyInputChange = viewModel::onClaudeKeyInputChange,
                hasStoredKey = uiState.hasStoredClaudeKey,
                onSave = viewModel::onSaveClaudeKey,
                onCancel = viewModel::onCancelEditingClaudeKey,
                onStartEditing = viewModel::onStartEditingClaudeKey,
                onClear = viewModel::onClearClaudeKey,
                placeholder = stringResource(R.string.claude_api_key_placeholder),
                hint = stringResource(R.string.claude_api_key_hint),
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.model_label), style = MaterialTheme.typography.titleSmall)
            ModelOptionsColumn(
                options = listOf(
                    ModelRadioItem(
                        label = stringResource(R.string.model_opus),
                        selected = uiState.selectedClaudeModelOption == ClaudeModelOption.OPUS,
                        onClick = { viewModel.onClaudeModelOptionSelected(ClaudeModelOption.OPUS) },
                    ),
                    ModelRadioItem(
                        label = stringResource(R.string.model_sonnet),
                        selected = uiState.selectedClaudeModelOption == ClaudeModelOption.SONNET,
                        onClick = { viewModel.onClaudeModelOptionSelected(ClaudeModelOption.SONNET) },
                    ),
                    ModelRadioItem(
                        label = stringResource(R.string.model_haiku),
                        selected = uiState.selectedClaudeModelOption == ClaudeModelOption.HAIKU,
                        onClick = { viewModel.onClaudeModelOptionSelected(ClaudeModelOption.HAIKU) },
                    ),
                    ModelRadioItem(
                        label = stringResource(R.string.model_custom),
                        selected = uiState.selectedClaudeModelOption == ClaudeModelOption.CUSTOM,
                        onClick = { viewModel.onClaudeModelOptionSelected(ClaudeModelOption.CUSTOM) },
                    ),
                ),
                showCustomField = uiState.selectedClaudeModelOption == ClaudeModelOption.CUSTOM,
                customModelId = uiState.customClaudeModelId,
                onCustomModelIdChange = viewModel::onCustomClaudeModelIdChange,
                customFieldLabel = stringResource(R.string.model_custom_label),
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            SectionTitle(stringResource(R.string.provider_gemini))
            ApiKeySection(
                isEditing = uiState.isEditingGeminiKey,
                keyInput = uiState.geminiKeyInput,
                onKeyInputChange = viewModel::onGeminiKeyInputChange,
                hasStoredKey = uiState.hasStoredGeminiKey,
                onSave = viewModel::onSaveGeminiKey,
                onCancel = viewModel::onCancelEditingGeminiKey,
                onStartEditing = viewModel::onStartEditingGeminiKey,
                onClear = viewModel::onClearGeminiKey,
                placeholder = stringResource(R.string.gemini_api_key_placeholder),
                hint = stringResource(R.string.gemini_api_key_hint),
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.model_label), style = MaterialTheme.typography.titleSmall)
            ModelOptionsColumn(
                options = listOf(
                    ModelRadioItem(
                        label = stringResource(R.string.model_gemini_pro),
                        selected = uiState.selectedGeminiModelOption == GeminiModelOption.PRO,
                        onClick = { viewModel.onGeminiModelOptionSelected(GeminiModelOption.PRO) },
                    ),
                    ModelRadioItem(
                        label = stringResource(R.string.model_gemini_flash),
                        selected = uiState.selectedGeminiModelOption == GeminiModelOption.FLASH,
                        onClick = { viewModel.onGeminiModelOptionSelected(GeminiModelOption.FLASH) },
                    ),
                    ModelRadioItem(
                        label = stringResource(R.string.model_gemini_flash_lite),
                        selected = uiState.selectedGeminiModelOption == GeminiModelOption.FLASH_LITE,
                        onClick = { viewModel.onGeminiModelOptionSelected(GeminiModelOption.FLASH_LITE) },
                    ),
                    ModelRadioItem(
                        label = stringResource(R.string.model_custom),
                        selected = uiState.selectedGeminiModelOption == GeminiModelOption.CUSTOM,
                        onClick = { viewModel.onGeminiModelOptionSelected(GeminiModelOption.CUSTOM) },
                    ),
                ),
                showCustomField = uiState.selectedGeminiModelOption == GeminiModelOption.CUSTOM,
                customModelId = uiState.customGeminiModelId,
                onCustomModelIdChange = viewModel::onCustomGeminiModelIdChange,
                customFieldLabel = stringResource(R.string.model_custom_label),
            )

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderToggle(activeProvider: AssistantProvider, onSelect: (AssistantProvider) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = activeProvider == AssistantProvider.CLAUDE,
            onClick = { onSelect(AssistantProvider.CLAUDE) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = { Text(stringResource(R.string.provider_claude)) },
        )
        SegmentedButton(
            selected = activeProvider == AssistantProvider.GEMINI,
            onClick = { onSelect(AssistantProvider.GEMINI) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = { Text(stringResource(R.string.provider_gemini)) },
        )
    }
}

@Composable
private fun ApiKeySection(
    isEditing: Boolean,
    keyInput: String,
    onKeyInputChange: (String) -> Unit,
    hasStoredKey: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onStartEditing: () -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    hint: String,
) {
    if (isEditing) {
        Column {
            OutlinedTextField(
                value = keyInput,
                onValueChange = onKeyInputChange,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                placeholder = { Text(placeholder) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
            )
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Row {
                Button(onClick = onSave, enabled = keyInput.isNotBlank()) {
                    Text(stringResource(R.string.save))
                }
                if (hasStoredKey) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.api_key_saved), modifier = Modifier.weight(1f))
            TextButton(onClick = onStartEditing) { Text(stringResource(R.string.save)) }
            TextButton(onClick = onClear) { Text(stringResource(R.string.api_key_clear)) }
        }
    }
}

private data class ModelRadioItem(val label: String, val selected: Boolean, val onClick: () -> Unit)

@Composable
private fun ModelOptionsColumn(
    options: List<ModelRadioItem>,
    showCustomField: Boolean,
    customModelId: String,
    onCustomModelIdChange: (String) -> Unit,
    customFieldLabel: String,
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        options.forEach { item ->
            ModelRadioOption(label = item.label, selected = item.selected, onClick = item.onClick)
        }
        if (showCustomField) {
            OutlinedTextField(
                value = customModelId,
                onValueChange = onCustomModelIdChange,
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 4.dp),
                label = { Text(customFieldLabel) },
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
