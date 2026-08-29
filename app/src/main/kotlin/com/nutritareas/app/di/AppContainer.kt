package com.nutritareas.app.di

import android.content.Context
import com.nutritareas.app.BuildConfig
import com.nutritareas.app.data.assistant.ClaudeAssistantClient
import com.nutritareas.app.data.chat.ChatHistoryStore
import com.nutritareas.app.data.pdf.PdfTextExtractor
import com.nutritareas.app.data.settings.SettingsRepository
import com.nutritareas.app.data.update.UpdateChecker
import com.nutritareas.app.data.update.UpdateInstaller

/** Simple hand-rolled DI container - the app is small enough that Hilt/Dagger would be pure overhead. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }
    val chatHistoryStore: ChatHistoryStore by lazy { ChatHistoryStore(appContext) }
    val pdfTextExtractor: PdfTextExtractor by lazy { PdfTextExtractor(appContext) }
    val assistantClient: ClaudeAssistantClient by lazy { ClaudeAssistantClient() }
    val updateChecker: UpdateChecker by lazy {
        UpdateChecker(owner = BuildConfig.UPDATE_REPO_OWNER, repo = BuildConfig.UPDATE_REPO_NAME)
    }
    val updateInstaller: UpdateInstaller by lazy { UpdateInstaller(appContext) }
}
