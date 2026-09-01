package com.nutritareas.app.data.assistant

private val OPTIONS_MARKER = Regex("""\[\[OPCIONES]]""")
private const val MAX_OPTIONS = 3

/** [visibleText] is what stays in the chat bubble; [options] are the short next-step choices to
 *  show as tappable buttons - she can also always ignore them and type her own instead. */
data class QuickReplyRequest(val visibleText: String, val options: List<String>)

/**
 * Splits the assistant's reply into the part shown in chat and a short list of suggested next
 * steps, when it ends its turn with an `[[OPCIONES]]` marker (see [AssistantPersona.systemPrompt])
 * followed by one option per line. Null when there's no marker, no lines after it, or every line
 * after it is blank - an ordinary text reply.
 */
fun parseQuickReplyRequest(assistantText: String): QuickReplyRequest? {
    val match = OPTIONS_MARKER.find(assistantText) ?: return null
    val options = assistantText.substring(match.range.last + 1)
        .lines()
        .map { it.trim().trimStart('-', '•', '*').trim() }
        .filter { it.isNotEmpty() }
        .take(MAX_OPTIONS)
    if (options.isEmpty()) return null
    val visibleText = assistantText.substring(0, match.range.first).trim()
    return QuickReplyRequest(visibleText, options)
}
