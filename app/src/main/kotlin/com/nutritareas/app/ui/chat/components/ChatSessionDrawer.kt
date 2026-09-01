package com.nutritareas.app.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nutritareas.app.R
import com.nutritareas.app.ui.chat.ChatSessionSummary

/**
 * The hamburger-menu drawer listing every conversation she's keeping - see
 * [com.nutritareas.app.ui.chat.ChatViewModel.onSelectSession]. Lets her work several "tareas" at
 * once, switching between them instead of always overwriting the one conversation the app used
 * to keep.
 */
@Composable
fun ChatSessionDrawerContent(
    sessions: List<ChatSessionSummary>,
    activeSessionId: String,
    onSelect: (String) -> Unit,
    onNewChat: () -> Unit,
    onDeleteRequested: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(modifier = modifier) {
        Text(
            text = stringResource(R.string.conversations_drawer_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.new_conversation)) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            selected = false,
            onClick = onNewChat,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(sessions, key = { it.id }) { summary ->
                val isActive = summary.id == activeSessionId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(if (isActive) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable { onSelect(summary.id) }
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = summary.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                    )
                    IconButton(onClick = { onDeleteRequested(summary.id) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.delete_conversation),
                            tint = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
