package com.nutritareas.app.ui.chat

import android.net.Uri
import com.nutritareas.app.data.chat.ChatMessage
import com.nutritareas.app.data.chat.ChatRole
import com.nutritareas.app.data.settings.AssistantProvider
import com.nutritareas.app.data.template.TemplateParagraph

/** Lightweight display info for a PDF, attached or still pending - the actual base64/markdown
 *  payload stays out of UI state, which only needs to show what's there and let her remove it. */
data class PdfSummary(val id: String, val fileName: String, val pageCount: Int)

/** One row in the conversations drawer - the full message list stays out of UI state until she
 *  actually opens that conversation (see [com.nutritareas.app.ui.chat.ChatViewModel.onSelectSession]). */
data class ChatSessionSummary(val id: String, val title: String, val updatedAtEpochMillis: Long)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val sessionSummaries: List<ChatSessionSummary> = emptyList(),
    val activeSessionId: String = "",
    val pdfAttachments: List<PdfSummary> = emptyList(),
    val pendingPdfAttachments: List<PdfSummary> = emptyList(),
    val inputText: String = "",
    val isAssistantResponding: Boolean = false,
    val streamingText: String = "",
    val isLoadingPdf: Boolean = false,
    val isLoadingImages: Boolean = false,
    val templateFileName: String? = null,
    val templateParagraphCount: Int = 0,
    val isLoadingTemplate: Boolean = false,
    val isBuildingDocument: Boolean = false,
    val isSyncingTemplateDoc: Boolean = false,
    val isTemplatePreviewOpen: Boolean = false,
    val templatePreviewParagraphs: List<TemplateParagraph> = emptyList(),
    val isTemplatePreviewLoading: Boolean = false,
    val templatePreviewError: String? = null,
    val readyDocumentUri: Uri? = null,
    val readyDocumentFileName: String? = null,
    val isGeneratingImage: Boolean = false,
    val readyImageUri: Uri? = null,
    val readyImageFileName: String? = null,
    val editingMessageId: String? = null,
    val editingAssistantMessageId: String? = null,
    val editingAssistantMessageText: String = "",
    val quickReplyOptions: List<String> = emptyList(),
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val deleteSessionConfirmId: String? = null,
    val isAppInfoOpen: Boolean = false,
    val hasApiKey: Boolean = true,
    val activeProvider: AssistantProvider = AssistantProvider.CLAUDE,
) {
    val hasPdf: Boolean get() = pdfAttachments.isNotEmpty()
    val hasPendingPdf: Boolean get() = pendingPdfAttachments.isNotEmpty()
    val hasTemplate: Boolean get() = templateFileName != null
    val isEditingMessage: Boolean get() = editingMessageId != null
    val isEditingAssistantMessage: Boolean get() = editingAssistantMessageId != null
    val canRetryLastResponse: Boolean get() = !isAssistantResponding && messages.lastOrNull()?.role == ChatRole.ASSISTANT
    val canSend: Boolean get() = !isAssistantResponding && (inputText.isNotBlank() || hasPendingPdf)
    val canAttachPdf: Boolean get() = !isAssistantResponding && !isLoadingPdf
    val canAttachImages: Boolean get() = !isAssistantResponding && !isLoadingImages
    val canAttachTemplate: Boolean get() = !hasTemplate && !isAssistantResponding && !isLoadingTemplate
    val canApplyTemplate: Boolean get() = hasTemplate && !isAssistantResponding && !isBuildingDocument && messages.isNotEmpty()
}
