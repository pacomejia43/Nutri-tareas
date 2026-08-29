package com.nutritareas.app.data.settings

data class AppSettings(
    val apiKey: String? = null,
    val modelId: String = DEFAULT_MODEL_ID,
    val lastSeenReleaseTag: String? = null,
) {
    val hasApiKey: Boolean get() = !apiKey.isNullOrBlank()

    companion object {
        const val DEFAULT_MODEL_ID = "claude-opus-5"
    }
}
