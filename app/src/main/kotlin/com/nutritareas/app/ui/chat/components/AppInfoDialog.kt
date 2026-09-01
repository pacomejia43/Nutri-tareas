package com.nutritareas.app.ui.chat.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nutritareas.app.R

/** Everything the app can do, in plain language, for when she taps the info button and wants a
 *  reminder of what to ask Paco for - see [com.nutritareas.app.ui.chat.ChatViewModel.onOpenAppInfo]. */
@Composable
fun AppInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_info_title)) },
        text = {
            Text(
                text = stringResource(R.string.app_info_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}
