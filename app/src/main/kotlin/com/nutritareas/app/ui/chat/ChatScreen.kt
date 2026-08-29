package com.nutritareas.app.ui.chat

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutritareas.app.R
import com.nutritareas.app.data.chat.ChatMessage
import com.nutritareas.app.data.chat.ChatRole
import com.nutritareas.app.ui.chat.components.ApiKeyMissingBanner
import com.nutritareas.app.ui.chat.components.ChatInputBar
import com.nutritareas.app.ui.chat.components.DocumentReadyRow
import com.nutritareas.app.ui.chat.components.GenerateDocumentRow
import com.nutritareas.app.ui.chat.components.MessageBubble
import com.nutritareas.app.ui.chat.components.PdfChip
import com.nutritareas.app.ui.chat.components.TypingIndicatorBubble

private const val DOCX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.factory(context.applicationContext as Application),
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val pdfPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onAttachPdfPicked)
    }
    val saveDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DOCX_MIME_TYPE),
    ) { uri ->
        uri?.let(viewModel::writeDocumentTo)
    }

    val itemCount = uiState.messages.size + if (uiState.isAssistantResponding) 1 else 0
    LaunchedEffect(itemCount, uiState.streamingText) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorMessageShown()
        }
    }
    LaunchedEffect(uiState.infoMessage) {
        uiState.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onInfoMessageShown()
        }
    }

    if (uiState.showNewConversationConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissNewConversationConfirm,
            title = { Text(stringResource(R.string.new_conversation_confirm_title)) },
            text = { Text(stringResource(R.string.new_conversation_confirm_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmNewConversation) {
                    Text(stringResource(R.string.new_conversation))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissNewConversationConfirm) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_title)) },
                actions = {
                    IconButton(onClick = viewModel::onNewConversationClick) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.new_conversation))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                uiState.readyDocumentUri?.let { uri ->
                    DocumentReadyRow(
                        onSave = { saveDocumentLauncher.launch(uiState.readyDocumentFileName ?: "tareas.docx") },
                        onShare = { shareDocument(context, uri) },
                    )
                }
                if (uiState.canGenerateDocument) {
                    GenerateDocumentRow(isBuilding = uiState.isBuildingDocument, onClick = viewModel::onGenerateDocumentClick)
                }
                ChatInputBar(
                    text = uiState.inputText,
                    onTextChange = viewModel::onInputChange,
                    onSend = viewModel::onSendClick,
                    onAttachPdf = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                    canSend = uiState.canSend,
                    canAttach = uiState.canAttachPdf,
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (!uiState.hasApiKey) {
                ApiKeyMissingBanner(onOpenSettings = onOpenSettings)
            }
            if (uiState.isLoadingPdf) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (uiState.hasPdf) {
                PdfChip(fileName = uiState.pdfFileName.orEmpty(), pageCount = uiState.pdfPageCount)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.messages, key = { it.id }) { message -> MessageBubble(message) }
                if (uiState.isAssistantResponding) {
                    item(key = "streaming") {
                        if (uiState.streamingText.isEmpty()) {
                            TypingIndicatorBubble()
                        } else {
                            MessageBubble(
                                ChatMessage(
                                    id = "streaming",
                                    role = ChatRole.ASSISTANT,
                                    text = uiState.streamingText,
                                    timestampEpochMillis = 0L,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun shareDocument(context: Context, uri: Uri) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = DOCX_MIME_TYPE
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share_document)))
}
