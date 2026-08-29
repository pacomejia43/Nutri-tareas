package com.nutritareas.app.data.docx

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element

class DocxTemplateException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A picked .docx template: every zip entry kept byte-for-byte (so [DocxTemplateWriter] can
 * reproduce styles, fonts, media and layout untouched) plus the body paragraphs read out of
 * word/document.xml, in reading order, so the assistant can see them and refer to one by index.
 */
data class DocxTemplate(
    val fileName: String,
    val entries: Map<String, ByteArray>,
    val paragraphs: List<String>,
)

/**
 * Reads any .docx picked via Storage Access Framework, e.g. a template exported from Google Docs
 * with its own styling that must survive editing untouched.
 */
class DocxTemplateReader(private val context: Context) {

    suspend fun read(uri: Uri): DocxTemplate = withContext(Dispatchers.IO) {
        val fileName = queryDisplayName(uri) ?: "plantilla.docx"
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw DocxTemplateException("No se pudo abrir el archivo.")
            val entries = readZipEntries(bytes)
            val documentXml = entries["word/document.xml"]
                ?: throw DocxTemplateException("El archivo no parece un .docx válido.")
            DocxTemplate(fileName = fileName, entries = entries, paragraphs = extractParagraphs(documentXml))
        } catch (e: DocxTemplateException) {
            throw e
        } catch (e: Exception) {
            throw DocxTemplateException("No se pudo leer esa plantilla.", e)
        }
    }

    private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Exposed for [DocxTemplateWriter] and tests: one entry per body `<w:p>`, its runs' text concatenated. */
        internal fun extractParagraphs(documentXml: ByteArray): List<String> {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(ByteArrayInputStream(documentXml))
            val paragraphNodes = document.getElementsByTagName("w:p")
            return (0 until paragraphNodes.length).map { i ->
                val p = paragraphNodes.item(i) as Element
                val textNodes = p.getElementsByTagName("w:t")
                (0 until textNodes.length).joinToString("") { textNodes.item(it).textContent }
            }
        }
    }
}
