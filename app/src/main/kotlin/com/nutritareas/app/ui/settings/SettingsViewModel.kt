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
                val option = ModelOption.fromModelId(settings.modelId)
                _uiState.update {
                    it.copy(
                        hasStoredApiKey = settings.hasApiKey,
                        isEditingApiKey = it.isEditingApiKey && !settings.hasApiKey,
                        selectedModelOption = option,
                        customModelId = if (option == ModelOption.CUSTOM) settings.modelId else it.customModelId,
                    )
                }
            }
        }
    }

    fun onApiKeyInputChange(value: String) {
        _uiState.update { it.copy(apiKeyInput = value) }
    }

    fun onStartEditingApiKey() {
        _uiState.update { it.copy(isEditingApiKey = true, apiKeyInput = "") }
    }

    fun onCancelEditingApiKey() {
        _uiState.update { it.copy(isEditingApiKey = !it.hasStoredApiKey, apiKeyInput = "") }
    }

    fun onSaveApiKey() {
        val app = getApplication<Application>()
        val key = _uiState.value.apiKeyInput.trim()
        if (key.isEmpty()) return
        viewModelScope.launch {
            settingsRepository.saveApiKey(key)
            _uiState.update {
                it.copy(isEditingApiKey = false, apiKeyInput = "", infoMessage = app.getString(R.string.api_key_saved))
            }
        }
    }

    fun onClearApiKey() {
        viewModelScope.launch {
            settingsRepository.clearApiKey()
            _uiState.update { it.copy(isEditingApiKey = true, apiKeyInput = "") }
        }
    }

    fun onModelOptionSelected(option: ModelOption) {
        _uiState.update { it.copy(selectedModelOption = option) }
        if (option != ModelOption.CUSTOM) {
            saveModelId(requireNotNull(option.modelId))
        }
    }

    fun onCustomModelIdChange(value: String) {
        _uiState.update { it.copy(customModelId = value) }
        if (value.isNotBlank()) saveModelId(value.trim())
    }

    private fun saveModelId(modelId: String) {
        viewModelScope.launch { settingsRepository.saveModelId(modelId) }
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
