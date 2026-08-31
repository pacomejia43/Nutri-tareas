package com.nutritareas.app.data.assistant

import com.nutritareas.app.data.chat.ChatImageAttachment
import com.nutritareas.app.data.chat.ChatMessage
import com.nutritareas.app.data.chat.ChatPdfAttachment
import com.nutritareas.app.data.chat.ChatRole
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiAssistantClientTest {

    private val client = GeminiAssistantClient()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun message(role: ChatRole, text: String, images: List<ChatImageAttachment> = emptyList()) = ChatMessage(
        id = "id",
        role = role,
        text = text,
        timestampEpochMillis = 0L,
        imageAttachments = images,
    )

    private fun pdf(fileName: String, base64: String) = ChatPdfAttachment(fileName = fileName, pageCount = 1, base64 = base64)

    @Test
    fun `new turn with no history, pdf or images is a single plain text part`() {
        val request = client.buildRequestBody(history = emptyList(), pdfAttachments = emptyList(), newUserText = "Hola", newUserImages = emptyList())

        assertEquals(1, request.contents.size)
        assertEquals("user", request.contents[0].role)
        assertEquals(listOf(GeminiPart(text = "Hola")), request.contents[0].parts)
    }

    @Test
    fun `pdf attaches once on the first user turn, not on later or new turns`() {
        val history = listOf(
            message(ChatRole.USER, "aquí está el PDF"),
            message(ChatRole.ASSISTANT, "gracias, lo leo"),
            message(ChatRole.USER, "¿ya terminaste?"),
        )

        val request = client.buildRequestBody(
            history = history,
            pdfAttachments = listOf(pdf("tareas.pdf", "UERGQkFTRTY0")),
            newUserText = "¿algo más?",
            newUserImages = emptyList(),
        )

        assertEquals(4, request.contents.size)
        val firstTurnParts = request.contents[0].parts
        assertTrue(firstTurnParts.any { it.inlineData?.mimeType == "application/pdf" })
        for (laterContent in request.contents.drop(1)) {
            assertTrue(laterContent.parts.none { it.inlineData?.mimeType == "application/pdf" })
        }
    }

    @Test
    fun `pdf attaches on the new turn when history has no prior user message`() {
        val history = listOf(message(ChatRole.ASSISTANT, "hola, mándame tus tareas"))

        val request = client.buildRequestBody(
            history = history,
            pdfAttachments = listOf(pdf("tareas.pdf", "UERGQkFTRTY0")),
            newUserText = "aquí va",
            newUserImages = emptyList(),
        )

        assertTrue(request.contents[0].parts.none { it.inlineData != null })
        assertTrue(request.contents[1].parts.any { it.inlineData?.mimeType == "application/pdf" })
    }

    @Test
    fun `multiple pdfs all attach together on the same first user turn`() {
        val request = client.buildRequestBody(
            history = emptyList(),
            pdfAttachments = listOf(pdf("matematicas.pdf", "TUFURQ=="), pdf("historia.pdf", "SElTVA==")),
            newUserText = "aquí van mis dos tareas",
            newUserImages = emptyList(),
        )

        val parts = request.contents.single().parts
        val pdfParts = parts.filter { it.inlineData?.mimeType == "application/pdf" }
        assertEquals(2, pdfParts.size)
        assertEquals(listOf("TUFURQ==", "SElTVA=="), pdfParts.map { it.inlineData?.data })
        assertEquals("aquí van mis dos tareas", parts.last().text)
    }

    @Test
    fun `images on the new turn become inlineData parts with their own mime type, followed by the text part`() {
        val images = listOf(
            ChatImageAttachment(base64 = "aW1nMQ==", mimeType = "image/jpeg"),
            ChatImageAttachment(base64 = "aW1nMg==", mimeType = "image/jpeg"),
        )

        val request = client.buildRequestBody(history = emptyList(), pdfAttachments = emptyList(), newUserText = "mira esto", newUserImages = images)

        val parts = request.contents.single().parts
        assertEquals(3, parts.size)
        assertEquals("aW1nMQ==", parts[0].inlineData?.data)
        assertEquals("aW1nMg==", parts[1].inlineData?.data)
        assertEquals("mira esto", parts[2].text)
    }

    @Test
    fun `images already in history are re-sent on their own turn, not duplicated elsewhere`() {
        val history = listOf(
            message(ChatRole.USER, "mira mi tarea", images = listOf(ChatImageAttachment(base64 = "aW1n", mimeType = "image/png"))),
            message(ChatRole.ASSISTANT, "ya la vi"),
        )

        val request = client.buildRequestBody(history = history, pdfAttachments = emptyList(), newUserText = "gracias", newUserImages = emptyList())

        assertTrue(request.contents[0].parts.any { it.inlineData?.mimeType == "image/png" })
        assertTrue(request.contents[1].parts.none { it.inlineData != null })
        assertTrue(request.contents[2].parts.none { it.inlineData != null })
    }

    @Test
    fun `assistant turns map to Gemini's 'model' role`() {
        val history = listOf(message(ChatRole.ASSISTANT, "respuesta anterior"))

        val request = client.buildRequestBody(history = history, pdfAttachments = emptyList(), newUserText = "sigue", newUserImages = emptyList())

        assertEquals("model", request.contents[0].role)
        assertEquals("user", request.contents[1].role)
    }

    @Test
    fun `system instruction carries the persona prompt with no role`() {
        val request = client.buildRequestBody(history = emptyList(), pdfAttachments = emptyList(), newUserText = "hola", newUserImages = emptyList())

        assertEquals(AssistantPersona.systemPrompt, request.systemInstruction?.parts?.single()?.text)
        assertNull(request.systemInstruction?.role)
    }

    @Test
    fun `request encodes with camelCase field names and drops null fields`() {
        val request = client.buildRequestBody(history = emptyList(), pdfAttachments = emptyList(), newUserText = "hola", newUserImages = emptyList())

        val encoded = json.encodeToString(request)

        assertTrue(encoded.contains("\"systemInstruction\""))
        assertTrue(encoded.contains("\"contents\""))
        assertTrue(encoded.contains("\"parts\""))
        assertTrue(encoded.contains("\"role\":\"user\""))
        assertTrue("inlineData should be omitted when a part has none" , !encoded.contains("\"inlineData\":null"))
    }

    @Test
    fun `decodes a realistic streamed chunk down to its text`() {
        val payload = """
            {"candidates": [{"content": {"role": "model", "parts": [{"text": "Hola mi amor"}]}, "index": 0}]}
        """.trimIndent()

        val chunk = json.decodeFromString<GeminiStreamChunk>(payload)

        assertEquals("Hola mi amor", chunk.candidates.single().content?.parts?.single()?.text)
    }

    @Test
    fun `sseDataPayload extracts the JSON after the data field, ignoring other SSE lines`() {
        assertEquals("""{"a":1}""", GeminiAssistantClient.sseDataPayload("""data: {"a":1}"""))
        assertNull(GeminiAssistantClient.sseDataPayload(""))
        assertNull(GeminiAssistantClient.sseDataPayload("event: message"))
        assertNull(GeminiAssistantClient.sseDataPayload("data:"))
        assertNull(GeminiAssistantClient.sseDataPayload("data:   "))
    }
}
