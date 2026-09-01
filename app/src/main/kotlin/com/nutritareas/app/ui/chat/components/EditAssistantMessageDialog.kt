package com.nutritareas.app.ui.chat.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nutritareas.app.R

/**
 * Lets her correct Paco's last reply in place, when the information in it was wrong - see
 * [com.nutritareas.app.ui.chat.ChatViewModel.onSaveAssistantMessageEdit]. Unlike editing her own
 * last message, this never asks the model anything; it only rewrites the stored text.
 */
@Composable
fun EditAssistantMessageDialog(
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_assistant_message_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 10,
            )
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
