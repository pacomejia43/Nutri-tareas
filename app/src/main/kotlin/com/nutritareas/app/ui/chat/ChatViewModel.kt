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
import com.nutritareas.app.data.chat.ChatHistoryStore
import com.nutritareas.app.data.chat.ChatImageAttachment
import com.nutritareas.app.data.chat.ChatMessage
import com.nutritareas.app.data.chat.ChatRole
import com.nutritareas.app.data.chat.ChatSession
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

    private var session: ChatSession = ChatSession()
    private var currentSettings: AppSettings = AppSettings()
    private var readyDocumentFile: File? = null
    private var readyImageFile: File? = null
    private var pendingPdfContent: PdfContent? = null
    private var templatePreviewJob: Job? = null

    init {
        viewModelScope.launch {
            session = chatHistoryStore.load()
            val seededGreeting = seedGreetingIfNeeded()
            if (seededGreeting) persistSession()
            _uiState.update {
                it.copy(
                    messages = session.messages,
                    pdfFileName = session.pdfFileName,
                    pdfPageCount = session.pdfPageCount,
                    templateFileName = session.templateFileName,
                    templateParagraphCount = session.templateParagraphs.size,
                )
            }
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

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /**
     * Plain text sends immediately. A pending PDF (attached but not yet sent - see
     * [onAttachPdfPicked]) only goes out when she taps send: it's committed to [session] here,
     * using whatever she typed as the message, or a generic intro if she sent it with no caption.
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
        val pendingPdf = pendingPdfContent
        val messageText = when {
            text.isNotEmpty() -> text
            pendingPdf != null -> getApplication<Application>().getString(R.string.pdf_attach_intro, pendingPdf.fileName)
            else -> return
        }
        _uiState.update { it.copy(inputText = "") }
        if (pendingPdf != null) {
            session = session.copy(
                pdfFileName = pendingPdf.fileName,
                pdfPageCount = pendingPdf.pageCount,
                pdfBase64 = if (pendingPdf.markdown == null) pendingPdf.base64 else null,
                pdfMarkdown = pendingPdf.markdown,
            )
            pendingPdfContent = null
            _uiState.update {
                it.copy(
                    pdfFileName = pendingPdf.fileName,
                    pdfPageCount = pendingPdf.pageCount,
                    pendingPdfFileName = null,
                    pendingPdfPageCount = 0,
                )
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

    /** Just stages the PDF - it's not sent until she taps send (see [onSendClick]), so she can add context first. */
    fun onAttachPdfPicked(uri: Uri) {
        if (_uiState.value.isLoadingPdf) return
        _uiState.update { it.copy(isLoadingPdf = true, errorMessage = null) }
        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                val content = pdfTextExtractor.extract(uri)
                pendingPdfContent = content
                _uiState.update {
                    it.copy(isLoadingPdf = false, pendingPdfFileName = content.fileName, pendingPdfPageCount = content.pageCount)
                }
            } catch (e: PdfReadException) {
                _uiState.update { it.copy(isLoadingPdf = false, errorMessage = app.getString(R.string.error_pdf_read)) }
            }
        }
    }

    fun onCancelPendingPdf() {
        pendingPdfContent = null
        _uiState.update { it.copy(pendingPdfFileName = null, pendingPdfPageCount = 0) }
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

    fun onNewConversationClick() {
        _uiState.update { it.copy(showNewConversationConfirm = true) }
    }

    fun onDismissNewConversationConfirm() {
        _uiState.update { it.copy(showNewConversationConfirm = false) }
    }

    fun onConfirmNewConversation() {
        readyDocumentFile = null
        readyImageFile = null
        pendingPdfContent = null
        templatePreviewJob?.cancel()
        templatePreviewJob = null
        viewModelScope.launch {
            chatHistoryStore.clear()
            session = ChatSession()
            seedGreetingIfNeeded()
            persistSession()
            _uiState.value = ChatUiState(
                messages = session.messages,
                hasApiKey = currentSettings.hasActiveApiKey,
                activeProvider = currentSettings.activeProvider,
            )
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
            it.copy(messages = session.messages, isAssistantResponding = true, streamingText = "", errorMessage = null)
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
     * Streams one assistant turn. A transient server-side failure (Claude/Gemini returning 5xx -
     * e.g. Gemini's "the model is currently experiencing high demand") is retried silently up to
     * [MAX_TRANSIENT_RETRIES] times with backoff before it's shown as an error bubble, since these
     * clear up on their own within a few seconds almost every time.
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
            pdfBase64 = session.pdfBase64,
            pdfFileName = session.pdfFileName,
            pdfMarkdown = session.pdfMarkdown,
            newUserText = requestText,
            newUserImages = images,
        ).collect { event ->
            when (event) {
                is AssistantStreamEvent.Delta -> {
                    _uiState.update { it.copy(streamingText = it.streamingText + event.textChunk) }
                }

                is AssistantStreamEvent.Completed -> {
                    val imageRequest = parseImageRequest(event.fullText)
                    if (imageRequest != null) {
                        val app = getApplication<Application>()
                        val caption = imageRequest.visibleText.ifBlank { app.getString(R.string.image_generating_caption) }
                        appendAssistantMessage(caption, isError = false)
                        generateImage(imageRequest.prompt)
                    } else {
                        appendAssistantMessage(event.fullText, isError = false)
                        if (isTemplateApplication) applyTemplateEdits(event.fullText)
                        if (isGoogleDocSync) applyGoogleDocEdits(event.fullText)
                    }
                }

                is AssistantStreamEvent.Failed -> {
                    if (event.error is AssistantError.ServerError && attempt < MAX_TRANSIENT_RETRIES) {
                        _uiState.update { it.copy(streamingText = "") }
                        delay(TRANSIENT_RETRY_DELAY_MS * (attempt + 1))
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
        val snapshot = session
        viewModelScope.launch { chatHistoryStore.save(snapshot) }
    }

    companion object {
        private const val TEMPLATE_PREVIEW_POLL_INTERVAL_MS = 4000L
        private const val MAX_TRANSIENT_RETRIES = 2
        private const val TRANSIENT_RETRY_DELAY_MS = 1200L

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
