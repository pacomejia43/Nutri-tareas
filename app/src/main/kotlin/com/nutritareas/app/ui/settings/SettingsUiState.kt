package com.nutritareas.app.ui.settings

import com.nutritareas.app.data.settings.AppSettings

enum class ModelOption(val modelId: String?) {
    OPUS("claude-opus-5"),
    SONNET("claude-sonnet-5"),
    HAIKU("claude-haiku-4-5"),
    CUSTOM(null),
    ;

    companion object {
        fun fromModelId(modelId: String): ModelOption = entries.firstOrNull { it.modelId == modelId } ?: CUSTOM
    }
}

data class SettingsUiState(
    val isEditingApiKey: Boolean = true,
    val apiKeyInput: String = "",
    val hasStoredApiKey: Boolean = false,
    val selectedModelOption: ModelOption = ModelOption.fromModelId(AppSettings.DEFAULT_MODEL_ID),
    val customModelId: String = "",
    val currentVersionName: String = "",
    val infoMessage: String? = null,
    val errorMessage: String? = null,
)
