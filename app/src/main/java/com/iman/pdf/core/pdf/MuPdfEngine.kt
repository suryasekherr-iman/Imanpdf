package com.iman.pdf.core.pdf

import android.graphics.Bitmap
import android.graphics.RectF
import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.PDFPage
import com.artifex.mupdf.fitz.PDFWidget
import com.artifex.mupdf.fitz.Rect
import com.artifex.mupdf.fitz.Pixmap
import com.iman.pdf.pdf.TextGlyph
import java.io.File

/** MuPDF-backed PDF document owner. Native resources are always explicitly released. */
class MuPdfEngine {
    private var document: Document? = null
    private var pdf: PDFDocument? = null
    private var source: File? = null
    private var cachedPageCount: Int = 0

    // Cached during openDocument (called from Dispatchers.IO) so Compose reads never touch MuPDF.
    @get:Synchronized
    val pageCount: Int get() = cachedPageCount
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
            cachedPageCount = opened.countPages()
            source = file
            pdf != null
        } catch (e: Exception) {
            com.iman.pdf.logSwallowed("MuPdfEngine:48", e)
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
        } catch (e: Exception) { com.iman.pdf.logSwallowed("MuPdfEngine:62", e); false }
    }

    @Synchronized
    fun needsPassword(): Boolean = try { document?.needsPassword() ?: false } catch (e: Exception) { com.iman.pdf.logSwallowed("MuPdfEngine:66", e); false }

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
        } catch (e: Exception) {
            com.iman.pdf.logSwallowed("MuPdfEngine:98", e)
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
        } catch (e: Exception) { com.iman.pdf.logSwallowed("MuPdfEngine:116", e); null }
    }

    @Synchronized
    fun pageBounds(index: Int): RectF? {
        val doc = document ?: return null
        if (index !in 0 until doc.countPages()) return null
        var page: com.artifex.mupdf.fitz.Page? = null
        return try {
            page = doc.loadPage(index)
            val bounds = page.bounds
            RectF(bounds.x0, bounds.y0, bounds.x1, bounds.y1)
        } catch (e: Exception) {
            com.iman.pdf.logSwallowed("MuPdfEngine:128", e)
            null
        } finally {
            try { page?.destroy() } catch (e: Exception) { com.iman.pdf.logSwallowed("MuPdfEngine:132", e) }
        }
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
        } catch (e: Exception) { com.iman.pdf.logSwallowed("MuPdfEngine:146", e); 1f / 1.414f }
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
        } catch (e: Exception) { com.iman.pdf.logSwallowed("MuPdfEngine:169", e); "" }
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
        } catch (e: Exception) { com.iman.pdf.logSwallowed("MuPdfEngine:194", e); emptyList() }
    }

    /** Glyphs of one page, line by line in reading order, in page-relative coordinates. */
    @Synchronized
    fun textGlyphLines(index: Int): List<List<TextGlyph>> {
        val doc = document ?: return emptyList()
        if (index !in 0 until doc.countPages()) return emptyList()
        return try {
            val page = doc.loadPage(index)
            try {
                val bounds = page.bounds
                val width = (bounds.x1 - bounds.x0).coerceAtLeast(1f)
                val height = (bounds.y1 - bounds.y0).coerceAtLeast(1f)
                val text = page.toStructuredText()
                try {
                    text.blocks.flatMap { block ->
                        block.lines.map { line ->
                            line.chars.map { character ->
                                val glyph = character.quad.toRect()
                                TextGlyph(
                                    character.c,
                                    (glyph.x0 - bounds.x0) / width,
                                    (glyph.y0 - bounds.y0) / height,
                                    (glyph.x1 - bounds.x0) / width,
                                    (glyph.y1 - bounds.y0) / height
                                )
                            }
                        }
                    }
                } finally { text.destroy() }
            } finally { page.destroy() }
        } catch (e: Exception) { com.iman.pdf.logSwallowed("MuPdfEngine:glyphs", e); emptyList() }
    }

    /** Applies supported AcroForm values without saving or replacing the source file. */
    @Synchronized
    fun updateFormFields(updates: List<com.iman.pdf.pdf.PdfFormFieldUpdate>): Boolean {
        val doc = document ?: return false
        if (updates.isEmpty()) return false
        val byPage = updates.groupBy { it.pageIndex }
        var allApplied = true
        byPage.forEach { (pageIndex, pageUpdates) ->
            if (pageIndex !in 0 until cachedPageCount) {
                allApplied = false
                return@forEach
            }
            var page: PDFPage? = null
            try {
                page = doc.loadPage(pageIndex) as? PDFPage
                val loaded = page ?: run {
                    allApplied = false
                    return@forEach
                }
                val widgets = loaded.widgets.orEmpty()
                pageUpdates.forEach { update ->
                    val widget = widgets.getOrNull(update.fieldIndex)
                    if (widget == null || widget.isReadOnly) {
                        allApplied = false
                    } else {
                        try {
                            when (widget.fieldType) {
                                PDFWidget.TYPE_TEXT -> check(widget.setTextValue(update.value))
                                PDFWidget.TYPE_COMBOBOX, PDFWidget.TYPE_LISTBOX ->
                                    check(widget.setChoiceValue(update.value))
                                PDFWidget.TYPE_CHECKBOX -> {
                                    val wantsChecked = update.value == "true"
                                    val isChecked = !widget.value.equals("Off", ignoreCase = true) &&
                                        widget.value.isNotBlank()
                                    if (wantsChecked != isChecked) check(widget.toggle())
                                }
                                else -> allApplied = false
                            }
                        } catch (e: Exception) {
                            com.iman.pdf.logSwallowed("MuPdfEngine:461", e)
                            allApplied = false
                        }
                    }
                }
                loaded.update()
            } catch (e: Exception) {
                com.iman.pdf.logSwallowed("MuPdfEngine:468", e)
                allApplied = false
            } finally {
                try { page?.destroy() } catch (e: Exception) { com.iman.pdf.logSwallowed("MuPdfEngine:472", e) }
            }
        }
        return allApplied
    }

    @Synchronized
    fun saveDocument(originalPath: String): Boolean {
        val activeDocument = pdf ?: return false
        val originalFile = File(originalPath)
        val tempFile = File(originalPath + ".tmp")
        val backupFile = File(originalPath + ".bak")
        return try {
            require(originalFile.isFile) { "Original PDF does not exist." }
            tempFile.delete()
            backupFile.delete()

            // MuPDF writes while it owns the document; release its file lock before replacement.
            activeDocument.save(tempFile.absolutePath, "compress")
            check(tempFile.isFile && tempFile.length() > 0L) { "Temporary PDF is empty." }
            closeDocument()

            // Keep a rollback copy until the new file has been installed successfully.
            if (originalFile.exists()) {
                check(originalFile.renameTo(backupFile)) { "Could not move original PDF aside." }
            }
            if (!tempFile.renameTo(originalFile)) {
                if (backupFile.exists()) backupFile.renameTo(originalFile)
                error("Could not replace original PDF with saved temporary file.")
            }

            val reopened = openDocument(originalFile.absolutePath)
            if (reopened) {
                backupFile.delete()
                true
            } else {
                originalFile.delete()
                if (backupFile.exists()) backupFile.renameTo(originalFile)
                openDocument(originalFile.absolutePath)
                false
            }
        } catch (e: Exception) {
            com.iman.pdf.logSwallowed("MuPdfEngine:513", e)
            try {
                if (!originalFile.exists() && backupFile.exists()) backupFile.renameTo(originalFile)
                if (document == null && originalFile.isFile) openDocument(originalFile.absolutePath)
            } catch (e: Exception) { com.iman.pdf.logSwallowed("MuPdfEngine:518", e) }
            false
        } finally {
            try { tempFile.delete() } catch (e: Exception) { com.iman.pdf.logSwallowed("MuPdfEngine:521", e) }
            // Never delete the only recoverable copy if replacement/reopen failed.
            try {
                if (!backupFile.exists() || (originalFile.isFile && document != null)) {
                    backupFile.delete()
                }
            } catch (e: Exception) { com.iman.pdf.logSwallowed("MuPdfEngine:527", e) }
        }
    }

    /** Swaps the open file for [newFile] (for example one with annotations written in) and reopens it. */
    @Synchronized
    fun replaceDocument(newFile: File): Boolean {
        val target = source ?: return false
        if (!newFile.isFile || newFile.length() == 0L) return false
        val backup = File(target.absolutePath + ".bak")
        backup.delete()
        closeDocument()
        if (!target.renameTo(backup)) {
            openDocument(target.absolutePath)
            return false
        }
        val installed = newFile.renameTo(target) ||
            runCatching { newFile.copyTo(target, overwrite = true) }.isSuccess
        if (installed && openDocument(target.absolutePath)) {
            backup.delete()
            return true
        }
        target.delete()
        backup.renameTo(target)
        openDocument(target.absolutePath)
        return false
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
        } catch (e: Exception) { com.iman.pdf.logSwallowed("MuPdfEngine:543", e); false }
    }

    @Synchronized
    fun pdfDocument(): PDFDocument? = pdf
    @Synchronized
    fun document(): Document? = document

    @Synchronized
    fun closeDocument() {
        try { document?.destroy() } catch (e: Exception) { com.iman.pdf.logSwallowed("MuPdfEngine:553", e) }
        document = null
        pdf = null
        source = null
        cachedPageCount = 0
    }
}
