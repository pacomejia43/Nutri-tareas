package com.nutritareas.app.data.pdf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [base64] carries the whole file, sent to Claude as a native PDF document block so it can read
 * text, tables and scanned pages directly - it reads more reliably than any local text extraction.
 */
data class PdfContent(
    val fileName: String,
    val pageCount: Int,
    val base64: String,
)

class PdfReadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Reads a PDF picked via Storage Access Framework: validates it opens, counts its pages, and
 * base64-encodes it. Requires `PDFBoxResourceLoader.init(context)` to already have run (done once
 * in NutriTareasApp).
 */
class PdfTextExtractor(private val context: Context) {

    suspend fun extract(uri: Uri): PdfContent = withContext(Dispatchers.IO) {
        val fileName = queryDisplayName(uri) ?: "documento.pdf"
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw PdfReadException("No se pudo abrir el archivo.")
            val pageCount = PDDocument.load(ByteArrayInputStream(bytes)).use { it.numberOfPages }
            PdfContent(
                fileName = fileName,
                pageCount = pageCount,
                base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
            )
        } catch (e: PdfReadException) {
            throw e
        } catch (e: Exception) {
            throw PdfReadException("No se pudo leer el PDF.", e)
        }
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    } catch (e: Exception) {
        null
    }
}
