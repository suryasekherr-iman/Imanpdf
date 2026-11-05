package com.iman.pdf.pdf

import android.content.Context
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.PDFPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfCrop {
    suspend fun apply(
        context: Context,
        source: File,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): File? = withContext(Dispatchers.IO) {
        var doc: PDFDocument? = null
        try {
            val opened = com.artifex.mupdf.fitz.Document.openDocument(source.absolutePath)
            doc = opened as? PDFDocument ?: run { opened.destroy(); return@withContext null }
            for (index in 0 until doc.countPages()) {
                val page = doc.loadPage(index) as? PDFPage ?: continue
                try {
                    val box = page.getBounds(com.artifex.mupdf.fitz.Page.CROP_BOX)
                    val width = (box.x1 - box.x0).coerceAtLeast(1f)
                    val height = (box.y1 - box.y0).coerceAtLeast(1f)
                    val l = left.coerceIn(0f, 0.95f)
                    val t = top.coerceIn(0f, 0.95f)
                    val r = right.coerceIn(l + 0.01f, 1f)
                    val b = bottom.coerceIn(t + 0.01f, 1f)
                    val rect = doc.newArray().apply {
                        push(box.x0 + width * l)
                        push(box.y0 + height * t)
                        push(box.x0 + width * r)
                        push(box.y0 + height * b)
                    }
                    page.getObject().put("CropBox", rect)
                    page.update()
                } finally { page.destroy() }
            }
            val output = File(context.cacheDir, "cropped_${System.nanoTime()}.pdf")
            doc.save(output.absolutePath, "garbage=compact")
            output.takeIf { it.isFile && it.length() > 0 }
        } catch (_: Exception) { null }
        finally { try { doc?.destroy() } catch (_: Exception) {} }
    }
}
