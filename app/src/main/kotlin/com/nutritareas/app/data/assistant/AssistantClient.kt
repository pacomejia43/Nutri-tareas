package com.nutritareas.app.data.assistant

import com.nutritareas.app.data.chat.ChatImageAttachment
import com.nutritareas.app.data.chat.ChatMessage
import kotlinx.coroutines.flow.Flow

sealed interface AssistantStreamEvent {
    data class Delta(val textChunk: String) : AssistantStreamEvent
    data class Completed(val fullText: String) : AssistantStreamEvent
    data class Failed(val error: AssistantError) : AssistantStreamEvent
}

/**
 * Talks to one AI backend (Claude, Gemini...) for a single conversation turn. Every backend here
 * is stateless per request, so every call resends the full [history]; the PDF (if any) and any
 * images already sent are re-attached on whichever historical turn first carried them.
 */
interface AssistantClient {
    fun streamTurn(
        apiKey: String,
        modelId: String,
        history: List<ChatMessage>,
        pdfBase64: String?,
        pdfFileName: String?,
        newUserText: String,
        newUserImages: List<ChatImageAttachment> = emptyList(),
    ): Flow<AssistantStreamEvent>
}
