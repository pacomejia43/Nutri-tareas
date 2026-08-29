package com.nutritareas.app.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.nutritareas.app.data.chat.ChatImageAttachment
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImageReadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Reads a picked screenshot/photo, downscales it and re-encodes as JPEG before it's sent to the
 * assistant - phone screenshots can be several MB at native resolution, and a vision model doesn't
 * need more than ~1600px on the long side to read on-screen text reliably.
 */
class ImageProcessor(private val context: Context) {

    suspend fun process(uri: Uri): ChatImageAttachment = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw ImageReadException("No se pudo abrir la imagen.")
            val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw ImageReadException("No se pudo leer la imagen.")
            val scaled = downscale(original, MAX_DIMENSION_PX)
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            if (scaled !== original) scaled.recycle()
            original.recycle()
            ChatImageAttachment(
                base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP),
                mimeType = "image/jpeg",
            )
        } catch (e: ImageReadException) {
            throw e
        } catch (e: Exception) {
            throw ImageReadException("No se pudo procesar la imagen.", e)
        }
    }

    private fun downscale(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longSide = maxOf(bitmap.width, bitmap.height)
        if (longSide <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longSide
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    companion object {
        private const val MAX_DIMENSION_PX = 1568
        private const val JPEG_QUALITY = 85
    }
}
