package com.nutritareas.app.ui.settings

import com.nutritareas.app.data.settings.AssistantProvider

data class SettingsUiState(
    val activeProvider: AssistantProvider = AssistantProvider.CLAUDE,

    val isEditingClaudeKey: Boolean = true,
    val claudeKeyInput: String = "",
    val hasStoredClaudeKey: Boolean = false,

    val isEditingGeminiKey: Boolean = true,
    val geminiKeyInput: String = "",
    val hasStoredGeminiKey: Boolean = false,

    val isEditingTemplateWebAppUrl: Boolean = true,
    val templateWebAppUrlInput: String = "",
    val hasStoredTemplateWebAppUrl: Boolean = false,

    val currentVersionName: String = "",
    val infoMessage: String? = null,
    val errorMessage: String? = null,
)
