package com.nutritareas.app.data.chat

import kotlinx.serialization.Serializable

@Serializable
enum class ChatRole { USER, ASSISTANT }

@Serializable
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val timestampEpochMillis: Long,
    val isError: Boolean = false,
)

/** The full state of "today's" conversation: the transcript plus the PDF it's anchored to, if any. */
@Serializable
data class ChatSession(
    val messages: List<ChatMessage> = emptyList(),
    val pdfFileName: String? = null,
    val pdfPageCount: Int = 0,
    val pdfBase64: String? = null,
)
