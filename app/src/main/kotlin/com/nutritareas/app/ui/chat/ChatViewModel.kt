package com.nutritareas.app.ui.chat

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nutritareas.app.NutriTareasApp
import com.nutritareas.app.R
import com.nutritareas.app.data.assistant.AssistantError
import com.nutritareas.app.data.assistant.AssistantPersona
import com.nutritareas.app.data.assistant.AssistantStreamEvent
import com.nutritareas.app.data.assistant.ClaudeAssistantClient
import com.nutritareas.app.data.chat.ChatHistoryStore
import com.nutritareas.app.data.chat.ChatMessage
import com.nutritareas.app.data.chat.ChatRole
import com.nutritareas.app.data.chat.ChatSession
import com.nutritareas.app.data.docx.DocxGenerator
import com.nutritareas.app.data.pdf.PdfReadException
import com.nutritareas.app.data.pdf.PdfTextExtractor
import com.nutritareas.app.data.settings.AppSettings
import com.nutritareas.app.data.settings.SettingsRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val chatHistoryStore: ChatHistoryStore,
    private val pdfTextExtractor: PdfTextExtractor,
    private val assistantClient: ClaudeAssistantClient,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var session: ChatSession = ChatSession()
    private var currentSettings: AppSettings = AppSettings()
    private var readyDocumentFile: File? = null

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
                )
            }
        }
        viewModelScope.launch {
            settingsRepository.settings.collectLatest { settings ->
                currentSettings = settings
                _uiState.update { it.copy(hasApiKey = settings.hasApiKey) }
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

    fun onSendClick() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isAssistantResponding) return
        _uiState.update { it.copy(inputText = "") }
        sendTurn(text, isDocumentGeneration = false)
    }

    fun onGenerateDocumentClick() {
        if (!_uiState.value.canGenerateDocument) return
        sendTurn(AssistantPersona.GENERATE_DOCUMENT_REQUEST, isDocumentGeneration = true)
    }

    fun onAttachPdfPicked(uri: Uri) {
        if (_uiState.value.isLoadingPdf) return
        _uiState.update { it.copy(isLoadingPdf = true, errorMessage = null) }
        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                val content = pdfTextExtractor.extract(uri)
                session = session.copy(
                    pdfFileName = content.fileName,
                    pdfPageCount = content.pageCount,
                    pdfBase64 = content.base64,
                )
                persistSession()
                _uiState.update {
                    it.copy(isLoadingPdf = false, pdfFileName = content.fileName, pdfPageCount = content.pageCount)
                }
                sendTurn(app.getString(R.string.pdf_attach_intro, content.fileName), isDocumentGeneration = false)
            } catch (e: PdfReadException) {
                _uiState.update { it.copy(isLoadingPdf = false, errorMessage = app.getString(R.string.error_pdf_read)) }
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
        viewModelScope.launch {
            chatHistoryStore.clear()
            session = ChatSession()
            seedGreetingIfNeeded()
            persistSession()
            _uiState.value = ChatUiState(messages = session.messages, hasApiKey = currentSettings.hasApiKey)
        }
    }

    fun onErrorMessageShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onInfoMessageShown() {
        _uiState.update { it.copy(infoMessage = null) }
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

    private fun sendTurn(userText: String, isDocumentGeneration: Boolean) {
        val app = getApplication<Application>()
        val apiKey = currentSettings.apiKey
        if (apiKey.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = app.getString(R.string.error_no_api_key)) }
            return
        }

        val historyForRequest = session.messages
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.USER,
            text = userText,
            timestampEpochMillis = System.currentTimeMillis(),
        )
        session = session.copy(messages = session.messages + userMessage)
        persistSession()
        _uiState.update {
            it.copy(messages = session.messages, isAssistantResponding = true, streamingText = "", errorMessage = null)
        }

        viewModelScope.launch {
            assistantClient.streamTurn(
                apiKey = apiKey,
                modelId = currentSettings.modelId,
                history = historyForRequest,
                pdfBase64 = session.pdfBase64,
                pdfFileName = session.pdfFileName,
                newUserText = userText,
            ).collect { event ->
                when (event) {
                    is AssistantStreamEvent.Delta -> {
                        _uiState.update { it.copy(streamingText = it.streamingText + event.textChunk) }
                    }

                    is AssistantStreamEvent.Completed -> {
                        appendAssistantMessage(event.fullText, isError = false)
                        if (isDocumentGeneration) buildDocument(event.fullText)
                    }

                    is AssistantStreamEvent.Failed -> {
                        appendAssistantMessage(errorText(event.error), isError = true)
                    }
                }
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

    private fun buildDocument(assistantText: String) {
        _uiState.update { it.copy(isBuildingDocument = true) }
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = File(app.cacheDir, "documentos").apply { mkdirs() }
                val fileName = "tareas_${System.currentTimeMillis()}.docx"
                val file = File(dir, fileName)
                DocxGenerator.generate(assistantText, file)
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
            -> app.getString(R.string.error_generic)
        }
    }

    private fun persistSession() {
        val snapshot = session
        viewModelScope.launch { chatHistoryStore.save(snapshot) }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory {
            val container = (application as NutriTareasApp).container
            return viewModelFactory {
                initializer {
                    ChatViewModel(
                        application = application,
                        settingsRepository = container.settingsRepository,
                        chatHistoryStore = container.chatHistoryStore,
                        pdfTextExtractor = container.pdfTextExtractor,
                        assistantClient = container.assistantClient,
                    )
                }
            }
        }
    }
}
