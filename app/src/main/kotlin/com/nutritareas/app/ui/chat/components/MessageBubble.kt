package com.nutritareas.app.ui.chat.components

import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.nutritareas.app.R
import com.nutritareas.app.data.chat.ChatImageAttachment
import com.nutritareas.app.data.chat.ChatMessage
import com.nutritareas.app.data.chat.ChatRole

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    isEditable: Boolean = false,
    onEditRequested: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isUser = message.role == ChatRole.USER
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedLabel = stringResource(R.string.message_copied)
    var showEditMenu by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        val containerColor = when {
            message.isError -> MaterialTheme.colorScheme.errorContainer
            isUser -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
        val contentColor = when {
            message.isError -> MaterialTheme.colorScheme.onErrorContainer
            isUser -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Box {
            Surface(
                color = containerColor,
                contentColor = contentColor,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.widthIn(max = 320.dp).combinedClickable(
                    enabled = message.text.isNotBlank(),
                    onClick = {
                        clipboardManager.setText(AnnotatedString(message.text))
                        Toast.makeText(context, copiedLabel, Toast.LENGTH_SHORT).show()
                    },
                    onLongClick = if (isEditable) {
                        { showEditMenu = true }
                    } else {
                        null
                    },
                ),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (message.imageAttachments.isNotEmpty()) {
                        ImageAttachmentsRow(message.imageAttachments)
                        if (message.text.isNotBlank()) Spacer(Modifier.height(8.dp))
                    }
                    if (message.text.isNotBlank()) {
                        Text(text = message.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if (isEditable) {
                DropdownMenu(expanded = showEditMenu, onDismissRequest = { showEditMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit_message)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { showEditMenu = false; onEditRequested() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageAttachmentsRow(attachments: List<ChatImageAttachment>, modifier: Modifier = Modifier) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(attachments) { attachment ->
            val bitmap = remember(attachment.base64) { decodeThumbnail(attachment.base64) }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.cd_attach_image),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(10.dp)),
                )
            }
        }
    }
}

private fun decodeThumbnail(base64: String): ImageBitmap? = try {
    val bytes = Base64.decode(base64, Base64.NO_WRAP)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
} catch (e: Exception) {
    null
}

@Composable
fun TypingIndicatorBubble(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = MaterialTheme.shapes.large,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(R.string.thinking), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
