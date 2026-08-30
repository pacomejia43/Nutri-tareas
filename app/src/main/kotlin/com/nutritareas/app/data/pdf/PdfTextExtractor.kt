package com.nutritareas.app.data.pdf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [markdown] is the PDF's embedded text, converted to Markdown, so it can be sent as plain chat
 * text instead of the raw file - cheaper and simpler for both Claude and Gemini to process. It's
 * null when the PDF has no meaningful text layer (e.g. it's a scan/photo), in which case [base64]
 * is sent instead as a native document so the model can still read it via vision/OCR.
 */
data class PdfContent(
    val fileName: String,
    val pageCount: Int,
    val base64: String,
    val markdown: String?,
)

class PdfReadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Reads a PDF picked via Storage Access Framework: validates it opens, counts its pages, extracts
 * its text as Markdown, and base64-encodes the whole file as a fallback for scanned PDFs. Requires
 * `PDFBoxResourceLoader.init(context)` to already have run (done once in NutriTareasApp).
 */
class PdfTextExtractor(private val context: Context) {

    suspend fun extract(uri: Uri): PdfContent = withContext(Dispatchers.IO) {
        val fileName = queryDisplayName(uri) ?: "documento.pdf"
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw PdfReadException("No se pudo abrir el archivo.")
            PDDocument.load(ByteArrayInputStream(bytes)).use { document ->
                PdfContent(
                    fileName = fileName,
                    pageCount = document.numberOfPages,
                    base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                    markdown = extractMarkdown(document, fileName),
                )
            }
        } catch (e: PdfReadException) {
            throw e
        } catch (e: Exception) {
            throw PdfReadException("No se pudo leer el PDF.", e)
        }
    }

    /** Null when the extracted text is too short to be a real text layer (a scanned/photographed PDF). */
    private fun extractMarkdown(document: PDDocument, fileName: String): String? {
        val stripper = PDFTextStripper().apply { sortByPosition = true }
        val pages = (1..document.numberOfPages).map { page ->
            stripper.startPage = page
            stripper.endPage = page
            stripper.getText(document).trim()
        }
        if (pages.sumOf { it.length } < MIN_EXTRACTED_CHARS) return null

        val body = if (pages.size == 1) {
            pages.first()
        } else {
            pages.mapIndexed { index, text -> "## Página ${index + 1}\n\n$text" }.joinToString("\n\n")
        }
        return "# $fileName\n\n$body"
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val MIN_EXTRACTED_CHARS = 40
    }
}
