package com.iman.pdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import com.iman.pdf.core.pdf.MuPdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

object PdfCompress {
    suspend fun apply(
        context: Context,
        source: File,
        dpi: Int,
        quality: Int,
        onProgress: (Int, Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val engine = MuPdfEngine()
        val output = PdfDocument()
        try {
            if (!engine.openDocument(source.absolutePath)) return@withContext null
            val total = engine.pageCount
            if (total == 0) return@withContext null
            for (index in 0 until total) {
                val ratio = engine.pageAspectRatio(index).coerceAtLeast(0.1f)
                val targetWidth = ((612f / 72f) * dpi).toInt().coerceIn(300, 2400)
                val bitmap = engine.renderPageToBitmap(index, targetWidth / 612f)
                    ?: return@withContext null
                val jpegBytes = ByteArrayOutputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(25, 100), stream)
                    stream.toByteArray()
                }
                bitmap.recycle()
                val compressed = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                    ?: return@withContext null
                val pageWidth = 612
                val pageHeight = (pageWidth / ratio).toInt().coerceAtLeast(1)
                val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = output.startPage(info)
                page.canvas.drawBitmap(compressed, null, Rect(0, 0, pageWidth, pageHeight), null)
                output.finishPage(page)
                compressed.recycle()
                onProgress(index + 1, total)
            }
            val file = File(context.cacheDir, "compressed_${System.nanoTime()}.pdf")
            file.outputStream().use { output.writeTo(it) }
            file.takeIf { it.isFile && it.length() > 0 }
        } catch (_: Exception) { null }
        finally {
            output.close()
            engine.closeDocument()
        }
    }
}
