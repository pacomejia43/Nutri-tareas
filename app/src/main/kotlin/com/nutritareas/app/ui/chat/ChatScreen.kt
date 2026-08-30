package com.nutritareas.app.ui.chat

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutritareas.app.R
import com.nutritareas.app.data.chat.ChatMessage
import com.nutritareas.app.data.chat.ChatRole
import com.nutritareas.app.data.settings.AssistantProvider
import com.nutritareas.app.data.template.TemplateDocument
import com.nutritareas.app.ui.chat.components.ApiKeyMissingBanner
import com.nutritareas.app.ui.chat.components.ApplyTemplateRow
import com.nutritareas.app.ui.chat.components.ChatInputBar
import com.nutritareas.app.ui.chat.components.DocumentReadyRow
import com.nutritareas.app.ui.chat.components.EditingMessageRow
import com.nutritareas.app.ui.chat.components.ImageReadyRow
import com.nutritareas.app.ui.chat.components.MessageBubble
import com.nutritareas.app.ui.chat.components.NutritionBackdrop
import com.nutritareas.app.ui.chat.components.PdfChip
import com.nutritareas.app.ui.chat.components.PendingPdfRow
import com.nutritareas.app.ui.chat.components.TemplateChip
import com.nutritareas.app.ui.chat.components.TemplateDocPreviewDialog
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
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.onAttachImagesPicked(uris)
    }
    val templatePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onAttachTemplatePicked)
    }
    val saveDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DOCX_MIME_TYPE),
    ) { uri ->
        uri?.let(viewModel::writeDocumentTo)
    }
    val saveImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        uri?.let(viewModel::writeImageTo)
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

    if (uiState.isTemplatePreviewOpen) {
        TemplateDocPreviewDialog(
            paragraphs = uiState.templatePreviewParagraphs,
            isLoading = uiState.isTemplatePreviewLoading,
            errorMessage = uiState.templatePreviewError,
            onDismiss = viewModel::onCloseTemplatePreview,
            onRefresh = viewModel::onRefreshTemplatePreviewClick,
        )
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
                title = {
                    Column {
                        Text(stringResource(R.string.chat_title))
                        Text(
                            text = providerLabel(uiState.activeProvider),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::onNewConversationClick) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.new_conversation))
                    }
                    TemplateDocumentButton(
                        isSyncing = uiState.isSyncingTemplateDoc,
                        onOpenDocument = { openTemplateDocument(context) },
                        onOpenPreview = viewModel::onOpenTemplatePreviewClick,
                        onSyncFromChat = viewModel::onSyncTemplateDocClick,
                    )
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
            ) {
                uiState.readyDocumentUri?.let { uri ->
                    DocumentReadyRow(
                        onSave = { saveDocumentLauncher.launch(uiState.readyDocumentFileName ?: "tareas.docx") },
                        onShare = { shareDocument(context, uri) },
                    )
                }
                uiState.readyImageUri?.let { uri ->
                    ImageReadyRow(
                        onSave = { saveImageLauncher.launch(uiState.readyImageFileName ?: "imagen.png") },
                        onShare = { shareImage(context, uri) },
                    )
                }
                if (uiState.canApplyTemplate) {
                    ApplyTemplateRow(isBuilding = uiState.isBuildingDocument, onClick = viewModel::onApplyTemplateClick)
                }
                uiState.pendingPdfFileName?.let { fileName ->
                    PendingPdfRow(
                        fileName = fileName,
                        pageCount = uiState.pendingPdfPageCount,
                        onCancel = viewModel::onCancelPendingPdf,
                    )
                }
                if (uiState.isEditingMessage) {
                    EditingMessageRow(onCancel = viewModel::onCancelEditing)
                }
                ChatInputBar(
                    text = uiState.inputText,
                    onTextChange = viewModel::onInputChange,
                    onSend = viewModel::onSendClick,
                    onAttachPdf = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                    onAttachImage = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onAttachTemplate = { templatePickerLauncher.launch(arrayOf(DOCX_MIME_TYPE)) },
                    canSend = uiState.canSend,
                    canAttachPdf = uiState.canAttachPdf,
                    canAttachImage = uiState.canAttachImages,
                    canAttachTemplate = uiState.canAttachTemplate,
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            NutritionBackdrop(modifier = Modifier.fillMaxSize())
            Column(modifier = Modifier.fillMaxSize()) {
                if (!uiState.hasApiKey) {
                    ApiKeyMissingBanner(onOpenSettings = onOpenSettings)
                }
                if (uiState.isLoadingPdf || uiState.isLoadingImages || uiState.isLoadingTemplate || uiState.isGeneratingImage) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (uiState.hasPdf) {
                    PdfChip(fileName = uiState.pdfFileName.orEmpty(), pageCount = uiState.pdfPageCount)
                }
                if (uiState.hasTemplate) {
                    TemplateChip(
                        fileName = uiState.templateFileName.orEmpty(),
                        paragraphCount = uiState.templateParagraphCount,
                    )
                }
                PullToRefreshBox(
                    isRefreshing = false,
                    onRefresh = viewModel::onRefreshChat,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    val lastUserMessageId = uiState.messages.lastOrNull { it.role == ChatRole.USER }?.id
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.messages, key = { it.id }) { message ->
                            MessageBubble(
                                message = message,
                                isEditable = !uiState.isAssistantResponding && message.id == lastUserMessageId,
                                onEditRequested = { viewModel.onEditLastMessageRequested(message.id) },
                            )
                        }
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
    }
}

@Composable
private fun providerLabel(provider: AssistantProvider): String = when (provider) {
    AssistantProvider.CLAUDE -> stringResource(R.string.provider_claude)
    AssistantProvider.GEMINI -> stringResource(R.string.provider_gemini)
}

private fun shareDocument(context: Context, uri: Uri) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = DOCX_MIME_TYPE
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share_document)))
}

private fun shareImage(context: Context, uri: Uri) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share_document)))
}

/** The always-the-same Google Doc she works from - see [TemplateDocument]. */
private fun openTemplateDocument(context: Context) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TemplateDocument.EDIT_URL)))
}

@Composable
private fun TemplateDocumentButton(
    isSyncing: Boolean,
    onOpenDocument: () -> Unit,
    onOpenPreview: () -> Unit,
    onSyncFromChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(enabled = !isSyncing) { expanded = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Filled.Description,
                    contentDescription = stringResource(R.string.cd_open_template_document),
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(stringResource(R.string.template_document_label), style = MaterialTheme.typography.labelSmall)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.template_menu_view)) },
                leadingIcon = { Icon(Icons.Filled.OpenInNew, contentDescription = null) },
                onClick = { expanded = false; onOpenDocument() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.template_menu_preview)) },
                leadingIcon = { Icon(Icons.Filled.Visibility, contentDescription = null) },
                onClick = { expanded = false; onOpenPreview() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.template_menu_sync)) },
                leadingIcon = { Icon(Icons.Filled.Sync, contentDescription = null) },
                onClick = { expanded = false; onSyncFromChat() },
            )
        }
    }
}
