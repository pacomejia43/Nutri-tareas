package com.nutritareas.app.ui.chat

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nutritareas.app.NutriTareasApp
import com.nutritareas.app.R
import com.nutritareas.app.data.assistant.AssistantClient
import com.nutritareas.app.data.assistant.AssistantError
import com.nutritareas.app.data.assistant.AssistantPersona
import com.nutritareas.app.data.assistant.AssistantStreamEvent
import com.nutritareas.app.data.assistant.GeminiAssistantClient
import com.nutritareas.app.data.assistant.GeneratedImage
import com.nutritareas.app.data.assistant.parseImageRequest
import com.nutritareas.app.data.assistant.parseQuickReplyRequest
import com.nutritareas.app.data.chat.ChatHistoryStore
import com.nutritareas.app.data.chat.ChatImageAttachment
import com.nutritareas.app.data.chat.ChatMessage
import com.nutritareas.app.data.chat.ChatPdfAttachment
import com.nutritareas.app.data.chat.ChatRole
import com.nutritareas.app.data.chat.ChatSession
import com.nutritareas.app.data.chat.ChatSessionsData
import com.nutritareas.app.data.docx.DocxTemplate
import com.nutritareas.app.data.docx.DocxTemplateException
import com.nutritareas.app.data.docx.DocxTemplateReader
import com.nutritareas.app.data.docx.DocxTemplateWriter
import com.nutritareas.app.data.docx.parseTemplateEdits
import com.nutritareas.app.data.image.ImageProcessor
import com.nutritareas.app.data.image.ImageReadException
import com.nutritareas.app.data.pdf.PdfContent
import com.nutritareas.app.data.pdf.PdfReadException
import com.nutritareas.app.data.pdf.PdfTextExtractor
import com.nutritareas.app.data.settings.AppSettings
import com.nutritareas.app.data.settings.AssistantProvider
import com.nutritareas.app.data.settings.SettingsRepository
import com.nutritareas.app.data.template.TemplateDocSyncClient
import com.nutritareas.app.data.template.TemplateSyncException
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A staged PDF, not yet sent - [id] is what [ChatViewModel.onCancelPendingPdf] removes by. */
private data class PendingPdf(val id: String = UUID.randomUUID().toString(), val content: PdfContent) {
    fun toSummary() = PdfSummary(id, content.fileName, content.pageCount)
    fun toChatAttachment() = ChatPdfAttachment(
        fileName = content.fileName,
        pageCount = content.pageCount,
        base64 = if (content.markdown == null) content.base64 else null,
        markdown = content.markdown,
    )
}

/** Indexed rather than by file name alone - two attached PDFs can share a name, and [PdfSummary.id]
 *  needs to stay unique for Compose's LazyRow keys. */
private fun List<ChatPdfAttachment>.toSummaries(): List<PdfSummary> =
    mapIndexed { index, pdf -> PdfSummary(id = "$index-${pdf.fileName}", fileName = pdf.fileName, pageCount = pdf.pageCount) }

class ChatViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val chatHistoryStore: ChatHistoryStore,
    private val pdfTextExtractor: PdfTextExtractor,
    private val imageProcessor: ImageProcessor,
    private val docxTemplateReader: DocxTemplateReader,
    private val templateDocSyncClient: TemplateDocSyncClient,
    private val claudeAssistantClient: AssistantClient,
    private val geminiAssistantClient: GeminiAssistantClient,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Every conversation she's keeping, and which one is on screen - [session] below is just a
    // convenience view onto the active one, so the rest of the ViewModel (written long before
    // multi-chat existed) can keep reading/writing "session" as if there were only ever one.
    private var sessions: List<ChatSession> = emptyList()
    private var activeSessionId: String = ""

    private var session: ChatSession
        get() = sessions.firstOrNull { it.id == activeSessionId } ?: ChatSession().also {
            sessions = sessions + it
            activeSessionId = it.id
        }
        set(value) {
            sessions = if (sessions.any { it.id == value.id }) {
                sessions.map { if (it.id == value.id) value else it }
            } else {
                sessions + value
            }
            activeSessionId = value.id
        }

    private var currentSettings: AppSettings = AppSettings()
    private var readyDocumentFile: File? = null
    private var readyImageFile: File? = null
    private var pendingPdfs: List<PendingPdf> = emptyList()
    private var templatePreviewJob: Job? = null

    init {
        viewModelScope.launch {
            val data = chatHistoryStore.load()
            sessions = data.sessions
            activeSessionId = data.activeSessionId?.takeIf { id -> data.sessions.any { it.id == id } }
                ?: data.sessions.firstOrNull()?.id ?: ""
            seedGreetingIfNeeded()
            // Always writes chat_sessions.json, even when nothing changed - the only way a
            // pre-multi-chat install's legacy chat_session.json actually gets migrated to it.
            persistSession()
            refreshUiFromSession()
        }
        viewModelScope.launch {
            settingsRepository.settings.collectLatest { settings ->
                currentSettings = settings
                _uiState.update { it.copy(hasApiKey = settings.hasActiveApiKey, activeProvider = settings.activeProvider) }
            }
        }
    }

    private fun seedGreetingIfNeeded(): Boolean {
        if (session.messages.isNotEmpty()) return false
        val greeting = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.ASSISTANT,
            text = AssistantPersona.GREETING,
            timestampEpochMillis = System.currentTimeMillis(),
        )
        session = session.copy(messages = listOf(greeting))
        return true
    }

    /** Refreshes every part of [ChatUiState] derived from [session]/[sessions] - used on load and
     *  whenever which conversation is active changes (new chat, switch, delete). */
    private fun refreshUiFromSession() {
        _uiState.update {
            it.copy(
                messages = session.messages,
                sessionSummaries = sessions.toSummaries(),
                activeSessionId = activeSessionId,
                pdfAttachments = session.pdfAttachments.toSummaries(),
                pendingPdfAttachments = emptyList(),
                templateFileName = session.templateFileName,
                templateParagraphCount = session.templateParagraphs.size,
                inputText = "",
                editingMessageId = null,
                editingAssistantMessageId = null,
                editingAssistantMessageText = "",
                quickReplyOptions = emptyList(),
                readyDocumentUri = null,
                readyDocumentFileName = null,
                readyImageUri = null,
                readyImageFileName = null,
                isAssistantResponding = false,
                streamingText = "",
            )
        }
    }

    /** One row per conversation for the drawer, most recently active first. [ChatSessionSummary.title]
     *  is her first message in it, so she can tell her conversations apart at a glance. */
    private fun List<ChatSession>.toSummaries(): List<ChatSessionSummary> {
        val app = getApplication<Application>()
        return sortedByDescending { it.messages.lastOrNull()?.timestampEpochMillis ?: 0L }
            .map { s ->
                val firstUserText = s.messages.firstOrNull { it.role == ChatRole.USER }?.text?.trim()
                val title = firstUserText?.takeIf { it.isNotEmpty() }
                    ?.let { if (it.length > 40) it.take(40) + "…" else it }
                    ?: app.getString(R.string.new_conversation)
                ChatSessionSummary(
                    id = s.id,
                    title = title,
                    updatedAtEpochMillis = s.messages.lastOrNull()?.timestampEpochMillis ?: 0L,
                )
            }
    }

    /**
     * Starts a brand-new conversation and switches to it - the old ones stay in [sessions], picked
     * from the drawer (see [onSelectSession]). Blocked mid-reply, same as switching or deleting one.
     * When the conversation on screen is already empty (she hasn't sent anything in it yet), there's
     * nothing to start over from, so this just tells her instead of piling up empty duplicates -
     * without this she could tap "+" repeatedly and see what looks like nothing happening, since a
     * second blank conversation looks identical to the first.
     */
    fun onNewChatClick() {
        if (_uiState.value.isAssistantResponding) return
        val app = getApplication<Application>()
        if (session.messages.none { it.role == ChatRole.USER }) {
            _uiState.update { it.copy(infoMessage = app.getString(R.string.already_new_conversation)) }
            return
        }
        pendingPdfs = emptyList()
        readyDocumentFile = null
        readyImageFile = null
        val fresh = ChatSession()
        sessions = sessions + fresh
        activeSessionId = fresh.id
        seedGreetingIfNeeded()
        persistSession()
        refreshUiFromSession()
        _uiState.update { it.copy(infoMessage = app.getString(R.string.new_conversation_started)) }
    }

    fun onOpenAppInfo() {
        _uiState.update { it.copy(isAppInfoOpen = true) }
    }

    fun onCloseAppInfo() {
        _uiState.update { it.copy(isAppInfoOpen = false) }
    }

    /** Switches which conversation is on screen - see [onNewChatClick] for why this is blocked mid-reply. */
    fun onSelectSession(sessionId: String) {
        if (_uiState.value.isAssistantResponding) return
        if (sessionId == activeSessionId) return
        if (sessions.none { it.id == sessionId }) return
        pendingPdfs = emptyList()
        readyDocumentFile = null
        readyImageFile = null
        activeSessionId = sessionId
        persistSession()
        refreshUiFromSession()
    }

    fun onDeleteSessionRequested(sessionId: String) {
        if (sessionId == activeSessionId && _uiState.value.isAssistantResponding) return
        _uiState.update { it.copy(deleteSessionConfirmId = sessionId) }
    }

    fun onDismissDeleteSessionConfirm() {
        _uiState.update { it.copy(deleteSessionConfirmId = null) }
    }

    /** Deletes the requested conversation. If it was the one on screen, whichever conversation she
     *  last touched takes its place, or a fresh one if that was her last conversation. */
    fun onConfirmDeleteSession() {
        val sessionId = _uiState.value.deleteSessionConfirmId ?: return
        _uiState.update { it.copy(deleteSessionConfirmId = null) }
        sessions = sessions.filterNot { it.id == sessionId }
        if (sessionId != activeSessionId) {
            persistSession()
            _uiState.update { it.copy(sessionSummaries = sessions.toSummaries()) }
            return
        }
        pendingPdfs = emptyList()
        readyDocumentFile = null
        readyImageFile = null
        val next = sessions.maxByOrNull { it.messages.lastOrNull()?.timestampEpochMillis ?: 0L }
        if (next != null) {
            activeSessionId = next.id
        } else {
            val fresh = ChatSession()
            sessions = listOf(fresh)
            activeSessionId = fresh.id
            seedGreetingIfNeeded()
        }
        persistSession()
        refreshUiFromSession()
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /**
     * Plain text sends immediately. Any pending PDFs (attached but not yet sent - see
     * [onAttachPdfPicked]) only go out when she taps send: they're committed to [session] here,
     * added to whichever ones are already attached, using whatever she typed as the message, or a
     * generic intro naming them if she sent it with no caption.
     */
    fun onSendClick() {
        if (_uiState.value.isAssistantResponding) return
        val text = _uiState.value.inputText.trim()
        val editingId = _uiState.value.editingMessageId
        if (editingId != null) {
            if (text.isEmpty()) return
            resendEditedMessage(editingId, text)
            return
        }
        val pending = pendingPdfs
        val messageText = when {
            text.isNotEmpty() -> text
            pending.isNotEmpty() -> {
                val app = getApplication<Application>()
                val fileNames = pending.joinToString(", ") { it.content.fileName }
                app.resources.getQuantityString(R.plurals.pdf_attach_intro, pending.size, fileNames)
            }
            else -> return
        }
        _uiState.update { it.copy(inputText = "") }
        if (pending.isNotEmpty()) {
            session = session.copy(pdfAttachments = session.pdfAttachments + pending.map { it.toChatAttachment() })
            pendingPdfs = emptyList()
            _uiState.update {
                it.copy(pdfAttachments = session.pdfAttachments.toSummaries(), pendingPdfAttachments = emptyList())
            }
        }
        sendTurn(messageText)
    }

    /** Long-pressing her last sent message loads it back into the input bar for editing (see [onSendClick]). */
    fun onEditLastMessageRequested(messageId: String) {
        if (_uiState.value.isAssistantResponding) return
        val message = session.messages.lastOrNull { it.role == ChatRole.USER } ?: return
        if (message.id != messageId) return
        _uiState.update { it.copy(inputText = message.text, editingMessageId = messageId) }
    }

    fun onCancelEditing() {
        _uiState.update { it.copy(inputText = "", editingMessageId = null) }
    }

    /**
     * Drops [messageId] and everything after it (its old assistant reply, or error bubble) and
     * resends [newText] as a fresh turn - the edited message replaces the original in place
     * instead of appearing twice.
     */
    private fun resendEditedMessage(messageId: String, newText: String) {
        val index = session.messages.indexOfFirst { it.id == messageId }
        _uiState.update { it.copy(inputText = "", editingMessageId = null) }
        if (index == -1) return
        session = session.copy(messages = session.messages.take(index))
        persistSession()
        _uiState.update { it.copy(messages = session.messages) }
        sendTurn(newText)
    }

    /**
     * "Reintentar": drops Paco's last reply and asks him again with the exact same last message
     * she sent - for when his answer was wrong rather than her question. Only available once he's
     * actually replied (the last message in the conversation is his).
     */
    fun onRetryLastResponse() {
        if (_uiState.value.isAssistantResponding) return
        if (session.messages.lastOrNull()?.role != ChatRole.ASSISTANT) return
        val lastUserMessage = session.messages.lastOrNull { it.role == ChatRole.USER } ?: return
        resendEditedMessage(lastUserMessage.id, lastUserMessage.text)
    }

    /**
     * Long-pressing Paco's last reply loads it into the edit dialog (see [onSaveAssistantMessageEdit])
     * so she can correct wrong information herself, in place - unlike [onRetryLastResponse], this
     * doesn't ask the model again, it just fixes what's already there.
     */
    fun onEditAssistantMessageRequested(messageId: String) {
        if (_uiState.value.isAssistantResponding) return
        val message = session.messages.lastOrNull { it.role == ChatRole.ASSISTANT } ?: return
        if (message.id != messageId) return
        _uiState.update { it.copy(editingAssistantMessageId = messageId, editingAssistantMessageText = message.text) }
    }

    fun onAssistantMessageEditTextChange(text: String) {
        _uiState.update { it.copy(editingAssistantMessageText = text) }
    }

    fun onCancelAssistantMessageEdit() {
        _uiState.update { it.copy(editingAssistantMessageId = null, editingAssistantMessageText = "") }
    }

    fun onSaveAssistantMessageEdit() {
        val id = _uiState.value.editingAssistantMessageId ?: return
        val newText = _uiState.value.editingAssistantMessageText.trim()
        if (newText.isEmpty()) return
        session = session.copy(messages = session.messages.map { if (it.id == id) it.copy(text = newText) else it })
        persistSession()
        _uiState.update {
            it.copy(messages = session.messages, editingAssistantMessageId = null, editingAssistantMessageText = "")
        }
    }

    /** Tapping one of Paco's suggested next steps (see [parseQuickReplyRequest]) sends it exactly
     *  like typing it herself - the options are just a shortcut, never her only way to reply. */
    fun onQuickReplyOptionSelected(option: String) {
        if (_uiState.value.isAssistantResponding) return
        sendTurn(option)
    }

    /**
     * Just stages the PDF(s) - not sent until she taps send (see [onSendClick]), so she can add
     * context first. Can be called again to add more, even with PDFs already pending or already
     * attached to the session - there's no cap on how many she can bring into one conversation.
     */
    fun onAttachPdfPicked(uris: List<Uri>) {
        if (uris.isEmpty() || _uiState.value.isLoadingPdf) return
        _uiState.update { it.copy(isLoadingPdf = true, errorMessage = null) }
        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                val newPending = uris.map { uri -> PendingPdf(content = pdfTextExtractor.extract(uri)) }
                pendingPdfs = pendingPdfs + newPending
                _uiState.update {
                    it.copy(isLoadingPdf = false, pendingPdfAttachments = pendingPdfs.map { p -> p.toSummary() })
                }
            } catch (e: PdfReadException) {
                _uiState.update { it.copy(isLoadingPdf = false, errorMessage = app.getString(R.string.error_pdf_read)) }
            }
        }
    }

    fun onCancelPendingPdf(id: String) {
        pendingPdfs = pendingPdfs.filterNot { it.id == id }
        _uiState.update { it.copy(pendingPdfAttachments = pendingPdfs.map { p -> p.toSummary() }) }
    }

    /** Screenshots/photos of tasks from her phone - read the same way the PDF is, but can arrive any time, more than once. */
    fun onAttachImagesPicked(uris: List<Uri>) {
        if (uris.isEmpty() || _uiState.value.isLoadingImages) return
        _uiState.update { it.copy(isLoadingImages = true, errorMessage = null) }
        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                val attachments = uris.map { imageProcessor.process(it) }
                _uiState.update { it.copy(isLoadingImages = false) }
                val introText = app.resources.getQuantityString(
                    R.plurals.image_attach_intro,
                    attachments.size,
                    attachments.size,
                )
                sendTurn(introText, images = attachments)
            } catch (e: ImageReadException) {
                _uiState.update { it.copy(isLoadingImages = false, errorMessage = app.getString(R.string.error_image_read)) }
            }
        }
    }

    /** Her existing Word/Google Docs template (same design every time, only a few fields change). */
    fun onAttachTemplatePicked(uri: Uri) {
        if (_uiState.value.isLoadingTemplate) return
        _uiState.update { it.copy(isLoadingTemplate = true, errorMessage = null) }
        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                val template = docxTemplateReader.read(uri)
                session = session.copy(
                    templateFileName = template.fileName,
                    templateParagraphs = template.paragraphs,
                    templateEntriesBase64 = template.entries.mapValues { (_, bytes) -> Base64.encodeToString(bytes, Base64.NO_WRAP) },
                )
                persistSession()
                _uiState.update {
                    it.copy(
                        isLoadingTemplate = false,
                        templateFileName = template.fileName,
                        templateParagraphCount = template.paragraphs.size,
                    )
                }
                val listing = template.paragraphs.mapIndexed { index, text ->
                    "[$index] " + text.ifBlank { app.getString(R.string.template_paragraph_empty) }
                }.joinToString("\n")
                sendTurn(app.getString(R.string.template_attach_intro, template.fileName, listing))
            } catch (e: DocxTemplateException) {
                _uiState.update { it.copy(isLoadingTemplate = false, errorMessage = app.getString(R.string.error_template_read)) }
            }
        }
    }

    fun onApplyTemplateClick() {
        if (!_uiState.value.canApplyTemplate) return
        sendTurn(AssistantPersona.APPLY_TEMPLATE_REQUEST, isTemplateApplication = true)
    }

    /**
     * Reads the live Google Doc and asks the assistant for the edits to apply back to it. The
     * doc's full paragraph listing only goes to the model as [sendTurn]'s hidden context - the
     * visible chat bubble stays as short as tapping "Aplicar a la plantilla" does.
     */
    fun onSyncTemplateDocClick() {
        if (_uiState.value.isSyncingTemplateDoc || _uiState.value.isAssistantResponding) return
        val app = getApplication<Application>()
        val webAppUrl = currentSettings.templateWebAppUrl
        if (webAppUrl.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = app.getString(R.string.error_no_template_web_app_url)) }
            return
        }
        if (currentSettings.activeApiKey.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = app.getString(R.string.error_no_api_key)) }
            return
        }
        _uiState.update { it.copy(isSyncingTemplateDoc = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val paragraphs = templateDocSyncClient.fetchParagraphs(webAppUrl)
                val listing = paragraphs.mapIndexed { index, text ->
                    "[$index] " + text.ifBlank { app.getString(R.string.template_paragraph_empty) }
                }.joinToString("\n")
                val hiddenContext = app.getString(R.string.google_doc_sync_intro, listing)
                sendTurn(AssistantPersona.APPLY_TEMPLATE_REQUEST, isGoogleDocSync = true, hiddenContext = hiddenContext)
            } catch (e: TemplateSyncException) {
                _uiState.update { it.copy(isSyncingTemplateDoc = false, errorMessage = app.getString(R.string.error_template_sync)) }
            }
        }
    }

    /**
     * Opens the small in-app live preview and starts polling the live Google Doc so she can watch
     * Paco's edits land without leaving the app or being handed off to the Docs editor (that's
     * what [openTemplateDocument]/"Ver documento" is for).
     */
    fun onOpenTemplatePreviewClick() {
        val app = getApplication<Application>()
        val webAppUrl = currentSettings.templateWebAppUrl
        if (webAppUrl.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = app.getString(R.string.error_no_template_web_app_url)) }
            return
        }
        _uiState.update {
            it.copy(isTemplatePreviewOpen = true, templatePreviewError = null, templatePreviewParagraphs = emptyList())
        }
        startTemplatePreviewPolling(webAppUrl)
    }

    fun onCloseTemplatePreview() {
        templatePreviewJob?.cancel()
        templatePreviewJob = null
        _uiState.update { it.copy(isTemplatePreviewOpen = false) }
    }

    fun onRefreshTemplatePreviewClick() {
        val webAppUrl = currentSettings.templateWebAppUrl ?: return
        startTemplatePreviewPolling(webAppUrl)
    }

    /** Keeps fetching [webAppUrl]'s paragraphs every [TEMPLATE_PREVIEW_POLL_INTERVAL_MS] until the preview is closed. */
    private fun startTemplatePreviewPolling(webAppUrl: String) {
        templatePreviewJob?.cancel()
        templatePreviewJob = viewModelScope.launch {
            while (isActive) {
                _uiState.update { it.copy(isTemplatePreviewLoading = true) }
                try {
                    val paragraphs = templateDocSyncClient.fetchPreview(webAppUrl)
                    _uiState.update {
                        it.copy(
                            isTemplatePreviewLoading = false,
                            templatePreviewParagraphs = paragraphs,
                            templatePreviewError = null,
                        )
                    }
                } catch (e: TemplateSyncException) {
                    val app = getApplication<Application>()
                    _uiState.update {
                        it.copy(isTemplatePreviewLoading = false, templatePreviewError = app.getString(R.string.error_template_sync))
                    }
                }
                delay(TEMPLATE_PREVIEW_POLL_INTERVAL_MS)
            }
        }
    }

    fun onErrorMessageShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onInfoMessageShown() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    /**
     * Pull-to-refresh on the chat list: clears a stuck error bubble (e.g. "no hay conexión") left
     * over from a failed turn, without touching the rest of the conversation. A no-op otherwise.
     */
    fun onRefreshChat() {
        val lastMessage = session.messages.lastOrNull() ?: return
        if (!lastMessage.isError) return
        session = session.copy(messages = session.messages.dropLast(1))
        persistSession()
        _uiState.update { it.copy(messages = session.messages) }
    }

    /** Copies the last generated document to [destinationUri] (from a CreateDocument picker result). */
    fun writeDocumentTo(destinationUri: Uri) {
        val sourceFile = readyDocumentFile ?: return
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                app.contentResolver.openOutputStream(destinationUri)?.use { out ->
                    sourceFile.inputStream().use { input -> input.copyTo(out) }
                } ?: throw IllegalStateException("No se pudo abrir el destino.")
                _uiState.update { it.copy(infoMessage = app.getString(R.string.document_ready)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = app.getString(R.string.error_generic)) }
            }
        }
    }

    /**
     * [hiddenContext] rides along on the request sent to the model but never becomes part of the
     * visible/stored [ChatMessage] - used for bulky data (like the live Google Doc's full
     * paragraph listing) the model needs once to decide its reply, but that would otherwise
     * flood the chat transcript.
     */
    private fun sendTurn(
        userText: String,
        images: List<ChatImageAttachment> = emptyList(),
        isTemplateApplication: Boolean = false,
        isGoogleDocSync: Boolean = false,
        hiddenContext: String? = null,
    ) {
        val app = getApplication<Application>()
        val apiKey = currentSettings.activeApiKey
        if (apiKey.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = app.getString(R.string.error_no_api_key)) }
            return
        }

        // Claude and Gemini both reject a request whose message list opens with an assistant turn,
        // which the locally-seeded greeting (see seedGreetingIfNeeded) always is on a fresh chat -
        // so it's dropped here rather than sent as real conversation history.
        val historyForRequest = session.messages.dropWhile { it.role == ChatRole.ASSISTANT }
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.USER,
            text = userText,
            timestampEpochMillis = System.currentTimeMillis(),
            imageAttachments = images,
        )
        session = session.copy(messages = session.messages + userMessage)
        persistSession()
        _uiState.update {
            it.copy(
                messages = session.messages,
                isAssistantResponding = true,
                streamingText = "",
                errorMessage = null,
                quickReplyOptions = emptyList(),
            )
        }

        val client = assistantClientForActiveProvider()
        val modelId = currentSettings.activeModelId
        val requestText = if (hiddenContext != null) "$userText\n\n$hiddenContext" else userText
        viewModelScope.launch {
            runAssistantTurn(
                client = client,
                apiKey = apiKey,
                modelId = modelId,
                history = historyForRequest,
                requestText = requestText,
                images = images,
                isTemplateApplication = isTemplateApplication,
                isGoogleDocSync = isGoogleDocSync,
            )
        }
    }

    /**
     * Streams one assistant turn. A transient failure - Claude/Gemini returning 5xx (e.g. Gemini's
     * "the model is currently experiencing high demand"), or a network hiccup - is retried silently
     * with backoff before it's shown as an error bubble, since these clear up on their own within a
     * few seconds almost every time (see [maxRetriesFor] for how many times, per error kind). A
     * genuine timeout - the request ran the full [ClaudeAssistantClient]/[GeminiAssistantClient]
     * deadline without finishing, e.g. a big PDF plus a long conversation taking a while to process -
     * is NOT retried: retrying would just repeat the same slow work and could leave her staring at
     * "Pensando…" for several more minutes, which is exactly what looked like the app being frozen
     * before this distinction existed. It fails fast instead, with copy that says it was slow rather
     * than the misleading "no hay conexión a internet" a timeout used to show.
     */
    private suspend fun runAssistantTurn(
        client: AssistantClient,
        apiKey: String,
        modelId: String,
        history: List<ChatMessage>,
        requestText: String,
        images: List<ChatImageAttachment>,
        isTemplateApplication: Boolean,
        isGoogleDocSync: Boolean,
        attempt: Int = 0,
    ) {
        client.streamTurn(
            apiKey = apiKey,
            modelId = modelId,
            history = history,
            pdfAttachments = session.pdfAttachments,
            newUserText = requestText,
            newUserImages = images,
        ).collect { event ->
            when (event) {
                is AssistantStreamEvent.Delta -> {
                    _uiState.update { it.copy(streamingText = it.streamingText + event.textChunk) }
                }

                is AssistantStreamEvent.Completed -> {
                    val imageRequest = parseImageRequest(event.fullText)
                    when {
                        imageRequest != null -> {
                            val app = getApplication<Application>()
                            val caption = imageRequest.visibleText.ifBlank { app.getString(R.string.image_generating_caption) }
                            appendAssistantMessage(caption, isError = false)
                            generateImage(imageRequest.prompt)
                        }
                        isTemplateApplication || isGoogleDocSync -> {
                            appendAssistantMessage(event.fullText, isError = false)
                            if (isTemplateApplication) applyTemplateEdits(event.fullText)
                            if (isGoogleDocSync) applyGoogleDocEdits(event.fullText)
                        }
                        else -> {
                            // A normal conversational reply can end with [[OPCIONES]] offering a
                            // couple of short next steps - see AssistantPersona and QuickReplyParser.
                            val quickReply = parseQuickReplyRequest(event.fullText)
                            if (quickReply != null) {
                                appendAssistantMessage(quickReply.visibleText, isError = false)
                                _uiState.update { it.copy(quickReplyOptions = quickReply.options) }
                            } else {
                                appendAssistantMessage(event.fullText, isError = false)
                            }
                        }
                    }
                }

                is AssistantStreamEvent.Failed -> {
                    if (attempt < maxRetriesFor(event.error)) {
                        _uiState.update { it.copy(streamingText = "") }
                        delay(transientRetryDelayMs(attempt))
                        runAssistantTurn(
                            client, apiKey, modelId, history, requestText, images,
                            isTemplateApplication, isGoogleDocSync, attempt + 1,
                        )
                    } else {
                        appendAssistantMessage(errorText(event.error), isError = true)
                        if (isGoogleDocSync) _uiState.update { it.copy(isSyncingTemplateDoc = false) }
                    }
                }
            }
        }
    }

    private fun assistantClientForActiveProvider(): AssistantClient = when (currentSettings.activeProvider) {
        AssistantProvider.CLAUDE -> claudeAssistantClient
        AssistantProvider.GEMINI -> geminiAssistantClient
    }

    /**
     * Image generation always runs on Gemini specifically - via [geminiAssistantClient], regardless
     * of [AppSettings.activeProvider] - since Claude has no equivalent integrated here. Needs her
     * Gemini key configured even if she's chatting with Claude.
     */
    private fun generateImage(prompt: String) {
        val app = getApplication<Application>()
        val geminiKey = currentSettings.geminiApiKey
        if (geminiKey.isNullOrBlank()) {
            appendAssistantMessage(app.getString(R.string.error_no_gemini_key_for_image), isError = true)
            return
        }
        _uiState.update { it.copy(isGeneratingImage = true) }
        viewModelScope.launch {
            try {
                val image = geminiAssistantClient.generateImage(geminiKey, GeminiAssistantClient.IMAGE_MODEL_ID, prompt)
                appendGeneratedImage(image)
            } catch (e: AssistantError) {
                appendAssistantMessage(errorText(e), isError = true)
            }
            _uiState.update { it.copy(isGeneratingImage = false) }
        }
    }

    private fun appendGeneratedImage(image: GeneratedImage) {
        val app = getApplication<Application>()
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.ASSISTANT,
            text = "",
            timestampEpochMillis = System.currentTimeMillis(),
            imageAttachments = listOf(ChatImageAttachment(base64 = image.base64, mimeType = image.mimeType)),
        )
        session = session.copy(messages = session.messages + message)
        persistSession()
        _uiState.update { it.copy(messages = session.messages) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = Base64.decode(image.base64, Base64.NO_WRAP)
                val extension = if (image.mimeType == "image/jpeg") "jpg" else "png"
                val dir = File(app.cacheDir, "imagenes").apply { mkdirs() }
                val fileName = "imagen_${System.currentTimeMillis()}.$extension"
                val file = File(dir, fileName)
                file.writeBytes(bytes)
                readyImageFile = file
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
                _uiState.update { it.copy(readyImageUri = uri, readyImageFileName = fileName) }
            } catch (e: Exception) {
                // The image is already visible in chat either way - just no Guardar/Compartir row for it.
            }
        }
    }

    /** Copies the last generated image to [destinationUri] (from a CreateDocument picker result). */
    fun writeImageTo(destinationUri: Uri) {
        val sourceFile = readyImageFile ?: return
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                app.contentResolver.openOutputStream(destinationUri)?.use { out ->
                    sourceFile.inputStream().use { input -> input.copyTo(out) }
                } ?: throw IllegalStateException("No se pudo abrir el destino.")
                _uiState.update { it.copy(infoMessage = app.getString(R.string.image_ready)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = app.getString(R.string.error_generic)) }
            }
        }
    }

    private fun appendAssistantMessage(text: String, isError: Boolean) {
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.ASSISTANT,
            text = text,
            timestampEpochMillis = System.currentTimeMillis(),
            isError = isError,
        )
        session = session.copy(messages = session.messages + message)
        persistSession()
        _uiState.update { it.copy(messages = session.messages, isAssistantResponding = false, streamingText = "") }
    }

    private fun applyTemplateEdits(assistantText: String) {
        val app = getApplication<Application>()
        // Tables aren't supported for a locally-uploaded Word template (see parseTemplateEdits) -
        // only the paragraph text edits apply here.
        val edits = parseTemplateEdits(assistantText).paragraphs
        if (edits.isEmpty()) {
            _uiState.update { it.copy(errorMessage = app.getString(R.string.error_template_apply)) }
            return
        }
        _uiState.update { it.copy(isBuildingDocument = true) }
        val template = DocxTemplate(
            fileName = session.templateFileName ?: "plantilla.docx",
            entries = session.templateEntriesBase64.mapValues { (_, base64) -> Base64.decode(base64, Base64.NO_WRAP) },
            paragraphs = session.templateParagraphs,
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = File(app.cacheDir, "documentos").apply { mkdirs() }
                val fileName = "plantilla_${System.currentTimeMillis()}.docx"
                val file = File(dir, fileName)
                DocxTemplateWriter.apply(template, edits, file)
                readyDocumentFile = file
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
                _uiState.update {
                    it.copy(isBuildingDocument = false, readyDocumentUri = uri, readyDocumentFileName = fileName)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isBuildingDocument = false, errorMessage = app.getString(R.string.error_generic)) }
            }
        }
    }

    private fun applyGoogleDocEdits(assistantText: String) {
        val app = getApplication<Application>()
        val webAppUrl = currentSettings.templateWebAppUrl
        val templateEdits = parseTemplateEdits(assistantText)
        if (webAppUrl.isNullOrBlank()) {
            _uiState.update { it.copy(isSyncingTemplateDoc = false, errorMessage = app.getString(R.string.error_no_template_web_app_url)) }
            return
        }
        if (templateEdits.paragraphs.isEmpty() && templateEdits.tables.isEmpty()) {
            _uiState.update { it.copy(isSyncingTemplateDoc = false, errorMessage = app.getString(R.string.error_template_apply)) }
            return
        }
        viewModelScope.launch {
            try {
                templateDocSyncClient.applyEdits(webAppUrl, templateEdits.paragraphs, templateEdits.tables)
                _uiState.update {
                    it.copy(isSyncingTemplateDoc = false, infoMessage = app.getString(R.string.google_doc_synced))
                }
            } catch (e: TemplateSyncException) {
                _uiState.update { it.copy(isSyncingTemplateDoc = false, errorMessage = app.getString(R.string.error_template_sync)) }
            }
        }
    }

    private fun errorText(error: AssistantError): String {
        val app = getApplication<Application>()
        return when (error) {
            is AssistantError.MissingApiKey -> app.getString(R.string.error_no_api_key)
            is AssistantError.InvalidApiKey -> app.getString(R.string.error_invalid_api_key)
            is AssistantError.RateLimited -> app.getString(R.string.error_rate_limited)
            is AssistantError.Network -> app.getString(R.string.error_network)
            is AssistantError.Timeout -> app.getString(R.string.error_timeout)
            is AssistantError.ModelNotFound,
            is AssistantError.ServerError,
            is AssistantError.Unknown,
            -> {
                // These three otherwise collapse into one opaque "try again", which is exactly
                // what hid a real cause (a Gemini model tier/quota rejection) behind a useless
                // generic message. Appending the underlying cause makes that diagnosable in-chat.
                val detail = error.cause?.message?.takeIf { it.isNotBlank() }
                if (detail != null) app.getString(R.string.error_generic_detailed, detail) else app.getString(R.string.error_generic)
            }
        }
    }

    private fun persistSession() {
        val snapshot = ChatSessionsData(sessions = sessions, activeSessionId = activeSessionId)
        viewModelScope.launch { chatHistoryStore.save(snapshot) }
    }

    /**
     * How many times [runAssistantTurn] retries a given failure before giving up. ServerError (5xx)
     * fails fast - the server responds with an error status almost immediately - so several retries
     * cost little. Network (DNS/connect failures) also fails fast, so a couple of quick retries can
     * catch a brief Wi-Fi blip. Timeout is the opposite: by definition it already waited the full
     * client-side deadline before failing, so retrying would silently repeat that same long wait -
     * not worth it, it fails straight to an error bubble instead (see [runAssistantTurn]'s doc).
     */
    private fun maxRetriesFor(error: AssistantError): Int = when (error) {
        is AssistantError.ServerError -> MAX_RETRIES_SERVER_ERROR
        is AssistantError.Network -> MAX_RETRIES_NETWORK
        else -> 0
    }

    /** Exponential backoff for [runAssistantTurn]'s retries, capped so a long spike (Gemini's "high
     *  demand" 503 can run well past the couple of seconds the old fixed short delay allowed for)
     *  doesn't leave her waiting forever either. */
    private fun transientRetryDelayMs(attempt: Int): Long =
        (TRANSIENT_RETRY_BASE_DELAY_MS * (1L shl attempt)).coerceAtMost(TRANSIENT_RETRY_MAX_DELAY_MS)

    companion object {
        private const val TEMPLATE_PREVIEW_POLL_INTERVAL_MS = 4000L
        private const val MAX_RETRIES_SERVER_ERROR = 4
        private const val MAX_RETRIES_NETWORK = 2
        private const val TRANSIENT_RETRY_BASE_DELAY_MS = 1500L
        private const val TRANSIENT_RETRY_MAX_DELAY_MS = 10_000L

        fun factory(application: Application): ViewModelProvider.Factory {
            val container = (application as NutriTareasApp).container
            return viewModelFactory {
                initializer {
                    ChatViewModel(
                        application = application,
                        settingsRepository = container.settingsRepository,
                        chatHistoryStore = container.chatHistoryStore,
                        pdfTextExtractor = container.pdfTextExtractor,
                        imageProcessor = container.imageProcessor,
                        docxTemplateReader = container.docxTemplateReader,
                        templateDocSyncClient = container.templateDocSyncClient,
                        claudeAssistantClient = container.claudeAssistantClient,
                        geminiAssistantClient = container.geminiAssistantClient,
                    )
                }
            }
        }
    }
}
