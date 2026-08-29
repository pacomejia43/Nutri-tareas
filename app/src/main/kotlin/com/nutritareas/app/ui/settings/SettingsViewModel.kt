package com.nutritareas.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nutritareas.app.BuildConfig
import com.nutritareas.app.NutriTareasApp
import com.nutritareas.app.R
import com.nutritareas.app.data.settings.AssistantProvider
import com.nutritareas.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState(currentVersionName = BuildConfig.VERSION_NAME))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collectLatest { settings ->
                val claudeOption = ClaudeModelOption.fromModelId(settings.claudeModelId)
                val geminiOption = GeminiModelOption.fromModelId(settings.geminiModelId)
                _uiState.update {
                    it.copy(
                        activeProvider = settings.activeProvider,
                        hasStoredClaudeKey = settings.hasClaudeApiKey,
                        isEditingClaudeKey = it.isEditingClaudeKey && !settings.hasClaudeApiKey,
                        selectedClaudeModelOption = claudeOption,
                        customClaudeModelId = if (claudeOption == ClaudeModelOption.CUSTOM) {
                            settings.claudeModelId
                        } else {
                            it.customClaudeModelId
                        },
                        hasStoredGeminiKey = settings.hasGeminiApiKey,
                        isEditingGeminiKey = it.isEditingGeminiKey && !settings.hasGeminiApiKey,
                        selectedGeminiModelOption = geminiOption,
                        customGeminiModelId = if (geminiOption == GeminiModelOption.CUSTOM) {
                            settings.geminiModelId
                        } else {
                            it.customGeminiModelId
                        },
                    )
                }
            }
        }
    }

    fun onProviderSelected(provider: AssistantProvider) {
        viewModelScope.launch { settingsRepository.saveActiveProvider(provider) }
    }

    // --- Claude ---

    fun onClaudeKeyInputChange(value: String) {
        _uiState.update { it.copy(claudeKeyInput = value) }
    }

    fun onStartEditingClaudeKey() {
        _uiState.update { it.copy(isEditingClaudeKey = true, claudeKeyInput = "") }
    }

    fun onCancelEditingClaudeKey() {
        _uiState.update { it.copy(isEditingClaudeKey = !it.hasStoredClaudeKey, claudeKeyInput = "") }
    }

    fun onSaveClaudeKey() {
        val app = getApplication<Application>()
        val key = _uiState.value.claudeKeyInput.trim()
        if (key.isEmpty()) return
        viewModelScope.launch {
            settingsRepository.saveClaudeApiKey(key)
            _uiState.update {
                it.copy(isEditingClaudeKey = false, claudeKeyInput = "", infoMessage = app.getString(R.string.api_key_saved))
            }
        }
    }

    fun onClearClaudeKey() {
        viewModelScope.launch {
            settingsRepository.clearClaudeApiKey()
            _uiState.update { it.copy(isEditingClaudeKey = true, claudeKeyInput = "") }
        }
    }

    fun onClaudeModelOptionSelected(option: ClaudeModelOption) {
        _uiState.update { it.copy(selectedClaudeModelOption = option) }
        if (option != ClaudeModelOption.CUSTOM) {
            viewModelScope.launch { settingsRepository.saveClaudeModelId(requireNotNull(option.modelId)) }
        }
    }

    fun onCustomClaudeModelIdChange(value: String) {
        _uiState.update { it.copy(customClaudeModelId = value) }
        if (value.isNotBlank()) viewModelScope.launch { settingsRepository.saveClaudeModelId(value.trim()) }
    }

    // --- Gemini ---

    fun onGeminiKeyInputChange(value: String) {
        _uiState.update { it.copy(geminiKeyInput = value) }
    }

    fun onStartEditingGeminiKey() {
        _uiState.update { it.copy(isEditingGeminiKey = true, geminiKeyInput = "") }
    }

    fun onCancelEditingGeminiKey() {
        _uiState.update { it.copy(isEditingGeminiKey = !it.hasStoredGeminiKey, geminiKeyInput = "") }
    }

    fun onSaveGeminiKey() {
        val app = getApplication<Application>()
        val key = _uiState.value.geminiKeyInput.trim()
        if (key.isEmpty()) return
        viewModelScope.launch {
            settingsRepository.saveGeminiApiKey(key)
            _uiState.update {
                it.copy(isEditingGeminiKey = false, geminiKeyInput = "", infoMessage = app.getString(R.string.api_key_saved))
            }
        }
    }

    fun onClearGeminiKey() {
        viewModelScope.launch {
            settingsRepository.clearGeminiApiKey()
            _uiState.update { it.copy(isEditingGeminiKey = true, geminiKeyInput = "") }
        }
    }

    fun onGeminiModelOptionSelected(option: GeminiModelOption) {
        _uiState.update { it.copy(selectedGeminiModelOption = option) }
        if (option != GeminiModelOption.CUSTOM) {
            viewModelScope.launch { settingsRepository.saveGeminiModelId(requireNotNull(option.modelId)) }
        }
    }

    fun onCustomGeminiModelIdChange(value: String) {
        _uiState.update { it.copy(customGeminiModelId = value) }
        if (value.isNotBlank()) viewModelScope.launch { settingsRepository.saveGeminiModelId(value.trim()) }
    }

    fun onInfoMessageShown() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    fun onErrorMessageShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory {
            val container = (application as NutriTareasApp).container
            return viewModelFactory {
                initializer {
                    SettingsViewModel(application = application, settingsRepository = container.settingsRepository)
                }
            }
        }
    }
}
