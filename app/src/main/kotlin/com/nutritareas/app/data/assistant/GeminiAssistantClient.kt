package com.nutritareas.app.data.assistant

import com.nutritareas.app.data.chat.ChatImageAttachment
import com.nutritareas.app.data.chat.ChatMessage
import com.nutritareas.app.data.chat.ChatPdfAttachment
import com.nutritareas.app.data.chat.ChatRole
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** A generated image (infographic, diagram, drawing...) - see [GeminiAssistantClient.generateImage]. */
data class GeneratedImage(val base64: String, val mimeType: String)

/**
 * Talks to Gemini via its plain REST API over OkHttp (no official Android SDK dependency needed:
 * this app already carries OkHttp + kotlinx.serialization for [GeminiModels]). Mirrors
 * [ClaudeAssistantClient]'s turn-resending behavior: every attached PDF and any screenshots are
 * re-attached as inline data on whichever turn first carried them, since this API is stateless too.
 */
class GeminiAssistantClient : AssistantClient {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        // Uploading a turn with one or more attached PDFs as base64 can take a while on mobile
        // data - 60s was too tight and turned a merely-slow upload into a SocketTimeoutException,
        // shown to her as a misleading "no hay conexión a internet" even though she was online.
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    override fun streamTurn(
        apiKey: String,
        modelId: String,
        history: List<ChatMessage>,
        pdfAttachments: List<ChatPdfAttachment>,
        newUserText: String,
        newUserImages: List<ChatImageAttachment>,
    ): Flow<AssistantStreamEvent> = callbackFlow {
        var activeCall: Call? = null

        val job = launch(Dispatchers.IO) {
            val textBuilder = StringBuilder()
            try {
                val requestBody = buildRequestBody(history, pdfAttachments, newUserText, newUserImages)
                val request = Request.Builder()
                    .url("$BASE_URL/models/$modelId:streamGenerateContent?alt=sse")
                    .header("x-goog-api-key", apiKey)
                    .post(json.encodeToString(requestBody).toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val call = httpClient.newCall(request)
                activeCall = call
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val bodyText = response.body?.string().orEmpty()
                        trySend(AssistantStreamEvent.Failed(errorForStatus(response.code, bodyText)))
                        return@use
                    }
                    val source = response.body?.source() ?: throw IOException("Respuesta vacía.")
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        val payload = sseDataPayload(line) ?: continue
                        val chunk = runCatching { json.decodeFromString<GeminiStreamChunk>(payload) }.getOrNull()
                        val chunkText = chunk?.candidates?.firstOrNull()?.content?.parts
                            ?.joinToString("") { it.text.orEmpty() }
                            .orEmpty()
                        if (chunkText.isNotEmpty()) {
                            textBuilder.append(chunkText)
                            trySend(AssistantStreamEvent.Delta(chunkText))
                        }
                    }
                    trySend(AssistantStreamEvent.Completed(textBuilder.toString()))
                }
            } catch (e: IOException) {
                trySend(AssistantStreamEvent.Failed(if (e.hasTimeoutCause()) AssistantError.Timeout(e) else AssistantError.Network(e)))
            } catch (e: Exception) {
                trySend(AssistantStreamEvent.Failed(AssistantError.Unknown(e)))
            }
            close()
        }
        awaitClose {
            job.cancel()
            activeCall?.cancel()
        }
    }
        // trySend() above doesn't suspend - on a full channel it just drops the element. Text
        // deltas can arrive faster than the UI consumes them (a slow recomposition, a busy main
        // thread), and callbackFlow's default buffer is only 64, so without this a long response
        // could silently lose chunks partway through and render as truncated with no error at all.
        .buffer(Channel.UNLIMITED)

    /**
     * One-shot image generation (infographics, diagrams, drawings...) - not part of [AssistantClient]
     * since Claude has no equivalent here; Paco asks for this via the `[[IMAGEN]]` marker (see
     * [AssistantPersona.systemPrompt] and [parseImageRequest]) regardless of which provider is
     * chatting, so this always runs on Gemini specifically with [IMAGE_MODEL_ID].
     */
    suspend fun generateImage(apiKey: String, modelId: String, prompt: String): GeneratedImage = withContext(Dispatchers.IO) {
        try {
            val requestBody = GeminiRequest(
                contents = listOf(GeminiContent(role = "user", parts = listOf(GeminiPart(text = prompt)))),
                generationConfig = GeminiGenerationConfig(responseModalities = listOf("TEXT", "IMAGE")),
            )
            val request = Request.Builder()
                .url("$BASE_URL/models/$modelId:generateContent")
                .header("x-goog-api-key", apiKey)
                .post(json.encodeToString(requestBody).toRequestBody(JSON_MEDIA_TYPE))
                .build()
            httpClient.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw errorForStatus(response.code, bodyText)
                val parts = runCatching { json.decodeFromString<GeminiStreamChunk>(bodyText) }.getOrNull()
                    ?.candidates?.firstOrNull()?.content?.parts.orEmpty()
                val imageData = parts.firstNotNullOfOrNull { it.inlineData }
                    ?: throw AssistantError.Unknown(IOException("La respuesta no incluyó una imagen."))
                GeneratedImage(base64 = imageData.data, mimeType = imageData.mimeType)
            }
        } catch (e: IOException) {
            throw if (e.hasTimeoutCause()) AssistantError.Timeout(e) else AssistantError.Network(e)
        }
    }

    /** Builds the request contents, placing the PDFs/images on the same turn they were first sent on. */
    internal fun buildRequestBody(
        history: List<ChatMessage>,
        pdfAttachments: List<ChatPdfAttachment>,
        newUserText: String,
        newUserImages: List<ChatImageAttachment>,
    ): GeminiRequest {
        val contents = mutableListOf<GeminiContent>()
        var pdfsAttached = false
        for (message in history) {
            val attachPdfsHere = !pdfsAttached && message.role == ChatRole.USER && pdfAttachments.isNotEmpty()
            if (attachPdfsHere) pdfsAttached = true
            contents += toGeminiContent(
                role = message.role,
                text = message.text,
                images = message.imageAttachments,
                pdfs = if (attachPdfsHere) pdfAttachments else emptyList(),
            )
        }

        val attachPdfsOnNewTurn = !pdfsAttached && pdfAttachments.isNotEmpty()
        contents += toGeminiContent(
            role = ChatRole.USER,
            text = newUserText,
            images = newUserImages,
            pdfs = if (attachPdfsOnNewTurn) pdfAttachments else emptyList(),
        )

        return GeminiRequest(
            contents = contents,
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = AssistantPersona.systemPrompt))),
        )
    }

    private fun toGeminiContent(
        role: ChatRole,
        text: String,
        images: List<ChatImageAttachment>,
        pdfs: List<ChatPdfAttachment>,
    ): GeminiContent {
        val parts = mutableListOf<GeminiPart>()
        for (pdf in pdfs) {
            if (pdf.markdown != null) {
                parts += GeminiPart(text = pdf.markdown)
            } else if (pdf.base64 != null) {
                parts += GeminiPart(inlineData = GeminiInlineData(mimeType = "application/pdf", data = pdf.base64))
            }
        }
        for (image in images) {
            parts += GeminiPart(inlineData = GeminiInlineData(mimeType = image.mimeType, data = image.base64))
        }
        if (text.isNotEmpty()) parts += GeminiPart(text = text)
        return GeminiContent(role = if (role == ChatRole.USER) "user" else "model", parts = parts)
    }

    /** [bodyText] is Google's own error JSON, if any - its message (e.g. "quota exceeded for
     *  gemini-pro-latest") is far more actionable than the bare status code, so it's kept as the
     *  cause's message instead of being discarded. */
    private fun errorForStatus(code: Int, bodyText: String): AssistantError {
        val detail = runCatching { json.decodeFromString<GeminiErrorResponse>(bodyText).error?.message }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        val cause = IOException(if (detail != null) "HTTP $code: $detail" else "HTTP $code")
        return when (code) {
            401, 403 -> AssistantError.InvalidApiKey(cause)
            404 -> AssistantError.ModelNotFound(cause)
            429 -> AssistantError.RateLimited(cause)
            in 500..599 -> AssistantError.ServerError(cause)
            else -> AssistantError.Unknown(cause)
        }
    }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** Google's current Gemini image-generation model ("Nano Banana" family). Independent of
         *  [com.nutritareas.app.data.settings.AppSettings.GEMINI_MODEL_ID], which is only for text
         *  chat - update this if Google renames or retires it. */
        const val IMAGE_MODEL_ID = "gemini-2.5-flash-image"

        /** Extracts the payload from an SSE `data: {...}` line, or null for any other SSE line (comments, blanks, other fields). */
        internal fun sseDataPayload(line: String): String? {
            if (!line.startsWith("data:")) return null
            return line.removePrefix("data:").trim().takeIf { it.isNotEmpty() }
        }
    }
}
