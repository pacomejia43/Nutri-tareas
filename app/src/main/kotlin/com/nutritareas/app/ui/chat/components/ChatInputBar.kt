package com.nutritareas.app.ui.chat.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nutritareas.app.R

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachPdf: () -> Unit,
    canSend: Boolean,
    canAttach: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(tonalElevation = 3.dp, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onAttachPdf, enabled = canAttach) {
                Icon(Icons.Filled.AttachFile, contentDescription = stringResource(R.string.cd_attach_pdf))
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.input_placeholder)) },
                maxLines = 5,
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onSend, enabled = canSend) {
                Icon(Icons.Filled.Send, contentDescription = stringResource(R.string.cd_send))
            }
        }
    }
}
