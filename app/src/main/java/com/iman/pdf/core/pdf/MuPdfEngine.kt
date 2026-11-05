package com.iman.pdf.core.pdf

import android.graphics.Bitmap
import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.PDFDocument
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
