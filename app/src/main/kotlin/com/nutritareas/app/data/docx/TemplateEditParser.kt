package com.nutritareas.app.data.docx

private val EDIT_MARKER = Regex("""\[\[(PARRAFO|TABLA) (\d+)]]""")

/** [paragraphs]: paragraph index -> new full text. [tables]: paragraph index -> table rows (each a list of cell
 *  strings) to insert right after that paragraph - only meaningful for the live Google Docs template. */
data class TemplateEdits(
    val paragraphs: Map<Int, String>,
    val tables: Map<Int, List<List<String>>>,
)

/**
 * Parses the assistant's reply to [com.nutritareas.app.data.assistant.AssistantPersona.APPLY_TEMPLATE_REQUEST]:
 * one or more `[[PARRAFO N]]` markers (each followed by that paragraph's full new text) and/or
 * `[[TABLA N]]` markers (each followed by table rows, one per line, cells separated by `|`), up to
 * the next marker of either kind or the end of the message. A later marker for the same index and
 * kind overrides an earlier one.
 */
fun parseTemplateEdits(assistantText: String): TemplateEdits {
    val matches = EDIT_MARKER.findAll(assistantText).toList()
    val paragraphs = linkedMapOf<Int, String>()
    val tables = linkedMapOf<Int, List<List<String>>>()
    for ((position, match) in matches.withIndex()) {
        val index = match.groupValues[2].toIntOrNull() ?: continue
        val contentStart = match.range.last + 1
        val contentEnd = if (position + 1 < matches.size) matches[position + 1].range.first else assistantText.length
        val content = assistantText.substring(contentStart, contentEnd).trim()
        if (match.groupValues[1] == "PARRAFO") {
            paragraphs[index] = content
        } else {
            val rows = content.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { line -> line.trim('|').split("|").map { cell -> cell.trim() } }
            if (rows.isNotEmpty()) tables[index] = rows
        }
    }
    return TemplateEdits(paragraphs, tables)
}
