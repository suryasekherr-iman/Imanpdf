package com.iman.pdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.iman.pdf.core.pdf.MuPdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Compatibility facade for existing screens; all PDF work is delegated to MuPDF. */
class PdfEngine private constructor(
    val file: File,
    private val mupdf: MuPdfEngine,
    private val deleteFileOnClose: Boolean
) {
    val pageCount: Int get() = mupdf.pageCount
    fun renderPage(index: Int, targetWidth: Int): Bitmap {
        return checkNotNull(mupdf.renderPageToWidth(index, targetWidth)) { "MuPDF render failed" }
    }
    fun pageAspectRatio(index: Int): Float = mupdf.pageAspectRatio(index)
    fun pageText(index: Int): String = mupdf.pageText(index)
    fun searchPage(index: Int, query: String): List<FloatArray> = mupdf.searchPage(index, query)
    fun save(output: File, options: String = "garbage=compact"): Boolean = mupdf.save(output, options)
    fun close() {
        mupdf.closeDocument()
        if (deleteFileOnClose) file.delete()
    }

    companion object {
        suspend fun open(context: Context, uri: Uri): PdfEngine? = withContext(Dispatchers.IO) {
            var temp: File? = null
            try {
                val copiedToCache = uri.scheme != "file"
                val source = if (!copiedToCache) File(checkNotNull(uri.path)) else {
                    temp = File(context.cacheDir, "mupdf_${System.nanoTime()}.pdf")
                    val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
                    input.use { stream -> temp!!.outputStream().use { stream.copyTo(it) } }
                    temp!!
                }
                val engine = MuPdfEngine()
                if (!engine.openDocument(source.absolutePath)) {
                    engine.closeDocument()
                    return@withContext null
                }
                PdfEngine(source, engine, copiedToCache)
            } catch (_: Exception) {
                temp?.delete()
                null
            }
        }
    }
}
