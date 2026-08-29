package com.nutritareas.app.ui.chat

import android.net.Uri
import com.nutritareas.app.data.chat.ChatMessage

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val pdfFileName: String? = null,
    val pdfPageCount: Int = 0,
    val inputText: String = "",
    val isAssistantResponding: Boolean = false,
    val streamingText: String = "",
    val isLoadingPdf: Boolean = false,
    val isBuildingDocument: Boolean = false,
    val readyDocumentUri: Uri? = null,
    val readyDocumentFileName: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val showNewConversationConfirm: Boolean = false,
    val hasApiKey: Boolean = true,
) {
    val hasPdf: Boolean get() = pdfFileName != null
    val canSend: Boolean get() = !isAssistantResponding && inputText.isNotBlank()
    val canGenerateDocument: Boolean get() = !isAssistantResponding && !isBuildingDocument && messages.isNotEmpty()
    val canAttachPdf: Boolean get() = !hasPdf && !isAssistantResponding && !isLoadingPdf
}
