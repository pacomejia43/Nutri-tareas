package com.nutritareas.app.data.template

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class TemplateSyncException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
private data class TemplateSyncResponse(
    val ok: Boolean = true,
    val paragraphs: List<String> = emptyList(),
    val error: String? = null,
)

@Serializable
private data class TemplateEditsRequest(val edits: List<TemplateEditItem>)

@Serializable
private data class TemplateEditItem(val index: Int, val text: String)

/**
 * Talks to the Google Apps Script Web App she deploys on [TemplateDocument] to read and write the
 * live Google Doc directly. No OAuth in the app itself - the script runs under her own Google
 * account, so the app only ever needs the (secret) Web App URL, stored like an API key.
 */
class TemplateDocSyncClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Fetches the live doc's paragraphs, in reading order, so the assistant can refer to one by index. */
    suspend fun fetchParagraphs(webAppUrl: String): List<String> = withContext(Dispatchers.IO) {
        execute { Request.Builder().url(webAppUrl).get().build() }
    }

    /** Applies [edits] (paragraph index -> new full text) to the live doc and returns its resulting paragraphs. */
    suspend fun applyEdits(webAppUrl: String, edits: Map<Int, String>): List<String> = withContext(Dispatchers.IO) {
        val payload = TemplateEditsRequest(edits.map { (index, text) -> TemplateEditItem(index, text) })
        execute {
            Request.Builder()
                .url(webAppUrl)
                .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
                .build()
        }
    }

    private fun execute(buildRequest: () -> Request): List<String> {
        try {
            httpClient.newCall(buildRequest()).execute().use { response ->
                if (!response.isSuccessful) throw TemplateSyncException("HTTP ${response.code}")
                val bodyText = response.body?.string().orEmpty()
                val parsed = runCatching { json.decodeFromString<TemplateSyncResponse>(bodyText) }
                    .getOrElse { throw TemplateSyncException("Respuesta inesperada del script.", it) }
                if (!parsed.ok) throw TemplateSyncException(parsed.error ?: "Error desconocido del script.")
                return parsed.paragraphs
            }
        } catch (e: IOException) {
            throw TemplateSyncException("No hay conexión a internet.", e)
        } catch (e: IllegalArgumentException) {
            throw TemplateSyncException("La URL de la plantilla no es válida.", e)
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
