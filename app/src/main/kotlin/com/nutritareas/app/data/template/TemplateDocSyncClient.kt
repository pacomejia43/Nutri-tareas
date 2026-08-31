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

/** How a paragraph is formatted in the live Doc - mirrors the three looks `applyParagraphEdit_` in
 *  plantilla-sync.gs already enforces, so the in-app preview can render the same way. */
enum class TemplateParagraphStyle { TITLE, SUBTITLE, NORMAL }

data class TemplateParagraph(val text: String, val style: TemplateParagraphStyle)

@Serializable
private data class TemplateSyncResponse(
    val ok: Boolean = true,
    val paragraphs: List<String> = emptyList(),
    // Absent on a script deployment that predates this field - every paragraph then falls back to
    // NORMAL in [toTemplateParagraphs], same plain look the preview always had.
    val paragraphStyles: List<String> = emptyList(),
    val error: String? = null,
)

@Serializable
private data class TemplateEditsRequest(
    val edits: List<TemplateEditItem>,
    val tableEdits: List<TemplateTableEditItem> = emptyList(),
)

@Serializable
private data class TemplateEditItem(val index: Int, val text: String)

/** [rows] is inserted as a new table right after paragraph [index] - see plantilla-sync.gs's applyTableEdit_. */
@Serializable
private data class TemplateTableEditItem(val index: Int, val rows: List<List<String>>)

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
        execute { Request.Builder().url(webAppUrl).get().build() }.paragraphs
    }

    /** Fetches the live doc's paragraphs with their Título/Subtítulo/Normal styling, for the "Ver en vivo" preview. */
    suspend fun fetchPreview(webAppUrl: String): List<TemplateParagraph> = withContext(Dispatchers.IO) {
        toTemplateParagraphs(execute { Request.Builder().url(webAppUrl).get().build() })
    }

    /**
     * Applies [edits] (paragraph index -> new full text) and [tableEdits] (paragraph index -> table
     * rows to insert right after it) to the live doc, and returns its resulting paragraphs.
     */
    suspend fun applyEdits(
        webAppUrl: String,
        edits: Map<Int, String>,
        tableEdits: Map<Int, List<List<String>>> = emptyMap(),
    ): List<String> = withContext(Dispatchers.IO) {
        val payload = TemplateEditsRequest(
            edits = edits.map { (index, text) -> TemplateEditItem(index, text) },
            tableEdits = tableEdits.map { (index, rows) -> TemplateTableEditItem(index, rows) },
        )
        execute {
            Request.Builder()
                .url(webAppUrl)
                .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
                .build()
        }.paragraphs
    }

    private fun execute(buildRequest: () -> Request): TemplateSyncResponse {
        try {
            httpClient.newCall(buildRequest()).execute().use { response ->
                if (!response.isSuccessful) throw TemplateSyncException("HTTP ${response.code}")
                val bodyText = response.body?.string().orEmpty()
                val parsed = runCatching { json.decodeFromString<TemplateSyncResponse>(bodyText) }
                    .getOrElse { throw TemplateSyncException("Respuesta inesperada del script.", it) }
                if (!parsed.ok) throw TemplateSyncException(parsed.error ?: "Error desconocido del script.")
                return parsed
            }
        } catch (e: IOException) {
            throw TemplateSyncException("No hay conexión a internet.", e)
        } catch (e: IllegalArgumentException) {
            throw TemplateSyncException("La URL de la plantilla no es válida.", e)
        }
    }

    private fun toTemplateParagraphs(response: TemplateSyncResponse): List<TemplateParagraph> =
        response.paragraphs.mapIndexed { index, text ->
            val style = when (response.paragraphStyles.getOrNull(index)) {
                "title" -> TemplateParagraphStyle.TITLE
                "subtitle" -> TemplateParagraphStyle.SUBTITLE
                else -> TemplateParagraphStyle.NORMAL
            }
            TemplateParagraph(text, style)
        }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
