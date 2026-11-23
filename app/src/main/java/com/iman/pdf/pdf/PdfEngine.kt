package com.iman.pdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
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

    fun renderPageToBitmap(index: Int, scale: Float = 2f): Bitmap? =
        mupdf.renderPageToBitmap(index, scale)

    private val ratios = java.util.concurrent.ConcurrentHashMap<Int, Float>()

    fun pageAspectRatio(index: Int): Float =
        ratios[index] ?: mupdf.pageAspectRatio(index).also { ratios[index] = it }
    fun pageBounds(index: Int): RectF? = mupdf.pageBounds(index)
    fun pageText(index: Int): String = mupdf.pageText(index)
    fun searchPage(index: Int, query: String): List<FloatArray> = mupdf.searchPage(index, query)

    /** Detects interactive AcroForm fields on one page without modifying the PDF. */
    fun formFields(pageIndex: Int): List<PdfFormField> =
        PdfFormFields.read(mupdf, pageIndex)

    /** Applies user-entered AcroForm values in memory; caller persists with saveDocument(). */
    fun updateFormFields(updates: List<PdfFormFieldUpdate>): Boolean =
        mupdf.updateFormFields(updates)

    fun save(output: File, options: String = "garbage=compact"): Boolean = mupdf.save(output, options)
    fun addHighlightAnnotation(pageNumber: Int, rects: List<RectF>): Boolean =
        mupdf.addHighlightAnnotation(pageNumber, rects)
    fun getTextRectsForSelection(
        pageNum: Int,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ): List<RectF> = mupdf.getTextRectsForSelection(pageNum, startX, startY, endX, endY)
    fun addTextHighlightAnnotation(pageNumber: Int, rects: List<RectF>): Boolean =
        mupdf.addTextHighlightAnnotation(pageNumber, rects)
    fun addUnderlineAnnotation(pageNumber: Int, rects: List<RectF>): Boolean =
        mupdf.addUnderlineAnnotation(pageNumber, rects)
    fun addInkAnnotation(
        pageNumber: Int,
        paths: List<List<PointF>>,
        color: Int,
        thickness: Float
    ): Boolean = mupdf.addInkAnnotation(pageNumber, paths, color, thickness)

    fun saveDocument(savePath: String): Boolean = mupdf.saveDocument(savePath)
    fun reloadDocument(filePath: String): Boolean = mupdf.reloadDocument(filePath)

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
                val deleteOnClose = copiedToCache || source.absolutePath.startsWith(
                    context.cacheDir.absolutePath + File.separator
                )
                PdfEngine(source, engine, deleteOnClose)
            } catch (_: Exception) {
                temp?.delete()
                null
            }
        }
    }
}
