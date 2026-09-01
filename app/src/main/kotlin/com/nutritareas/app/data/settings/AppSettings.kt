package com.nutritareas.app.data.settings

data class AppSettings(
    val activeProvider: AssistantProvider = AssistantProvider.CLAUDE,
    val claudeApiKey: String? = null,
    val geminiApiKey: String? = null,
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

    /** Not user-configurable - picking between model tiers was pure margin for error for a single
     *  non-technical user, so each provider is pinned to the model that best balances quality and
     *  speed for a homework-helper chat: Sonnet over Opus/Haiku for Claude, Flash over Pro/Flash-Lite
     *  for Gemini. */
    val activeModelId: String
        get() = when (activeProvider) {
            AssistantProvider.CLAUDE -> CLAUDE_MODEL_ID
            AssistantProvider.GEMINI -> GEMINI_MODEL_ID
        }

    val hasActiveApiKey: Boolean get() = !activeApiKey.isNullOrBlank()

    companion object {
        const val CLAUDE_MODEL_ID = "claude-sonnet-5"
        const val GEMINI_MODEL_ID = "gemini-flash-latest"
    }
}
