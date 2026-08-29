package com.nutritareas.app.data.chat

import kotlinx.serialization.Serializable

@Serializable
enum class ChatRole { USER, ASSISTANT }

/** A photo/screenshot attached to a user message, kept as base64 so the flat JSON session file carries it whole. */
@Serializable
data class ChatImageAttachment(
    val base64: String,
    val mimeType: String,
)

@Serializable
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val timestampEpochMillis: Long,
    val isError: Boolean = false,
    val imageAttachments: List<ChatImageAttachment> = emptyList(),
)

/** The full state of "today's" conversation: the transcript plus the PDF/template it's anchored to, if any. */
@Serializable
data class ChatSession(
    val messages: List<ChatMessage> = emptyList(),
    val pdfFileName: String? = null,
    val pdfPageCount: Int = 0,
    val pdfBase64: String? = null,
    val templateFileName: String? = null,
    val templateParagraphs: List<String> = emptyList(),
    val templateEntriesBase64: Map<String, String> = emptyMap(),
)
