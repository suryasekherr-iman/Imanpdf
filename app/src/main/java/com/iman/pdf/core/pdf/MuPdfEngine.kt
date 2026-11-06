package com.iman.pdf.core.pdf

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.PDFPage
import com.artifex.mupdf.fitz.PDFAnnotation
import com.artifex.mupdf.fitz.Point
import com.artifex.mupdf.fitz.Quad
import com.artifex.mupdf.fitz.Rect
import com.artifex.mupdf.fitz.Pixmap
import java.io.File

/** MuPDF-backed PDF document owner. Native resources are always explicitly released. */
class MuPdfEngine {
    private var document: Document? = null
    private var pdf: PDFDocument? = null
    private var source: File? = null

    @get:Synchronized
    val pageCount: Int get() = document?.countPages() ?: 0
    @get:Synchronized
    val file: File? get() = source

    @Synchronized
    fun openDocument(path: String): Boolean {
        closeDocument()
        return try {
            val file = File(path)
            require(file.isFile && file.canRead())
            val opened = Document.openDocument(file.absolutePath)
            if (!opened.isPDF() || opened.needsPassword()) {
                opened.destroy()
                return false
            }
            document = opened
            pdf = opened as? PDFDocument
            source = file
            pdf != null
        } catch (_: Exception) {
            closeDocument()
            false
        }
    }

    @Synchronized
    fun authenticate(password: String): Boolean {
        val opened = document ?: return false
        return try {
            val accepted = opened.authenticatePassword(password)
            if (accepted) pdf = opened as? PDFDocument
            accepted && pdf != null
        } catch (_: Exception) { false }
    }

    @Synchronized
    fun needsPassword(): Boolean = try { document?.needsPassword() ?: false } catch (_: Exception) { false }

    @Synchronized
    fun renderPageToBitmap(index: Int, scale: Float = 1f): Bitmap? {
        val doc = document ?: return null
        if (index !in 0 until doc.countPages()) return null
        var pixmap: Pixmap? = null
        var page: com.artifex.mupdf.fitz.Page? = null
        return try {
            page = doc.loadPage(index)
            pixmap = page.toPixmap(Matrix(scale.coerceIn(0.25f, 6f)), ColorSpace.DeviceRGB, false, true)
            val w = pixmap.width
            val h = pixmap.height
            val stride = pixmap.stride
            val components = pixmap.numberOfComponents
            val samples = pixmap.samples
            require(w > 0 && h > 0 && components >= 3)
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                var p = y * stride
                val row = y * w
                for (x in 0 until w) {
                    val r = samples[p].toInt() and 255
                    val g = samples[p + 1].toInt() and 255
                    val b = samples[p + 2].toInt() and 255
                    pixels[row + x] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
                    p += components
                }
            }
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                it.setPixels(pixels, 0, w, 0, 0, w, h)
            }
        } catch (_: Exception) {
            null
        } finally {
            pixmap?.destroy()
            page?.destroy()
        }
    }

    @Synchronized
    fun renderPageToWidth(index: Int, targetWidth: Int): Bitmap? {
        val doc = document ?: return null
        if (index !in 0 until doc.countPages()) return null
        return try {
            val page = doc.loadPage(index)
            val width = try { (page.bounds.x1 - page.bounds.x0).coerceAtLeast(1f) }
                finally { page.destroy() }
            renderPageToBitmap(index, targetWidth.coerceAtLeast(1) / width)
        } catch (_: Exception) { null }
    }

    @Synchronized
    fun pageAspectRatio(index: Int): Float {
        val doc = document ?: return 1f / 1.414f
        if (index !in 0 until doc.countPages()) return 1f / 1.414f
        return try {
            val page = doc.loadPage(index)
            try {
                val b = page.bounds
                (b.x1 - b.x0).coerceAtLeast(1f) / (b.y1 - b.y0).coerceAtLeast(1f)
            } finally { page.destroy() }
        } catch (_: Exception) { 1f / 1.414f }
    }

    @Synchronized
    fun pageText(index: Int): String {
        val doc = document ?: return ""
        if (index !in 0 until doc.countPages()) return ""
        return try {
            val page = doc.loadPage(index)
            try {
                val text = page.toStructuredText()
                try {
                    buildString {
                        text.blocks.forEach { block ->
                            block.lines.forEach { line ->
                                line.chars.forEach { appendCodePoint(it.c) }
                                append('\n')
                            }
                            append('\n')
                        }
                    }.trim()
                } finally { text.destroy() }
            } finally { page.destroy() }
        } catch (_: Exception) { "" }
    }

    @Synchronized
    fun searchPage(index: Int, query: String): List<FloatArray> {
        val doc = document ?: return emptyList()
        if (query.isBlank() || index !in 0 until doc.countPages()) return emptyList()
        return try {
            val page = doc.loadPage(index)
            try {
                val bounds = page.bounds
                val width = (bounds.x1 - bounds.x0).coerceAtLeast(1f)
                val height = (bounds.y1 - bounds.y0).coerceAtLeast(1f)
                page.search(query).mapNotNull { quads ->
                    if (quads.isEmpty()) null else {
                        val rects = quads.map { it.toRect() }
                        floatArrayOf(
                            ((rects.minOf { it.x0 } - bounds.x0) / width).coerceIn(0f, 1f),
                            ((rects.minOf { it.y0 } - bounds.y0) / height).coerceIn(0f, 1f),
                            ((rects.maxOf { it.x1 } - bounds.x0) / width).coerceIn(0f, 1f),
                            ((rects.maxOf { it.y1 } - bounds.y0) / height).coerceIn(0f, 1f)
                        )
                    }
                }
            } finally { page.destroy() }
        } catch (_: Exception) { emptyList() }
    }

    /**
     * Adds normalized page-relative rectangles (0..1) as real PDF highlight annotations.
     */
    @Synchronized
    fun addHighlightAnnotation(pageNumber: Int, rects: List<RectF>): Boolean {
        val doc = document ?: return false
        if (pageNumber !in 0 until doc.countPages() || rects.isEmpty()) return false
        var page: PDFPage? = null
        return try {
            page = doc.loadPage(pageNumber) as? PDFPage ?: return false
            val bounds = page.bounds
            val width = (bounds.x1 - bounds.x0).coerceAtLeast(1f)
            val height = (bounds.y1 - bounds.y0).coerceAtLeast(1f)
            rects.forEach { input ->
                val left = input.left.coerceIn(0f, 1f)
                val top = input.top.coerceIn(0f, 1f)
                val right = input.right.coerceIn(0f, 1f)
                val bottom = input.bottom.coerceIn(0f, 1f)
                if (right - left > 0.001f && bottom - top > 0.001f) {
                    val rect = Rect(
                        bounds.x0 + left * width,
                        bounds.y0 + top * height,
                        bounds.x0 + right * width,
                        bounds.y0 + bottom * height
                    )
                    val annotation = page.createAnnotation(PDFAnnotation.TYPE_HIGHLIGHT)
                    annotation.setQuadPoints(arrayOf(Quad(rect)))
                    annotation.setRect(rect)
                    annotation.setColor(floatArrayOf(1f, 0.92f, 0.20f))
                    annotation.setOpacity(0.42f)
                    check(annotation.update()) { "MuPDF could not update highlight annotation." }
                }
            }
            page.update()
        } catch (_: Exception) {
            false
        } finally {
            page?.destroy()
        }
    }

    /**
     * Adds normalized page-relative paths (0..1) as real PDF ink annotations.
     */
    @Synchronized
    fun addInkAnnotation(
        pageNumber: Int,
        paths: List<List<PointF>>,
        color: Int,
        thickness: Float
    ): Boolean {
        val doc = document ?: return false
        if (pageNumber !in 0 until doc.countPages() || paths.isEmpty()) return false
        var page: PDFPage? = null
        return try {
            page = doc.loadPage(pageNumber) as? PDFPage ?: return false
            val bounds = page.bounds
            val width = (bounds.x1 - bounds.x0).coerceAtLeast(1f)
            val height = (bounds.y1 - bounds.y0).coerceAtLeast(1f)
            val annotation = page.createAnnotation(PDFAnnotation.TYPE_INK)
            val ink = paths.mapNotNull { path ->
                path.map { point ->
                    Point(
                        bounds.x0 + point.x.coerceIn(0f, 1f) * width,
                        bounds.y0 + point.y.coerceIn(0f, 1f) * height
                    )
                }.takeIf { it.isNotEmpty() }?.toTypedArray()
            }.toTypedArray()
            if (ink.isEmpty()) return false
            annotation.setInkList(ink)
            val alphaColor = android.graphics.Color.valueOf(color)
            annotation.setColor(floatArrayOf(alphaColor.red(), alphaColor.green(), alphaColor.blue()))
            annotation.setOpacity(alphaColor.alpha())
            annotation.setBorderWidth((thickness * width).coerceAtLeast(0.5f))
            val allPoints = ink.flatMap { it.toList() }
            val pad = (thickness * width).coerceAtLeast(0.5f)
            annotation.setRect(
                Rect(
                    allPoints.minOf { it.x } - pad,
                    allPoints.minOf { it.y } - pad,
                    allPoints.maxOf { it.x } + pad,
                    allPoints.maxOf { it.y } + pad
                )
            )
            check(annotation.update()) { "MuPDF could not update ink annotation." }
            page.update()
        } catch (_: Exception) {
            false
        } finally {
            page?.destroy()
        }
    }

    @Synchronized
    fun saveDocument(savePath: String): Boolean {
        val doc = pdf ?: return false
        val current = source ?: return false
        val destination = File(savePath)
        return try {
            if (destination.absoluteFile == current.absoluteFile) {
                val temporary = File(current.parentFile, current.nameWithoutExtension + ".annotating.tmp.pdf")
                try {
                    doc.save(temporary.absolutePath, "compress")
                    closeDocument()
                    if (!temporary.isFile || temporary.length() == 0L) return false
                    if (!temporary.copyTo(current, overwrite = true).exists()) return false
                    temporary.delete()
                    openDocument(current.absolutePath)
                } finally {
                    temporary.delete()
                }
            } else {
                doc.save(destination.absolutePath, "compress")
                destination.isFile && destination.length() > 0L
            }
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    fun reloadDocument(filePath: String): Boolean {
        closeDocument()
        return openDocument(filePath)
    }

    @Synchronized
    fun save(output: File, options: String = "garbage=compact"): Boolean {
        val doc = pdf ?: return false
        return try {
            doc.save(output.absolutePath, options)
            output.isFile && output.length() > 0
        } catch (_: Exception) { false }
    }

    @Synchronized
    fun pdfDocument(): PDFDocument? = pdf
    @Synchronized
    fun document(): Document? = document

    @Synchronized
    fun closeDocument() {
        try { document?.destroy() } catch (_: Exception) {}
        document = null
        pdf = null
        source = null
    }
}
