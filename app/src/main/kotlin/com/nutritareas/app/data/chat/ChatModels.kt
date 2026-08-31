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

/** A PDF attached to the conversation (there can be several - e.g. one per subject). [markdown] is
 *  its extracted text when it has one, mutually exclusive with [base64] (a native document, sent
 *  for a scan/photo PDF with no text layer) - same split [com.nutritareas.app.data.pdf.PdfContent] uses. */
@Serializable
data class ChatPdfAttachment(
    val fileName: String,
    val pageCount: Int,
    val base64: String? = null,
    val markdown: String? = null,
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

/** The full state of "today's" conversation: the transcript plus the PDFs/template it's anchored to, if any. */
@Serializable
data class ChatSession(
    val messages: List<ChatMessage> = emptyList(),
    val pdfAttachments: List<ChatPdfAttachment> = emptyList(),
    val templateFileName: String? = null,
    val templateParagraphs: List<String> = emptyList(),
    val templateEntriesBase64: Map<String, String> = emptyMap(),
)
