package com.iman.pdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.iman.pdf.core.pdf.MuPdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Compatibility facade used by the existing Compose screens. */
class PdfEngine private constructor(
    val file: File,
    private val mupdf: MuPdfEngine
) {
    val pageCount: Int get() = mupdf.pageCount

    fun renderPage(index: Int, targetWidth: Int): Bitmap {
        val ratio = pageAspectRatio(index).coerceAtLeast(0.1f)
        val scale = targetWidth.coerceAtLeast(1) / (612f * ratio)
        return checkNotNull(mupdf.renderPageToBitmap(index, scale)) {
            "MuPDF could not render page ${index + 1}"
        }
    }

    fun pageAspectRatio(index: Int): Float = mupdf.pageAspectRatio(index)
    fun pageText(index: Int): String = mupdf.pageText(index)
    fun searchPage(index: Int, query: String): List<FloatArray> = mupdf.searchPage(index, query)
    fun save(output: File, options: String = "garbage=compact"): Boolean = mupdf.save(output, options)
    fun close() = mupdf.closeDocument()

    companion object {
        suspend fun open(context: Context, uri: Uri): PdfEngine? = withContext(Dispatchers.IO) {
            var temp: File? = null
            try {
                val source = if (uri.scheme == "file") {
                    File(checkNotNull(uri.path))
                } else {
                    temp = File(context.cacheDir, "mupdf_open_${System.nanoTime()}.pdf")
                    val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
                    input.use { stream -> temp!!.outputStream().use { stream.copyTo(it) } }
                    temp!!
                }
                val mupdf = MuPdfEngine()
                if (!mupdf.openDocument(source.absolutePath)) {
                    mupdf.closeDocument()
                    return@withContext null
                }
                PdfEngine(source, mupdf)
            } catch (_: Exception) {
                temp?.delete()
                null
            }
        }
    }
}
