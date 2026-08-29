package com.nutritareas.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nutritareas.app.data.crypto.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "nutri_tareas_settings")

/** Persists user settings. Both API keys are encrypted at rest via [CryptoManager] before hitting disk. */
class SettingsRepository(
    private val context: Context,
    private val cryptoManager: CryptoManager = CryptoManager(),
) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            activeProvider = prefs[Keys.ACTIVE_PROVIDER]?.let { runCatching { AssistantProvider.valueOf(it) }.getOrNull() }
                ?: AssistantProvider.CLAUDE,
            claudeApiKey = prefs[Keys.ENCRYPTED_CLAUDE_API_KEY]?.let { cryptoManager.decrypt(it) },
            claudeModelId = prefs[Keys.CLAUDE_MODEL_ID] ?: AppSettings.DEFAULT_CLAUDE_MODEL_ID,
            geminiApiKey = prefs[Keys.ENCRYPTED_GEMINI_API_KEY]?.let { cryptoManager.decrypt(it) },
            geminiModelId = prefs[Keys.GEMINI_MODEL_ID] ?: AppSettings.DEFAULT_GEMINI_MODEL_ID,
            lastSeenReleaseTag = prefs[Keys.LAST_SEEN_RELEASE_TAG],
        )
    }

    suspend fun currentSettings(): AppSettings = settings.first()

    suspend fun saveActiveProvider(provider: AssistantProvider) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.ACTIVE_PROVIDER] = provider.name }
    }

    suspend fun saveClaudeApiKey(apiKey: String) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.ENCRYPTED_CLAUDE_API_KEY] = cryptoManager.encrypt(apiKey) }
    }

    suspend fun clearClaudeApiKey() {
        context.settingsDataStore.edit { prefs -> prefs.remove(Keys.ENCRYPTED_CLAUDE_API_KEY) }
    }

    suspend fun saveClaudeModelId(modelId: String) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.CLAUDE_MODEL_ID] = modelId }
    }

    suspend fun saveGeminiApiKey(apiKey: String) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.ENCRYPTED_GEMINI_API_KEY] = cryptoManager.encrypt(apiKey) }
    }

    suspend fun clearGeminiApiKey() {
        context.settingsDataStore.edit { prefs -> prefs.remove(Keys.ENCRYPTED_GEMINI_API_KEY) }
    }

    suspend fun saveGeminiModelId(modelId: String) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.GEMINI_MODEL_ID] = modelId }
    }

    suspend fun saveLastSeenReleaseTag(tag: String) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.LAST_SEEN_RELEASE_TAG] = tag }
    }

    private object Keys {
        val ACTIVE_PROVIDER = stringPreferencesKey("active_provider")
        val ENCRYPTED_CLAUDE_API_KEY = stringPreferencesKey("encrypted_claude_api_key")
        val CLAUDE_MODEL_ID = stringPreferencesKey("claude_model_id")
        val ENCRYPTED_GEMINI_API_KEY = stringPreferencesKey("encrypted_gemini_api_key")
        val GEMINI_MODEL_ID = stringPreferencesKey("gemini_model_id")
        val LAST_SEEN_RELEASE_TAG = stringPreferencesKey("last_seen_release_tag")
    }
}
