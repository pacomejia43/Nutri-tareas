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
 * Persists the current conversation as a single JSON file in app-private storage. The app only
 * ever keeps one active conversation, so a flat file is simpler and lighter than a database.
 */
class ChatHistoryStore(private val context: Context) {

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val file: File get() = File(context.filesDir, "chat_session.json")

    suspend fun load(): ChatSession = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!file.exists()) return@withLock ChatSession()
            runCatching { json.decodeFromString<ChatSession>(file.readText()) }.getOrDefault(ChatSession())
        }
    }

    suspend fun save(session: ChatSession) = withContext(Dispatchers.IO) {
        mutex.withLock {
            file.writeText(json.encodeToString(session))
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (file.exists()) file.delete()
        }
    }
}
