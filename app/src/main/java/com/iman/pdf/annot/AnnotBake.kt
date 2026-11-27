package com.iman.pdf.annot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Image
import com.artifex.mupdf.fitz.PDFAnnotation
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.PDFPage
import com.artifex.mupdf.fitz.Quad
import com.artifex.mupdf.fitz.Point
import com.artifex.mupdf.fitz.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.ceil

object AnnotBake {
    private const val TEXT_RENDER_WIDTH = 1600f

    suspend fun bake(context: Context, source: File, marks: List<Mark>): File? =
        withContext(Dispatchers.IO) {
            if (marks.isEmpty()) return@withContext null
            var document: PDFDocument? = null
            try {
                val pdf = Document.openDocument(source.absolutePath) as? PDFDocument
                    ?: return@withContext null
                document = pdf
                marks.groupBy { it.page }.forEach { (index, pageMarks) ->
                    check(index in 0 until pdf.countPages()) { "Mark is outside the document." }
                    val page = pdf.loadPage(index) as? PDFPage ?: error("Could not load the page.")
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
                                    // Text markup has no Rect property in MuPDF; it is derived from the quads.
                                    val quads = mark.areas().map {
                                        Quad(
                                            Rect(
                                                bounds.x0 + it.left * width,
                                                bounds.y0 + it.top * height,
                                                bounds.x0 + it.right * width,
                                                bounds.y0 + it.bottom * height
                                            )
                                        )
                                    }.toTypedArray()
                                    annotation.setQuadPoints(quads)
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
                                is TextMark -> addTextStamp(page, bounds, mark)
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
                pdf.save(output.absolutePath, "garbage=compact,appearance=yes")
                output.takeIf { it.isFile && it.length() > 0 }
            } catch (e: Exception) { com.iman.pdf.logSwallowed("AnnotBake:104", e); null }
            finally { try { document?.destroy() } catch (e: Exception) { com.iman.pdf.logSwallowed("AnnotBake:105", e) } }
        }

    /**
     * Writes text, notes, ticks and dates as an image drawn by Android, so Hindi, Bengali and
     * symbols look the same in the saved PDF as on screen.
     */
    private fun addTextStamp(page: PDFPage, bounds: Rect, mark: TextMark) {
        val width = (bounds.x1 - bounds.x0).coerceAtLeast(1f)
        val height = (bounds.y1 - bounds.y0).coerceAtLeast(1f)
        val renderWidth = TEXT_RENDER_WIDTH
        val renderHeight = renderWidth * height / width

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = mark.size * renderWidth
        val lines = mark.text.split("\n")
        val metrics = paint.fontMetrics
        val pad = paint.textSize * 0.4f
        val boxWidth = ceil(lines.maxOf { paint.measureText(it) } + pad * 2).toInt().coerceIn(1, 4096)
        val boxHeight = ceil((metrics.descent - metrics.ascent) * lines.size + pad * 2)
            .toInt().coerceIn(1, 4096)
        val originX = mark.x * renderWidth - pad
        val originY = mark.y * renderHeight - pad

        var bitmap = Bitmap.createBitmap(boxWidth, boxHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(-originX, -originY)
        MarkPainter.draw(canvas, renderWidth, renderHeight, listOf(mark))

        // The stamp appearance is stored unrotated, so counter-rotate it on rotated pages.
        val rotation = rotationOf(page)
        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(-rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, boxWidth, boxHeight, matrix, true)
            bitmap.recycle()
            bitmap = rotated
        }
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        bitmap.recycle()

        val unit = width / renderWidth
        val left = bounds.x0 + originX * unit
        val top = bounds.y0 + originY * unit
        val image = Image(bytes)
        val annotation = page.createAnnotation(PDFAnnotation.TYPE_STAMP)
        try {
            // A custom name stops MuPDF from redrawing this as its default "Draft" stamp.
            annotation.setIcon("ImanText")
            annotation.setAppearance(image)
            annotation.setRect(Rect(left, top, left + boxWidth * unit, top + boxHeight * unit))
            annotation.setContents(mark.text)
            annotation.update()
        } finally {
            annotation.destroy()
            image.destroy()
        }
    }

    private fun rotationOf(page: PDFPage): Int {
        var node = page.getObject()
        repeat(32) {
            val rotate = node.get("Rotate")
            if (rotate != null && !rotate.isNull()) return ((rotate.asInteger() % 360) + 360) % 360
            val parent = node.get("Parent")
            if (parent == null || parent.isNull()) return 0
            node = parent
        }
        return 0
    }

    private fun color(value: Int): FloatArray = floatArrayOf(
        ((value shr 16) and 255) / 255f,
        ((value shr 8) and 255) / 255f,
        (value and 255) / 255f
    )
}
