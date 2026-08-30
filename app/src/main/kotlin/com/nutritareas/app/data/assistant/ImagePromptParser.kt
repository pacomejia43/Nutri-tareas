package com.nutritareas.app.data.assistant

private val IMAGE_MARKER = Regex("""\[\[IMAGEN]]""")

/** [visibleText] is what stays in the chat bubble; [prompt] is the visual description sent to the image model. */
data class ImageRequest(val visibleText: String, val prompt: String)

/**
 * Splits the assistant's reply into the part shown in chat and the image prompt, when it ends its
 * turn with an `[[IMAGEN]]` marker (see [AssistantPersona.systemPrompt]) asking for an image or
 * infographic to be generated. Null when there's no marker - an ordinary text reply.
 */
fun parseImageRequest(assistantText: String): ImageRequest? {
    val match = IMAGE_MARKER.find(assistantText) ?: return null
    val prompt = assistantText.substring(match.range.last + 1).trim()
    if (prompt.isEmpty()) return null
    val visibleText = assistantText.substring(0, match.range.first).trim()
    return ImageRequest(visibleText, prompt)
}
