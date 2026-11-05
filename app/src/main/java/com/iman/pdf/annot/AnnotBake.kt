package com.iman.pdf.annot

import android.content.Context
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.PDFAnnotation
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.PDFPage
import com.artifex.mupdf.fitz.Quad
import com.artifex.mupdf.fitz.Point
import com.artifex.mupdf.fitz.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AnnotBake {
    suspend fun bake(context: Context, source: File, marks: List<Mark>): File? =
        withContext(Dispatchers.IO) {
            if (marks.isEmpty()) return@withContext null
            var document: PDFDocument? = null
            try {
                document = Document.openDocument(source.absolutePath) as? PDFDocument
                    ?: return@withContext null
                marks.groupBy { it.page }.forEach { (index, pageMarks) ->
                    if (index !in 0 until document.countPages()) return@forEach
                    val page = document.loadPage(index) as? PDFPage ?: return@forEach
                    try {
                        val bounds = page.bounds
                        val width = (bounds.x1 - bounds.x0).coerceAtLeast(1f)
                        val height = (bounds.y1 - bounds.y0).coerceAtLeast(1f)
                        pageMarks.forEach { mark ->
                            when (mark) {
                                is HighlightMark -> {
                                    val type = when (mark.kind) {
                                        1 -> PDFAnnotation.TYPE_UNDERLINE
                                        2 -> PDFAnnotation.TYPE_STRIKE_OUT
                                        else -> PDFAnnotation.TYPE_HIGHLIGHT
                                    }
                                    val annotation = page.createAnnotation(type)
                                    val rect = Rect(
                                        bounds.x0 + mark.left * width,
                                        bounds.y0 + mark.top * height,
                                        bounds.x0 + mark.right * width,
                                        bounds.y0 + mark.bottom * height
                                    )
                                    annotation.setRect(rect)
                                    annotation.setQuadPoints(arrayOf(Quad(rect)))
                                    annotation.setColor(color(mark.color))
                                    annotation.setOpacity(if (mark.kind == 0) 0.42f else 1f)
                                    annotation.update()
                                    annotation.destroy()
                                }
                                is StrokeMark -> {
                                    val annotation = page.createAnnotation(PDFAnnotation.TYPE_INK)
                                    val points = mark.points.map {
                                        Point(bounds.x0 + it.first * width, bounds.y0 + it.second * height)
                                    }.toTypedArray()
                                    annotation.setInkList(arrayOf(points))
                                    annotation.setColor(color(mark.color))
                                    annotation.setBorderWidth((mark.width * width).coerceAtLeast(0.8f))
                                    annotation.update()
                                    annotation.destroy()
                                }
                                is TextMark -> {
                                    val annotation = page.createAnnotation(PDFAnnotation.TYPE_FREE_TEXT)
                                    val x = bounds.x0 + mark.x * width
                                    val y = bounds.y0 + mark.y * height
                                    val fontSize = (mark.size * width).coerceIn(6f, 96f)
                                    val textWidth = (mark.text.lines().maxOfOrNull { it.length } ?: 1) * fontSize * 0.65f
                                    val textHeight = mark.text.lines().size * fontSize * 1.3f
                                    annotation.setRect(Rect(x, y, x + textWidth, y + textHeight))
                                    annotation.setContents(mark.text)
                                    annotation.setColor(color(mark.color))
                                    annotation.setDefaultAppearance("Helv", fontSize, color(mark.color))
                                    annotation.update()
                                    annotation.destroy()
                                }
                                is SignMark -> {
                                    val annotation = page.createAnnotation(PDFAnnotation.TYPE_INK)
                                    val boxWidth = mark.width * width
                                    val boxHeight = boxWidth / mark.signature.aspect.coerceAtLeast(0.1f)
                                    val strokes = mark.signature.strokes.map { stroke ->
                                        stroke.map { point ->
                                            Point(
                                                bounds.x0 + (mark.x + point.first * mark.width) * width,
                                                bounds.y0 + (mark.y + point.second * (boxHeight / height)) * height
                                            )
                                        }.toTypedArray()
                                    }.toTypedArray()
                                    annotation.setInkList(strokes)
                                    annotation.setColor(color(mark.color))
                                    annotation.setBorderWidth((boxWidth * 0.015f).coerceAtLeast(0.8f))
                                    annotation.update()
                                    annotation.destroy()
                                }
                            }
                        }
                        page.update()
                    } finally { page.destroy() }
                }
                val output = File(context.cacheDir, "annotated_${System.nanoTime()}.pdf")
                document.save(output.absolutePath, "garbage=compact,appearance=yes")
                output.takeIf { it.isFile && it.length() > 0 }
            } catch (_: Exception) { null }
            finally { try { document?.destroy() } catch (_: Exception) {} }
        }

    private fun color(value: Int): FloatArray = floatArrayOf(
        ((value shr 16) and 255) / 255f,
        ((value shr 8) and 255) / 255f,
        (value and 255) / 255f
    )
}
