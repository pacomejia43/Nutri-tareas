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

/** Persists user settings. The API key is encrypted at rest via [CryptoManager] before hitting disk. */
class SettingsRepository(
    private val context: Context,
    private val cryptoManager: CryptoManager = CryptoManager(),
) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            apiKey = prefs[Keys.ENCRYPTED_API_KEY]?.let { cryptoManager.decrypt(it) },
            modelId = prefs[Keys.MODEL_ID] ?: AppSettings.DEFAULT_MODEL_ID,
            lastSeenReleaseTag = prefs[Keys.LAST_SEEN_RELEASE_TAG],
        )
    }

    suspend fun currentSettings(): AppSettings = settings.first()

    suspend fun saveApiKey(apiKey: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.ENCRYPTED_API_KEY] = cryptoManager.encrypt(apiKey)
        }
    }

    suspend fun clearApiKey() {
        context.settingsDataStore.edit { prefs -> prefs.remove(Keys.ENCRYPTED_API_KEY) }
    }

    suspend fun saveModelId(modelId: String) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.MODEL_ID] = modelId }
    }

    suspend fun saveLastSeenReleaseTag(tag: String) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.LAST_SEEN_RELEASE_TAG] = tag }
    }

    private object Keys {
        val ENCRYPTED_API_KEY = stringPreferencesKey("encrypted_api_key")
        val MODEL_ID = stringPreferencesKey("model_id")
        val LAST_SEEN_RELEASE_TAG = stringPreferencesKey("last_seen_release_tag")
    }
}
