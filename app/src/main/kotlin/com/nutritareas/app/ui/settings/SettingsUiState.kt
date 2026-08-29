package com.nutritareas.app.ui.settings

import com.nutritareas.app.data.settings.AppSettings
import com.nutritareas.app.data.settings.AssistantProvider

enum class ClaudeModelOption(val modelId: String?) {
    OPUS("claude-opus-5"),
    SONNET("claude-sonnet-5"),
    HAIKU("claude-haiku-4-5"),
    CUSTOM(null),
    ;

    companion object {
        fun fromModelId(modelId: String): ClaudeModelOption = entries.firstOrNull { it.modelId == modelId } ?: CUSTOM
    }
}

enum class GeminiModelOption(val modelId: String?) {
    PRO("gemini-pro-latest"),
    FLASH("gemini-flash-latest"),
    FLASH_LITE("gemini-flash-lite-latest"),
    CUSTOM(null),
    ;

    companion object {
        fun fromModelId(modelId: String): GeminiModelOption = entries.firstOrNull { it.modelId == modelId } ?: CUSTOM
    }
}

data class SettingsUiState(
    val activeProvider: AssistantProvider = AssistantProvider.CLAUDE,

    val isEditingClaudeKey: Boolean = true,
    val claudeKeyInput: String = "",
    val hasStoredClaudeKey: Boolean = false,
    val selectedClaudeModelOption: ClaudeModelOption = ClaudeModelOption.fromModelId(AppSettings.DEFAULT_CLAUDE_MODEL_ID),
    val customClaudeModelId: String = "",

    val isEditingGeminiKey: Boolean = true,
    val geminiKeyInput: String = "",
    val hasStoredGeminiKey: Boolean = false,
    val selectedGeminiModelOption: GeminiModelOption = GeminiModelOption.fromModelId(AppSettings.DEFAULT_GEMINI_MODEL_ID),
    val customGeminiModelId: String = "",

    val currentVersionName: String = "",
    val infoMessage: String? = null,
    val errorMessage: String? = null,
)
