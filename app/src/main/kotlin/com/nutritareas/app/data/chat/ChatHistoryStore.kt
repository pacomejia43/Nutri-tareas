package com.nutritareas.app.data.chat

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists every conversation she's keeping ("tareas" she may work on at different times - see
 * [ChatSessionsData]) as a single JSON file in app-private storage. Not a database: even with
 * several conversations and their PDFs, this stays small enough for a flat file to be simpler.
 */
class ChatHistoryStore(private val context: Context) {

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val file: File get() = File(context.filesDir, "chat_sessions.json")

    // Pre-multi-chat installs kept exactly one conversation in this file - read once, on the first
    // launch after updating, to fold it in as her first session instead of losing it.
    private val legacyFile: File get() = File(context.filesDir, "chat_session.json")

    suspend fun load(): ChatSessionsData = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (file.exists()) {
                return@withLock runCatching { json.decodeFromString<ChatSessionsData>(file.readText()) }
                    .getOrDefault(ChatSessionsData())
            }
            val legacy = if (legacyFile.exists()) {
                runCatching { json.decodeFromString<ChatSession>(legacyFile.readText()) }.getOrNull()
            } else {
                null
            }
            if (legacy != null && legacy.messages.isNotEmpty()) {
                ChatSessionsData(sessions = listOf(legacy), activeSessionId = legacy.id)
            } else {
                ChatSessionsData()
            }
        }
    }

    suspend fun save(data: ChatSessionsData) = withContext(Dispatchers.IO) {
        mutex.withLock {
            file.writeText(json.encodeToString(data))
        }
    }
}
