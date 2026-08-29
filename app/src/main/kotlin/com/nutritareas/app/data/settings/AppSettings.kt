package com.nutritareas.app.data.settings

data class AppSettings(
    val activeProvider: AssistantProvider = AssistantProvider.CLAUDE,
    val claudeApiKey: String? = null,
    val claudeModelId: String = DEFAULT_CLAUDE_MODEL_ID,
    val geminiApiKey: String? = null,
    val geminiModelId: String = DEFAULT_GEMINI_MODEL_ID,
    val lastSeenReleaseTag: String? = null,
    val templateWebAppUrl: String? = null,
) {
    val hasClaudeApiKey: Boolean get() = !claudeApiKey.isNullOrBlank()
    val hasGeminiApiKey: Boolean get() = !geminiApiKey.isNullOrBlank()
    val hasTemplateWebAppUrl: Boolean get() = !templateWebAppUrl.isNullOrBlank()

    /** API key for whichever provider is active right now - what the assistant client actually uses. */
    val activeApiKey: String?
        get() = when (activeProvider) {
            AssistantProvider.CLAUDE -> claudeApiKey
            AssistantProvider.GEMINI -> geminiApiKey
        }

    val activeModelId: String
        get() = when (activeProvider) {
            AssistantProvider.CLAUDE -> claudeModelId
            AssistantProvider.GEMINI -> geminiModelId
        }

    val hasActiveApiKey: Boolean get() = !activeApiKey.isNullOrBlank()

    companion object {
        const val DEFAULT_CLAUDE_MODEL_ID = "claude-opus-5"
        const val DEFAULT_GEMINI_MODEL_ID = "gemini-pro-latest"
    }
}
