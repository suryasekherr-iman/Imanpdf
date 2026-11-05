package com.iman.pdf.pdf

import android.content.Context
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.PDFPage
import com.artifex.mupdf.fitz.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfOrganize {
    suspend fun apply(
        context: Context,
        source: File,
        order: List<Int>,
        rotations: List<Int>,
        second: File? = null
    ): File? = withContext(Dispatchers.IO) {
        if (order.isEmpty() || order.size != rotations.size) return@withContext null
        var input: PDFDocument? = null
        var other: PDFDocument? = null
        val output = PDFDocument()
        try {
            val inputDoc = Document.openDocument(source.absolutePath) as? PDFDocument
                ?: return@withContext null
            input = inputDoc
            val otherDoc = if (second != null) {
                Document.openDocument(second.absolutePath) as? PDFDocument
                    ?: return@withContext null
            } else null
            other = otherDoc
            order.forEachIndexed { position, original ->
                when {
                    original == -1 -> {
                        val first = if (inputDoc.countPages() > 0) inputDoc.loadPage(0) else null
                        val bounds = try { first?.bounds } finally { first?.destroy() }
                        val width = bounds?.let { it.x1 - it.x0 } ?: 595f
                        val height = bounds?.let { it.y1 - it.y0 } ?: 842f
                        val pageObject = output.addPage(
                            Rect(0f, 0f, width, height),
                            0,
                            output.newDictionary(),
                            ""
                        )
                        output.insertPage(-1, pageObject)
                    }
                    original <= -2 -> {
                        val src = otherDoc ?: return@withContext null
                        val sourceIndex = -original - 2
                        if (sourceIndex !in 0 until src.countPages()) return@withContext null
                        output.graftPage(position, src, sourceIndex)
                    }
                    else -> {
                        if (original !in 0 until inputDoc.countPages()) return@withContext null
                        output.graftPage(position, inputDoc, original)
                    }
                }
                val page = output.loadPage(position) as? PDFPage
                if (page != null) {
                    try {
                        val pageObject = page.getObject()
                        val rotationObject = pageObject.get("Rotate")
                        val existingRotation = if (rotationObject == null || rotationObject.isNull()) 0
                            else rotationObject.asInteger()
                        val rotation = (existingRotation + rotations[position]) % 360
                        pageObject.put("Rotate", (rotation + 360) % 360)
                        page.update()
                    } finally { page.destroy() }
                }
            }
            val file = File(context.cacheDir, "organized_${System.nanoTime()}.pdf")
            output.save(file.absolutePath, "garbage=compact")
            file.takeIf { it.isFile && it.length() > 0 }
        } catch (_: Exception) { null }
        finally {
            try { output.destroy() } catch (_: Exception) {}
            try { input?.destroy() } catch (_: Exception) {}
            try { other?.destroy() } catch (_: Exception) {}
        }
    }
}
