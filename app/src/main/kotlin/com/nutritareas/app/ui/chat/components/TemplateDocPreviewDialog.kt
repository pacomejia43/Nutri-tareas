package com.nutritareas.app.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nutritareas.app.R
import com.nutritareas.app.data.template.TemplateParagraph
import com.nutritareas.app.data.template.TemplateParagraphStyle

/**
 * A small floating window that polls the live Google Doc (see [com.nutritareas.app.data.template.TemplateDocSyncClient])
 * so she can watch Paco's edits land in near real time without leaving the app - unlike
 * "Ver documento", which hands off to the full Google Docs editor. The paragraph list renders on
 * a white "page" with the same Título/Subtítulo/Normal look plantilla-sync.gs enforces in the
 * real Doc, so it reads like a quick look at the document rather than a plain text dump.
 */
@Composable
fun TemplateDocPreviewDialog(
    paragraphs: List<TemplateParagraph>,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.72f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (errorMessage == null) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                ),
                        )
                        Text(
                            text = stringResource(R.string.template_preview_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Row {
                        IconButton(onClick = onRefresh, enabled = !isLoading) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cd_refresh_template_preview))
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close_template_preview))
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.template_preview_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )
                errorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                when {
                    isLoading && paragraphs.isEmpty() && errorMessage == null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    paragraphs.isEmpty() -> {
                        if (errorMessage == null) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(R.string.template_preview_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    else -> {
                        DocumentPage(paragraphs, modifier = Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** A white page, like Google Docs always shows regardless of the phone's own light/dark theme. */
@Composable
private fun DocumentPage(paragraphs: List<TemplateParagraph>, modifier: Modifier = Modifier) {
    Surface(
        color = Color.White,
        contentColor = Color(0xFF202124),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier.border(1.dp, Color(0xFFDADCE0), RoundedCornerShape(4.dp)),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(paragraphs) { paragraph ->
                DocumentParagraph(paragraph)
            }
        }
    }
}

@Composable
private fun DocumentParagraph(paragraph: TemplateParagraph) {
    val text = paragraph.text.ifBlank { stringResource(R.string.template_paragraph_empty) }
    when (paragraph.style) {
        TemplateParagraphStyle.TITLE -> Text(
            text = text.uppercase(),
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        TemplateParagraphStyle.SUBTITLE -> Text(
            text = text,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        TemplateParagraphStyle.NORMAL -> Text(
            text = text,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            textAlign = TextAlign.Justify,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
