package com.nutritareas.app.data.docx

private val PARAGRAPH_MARKER = Regex("""\[\[PARRAFO (\d+)]]""")

/**
 * Parses the assistant's reply to [com.nutritareas.app.data.assistant.AssistantPersona.APPLY_TEMPLATE_REQUEST]:
 * one or more `[[PARRAFO N]]` markers, each followed by that paragraph's full new text up to the
 * next marker (or the end of the message). A later marker for the same index overrides an earlier one.
 */
fun parseTemplateEdits(assistantText: String): Map<Int, String> {
    val matches = PARAGRAPH_MARKER.findAll(assistantText).toList()
    val edits = linkedMapOf<Int, String>()
    for ((position, match) in matches.withIndex()) {
        val index = match.groupValues[1].toIntOrNull() ?: continue
        val contentStart = match.range.last + 1
        val contentEnd = if (position + 1 < matches.size) matches[position + 1].range.first else assistantText.length
        edits[index] = assistantText.substring(contentStart, contentEnd).trim()
    }
    return edits
}
