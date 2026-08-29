package com.nutritareas.app.ui.chat

import android.net.Uri
import com.nutritareas.app.data.chat.ChatMessage
import com.nutritareas.app.data.settings.AssistantProvider

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val pdfFileName: String? = null,
    val pdfPageCount: Int = 0,
    val inputText: String = "",
    val isAssistantResponding: Boolean = false,
    val streamingText: String = "",
    val isLoadingPdf: Boolean = false,
    val isLoadingImages: Boolean = false,
    val templateFileName: String? = null,
    val templateParagraphCount: Int = 0,
    val isLoadingTemplate: Boolean = false,
    val isBuildingDocument: Boolean = false,
    val readyDocumentUri: Uri? = null,
    val readyDocumentFileName: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val showNewConversationConfirm: Boolean = false,
    val hasApiKey: Boolean = true,
    val activeProvider: AssistantProvider = AssistantProvider.CLAUDE,
) {
    val hasPdf: Boolean get() = pdfFileName != null
    val hasTemplate: Boolean get() = templateFileName != null
    val canSend: Boolean get() = !isAssistantResponding && inputText.isNotBlank()
    val canAttachPdf: Boolean get() = !hasPdf && !isAssistantResponding && !isLoadingPdf
    val canAttachImages: Boolean get() = !isAssistantResponding && !isLoadingImages
    val canAttachTemplate: Boolean get() = !hasTemplate && !isAssistantResponding && !isLoadingTemplate
    val canApplyTemplate: Boolean get() = hasTemplate && !isAssistantResponding && !isBuildingDocument && messages.isNotEmpty()
}
