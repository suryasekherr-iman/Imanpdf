package com.iman.pdf.pdf

import android.content.Context
import android.graphics.RectF
import android.net.Uri
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Image
import com.artifex.mupdf.fitz.PDFAnnotation
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.PDFPage
import com.artifex.mupdf.fitz.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Permanently removes content inside a user-selected page-space rectangle, then
 * places replacement text and/or an image in the same area.
 */
object PdfContentReplacement {
    suspend fun replace(
        context: Context,
        sourceUri: Uri,
        pageIndex: Int,
        selection: RectF,
        replacementText: String = "",
        fontSize: Float = 16f,
        imageUri: Uri? = null
    ): File? = withContext(Dispatchers.IO) {
        if (pageIndex < 0 || selection.width() <= 1f || selection.height() <= 1f ||
            (replacementText.isBlank() && imageUri == null)
        ) return@withContext null

        var source: File? = null
        var document: PDFDocument? = null
        var page: PDFPage? = null
        var image: Image? = null
        try {
            source = UriActions.copyToCache(context, sourceUri)
                ?: return@withContext null
            document = Document.openDocument(source.absolutePath) as? PDFDocument
                ?: return@withContext null
            if (pageIndex !in 0 until document.countPages()) return@withContext null
            page = document.loadPage(pageIndex) as? PDFPage
                ?: return@withContext null

            val bounds = page.bounds
            val rect = Rect(
                selection.left.coerceIn(bounds.x0, bounds.x1),
                selection.top.coerceIn(bounds.y0, bounds.y1),
                selection.right.coerceIn(bounds.x0, bounds.x1),
                selection.bottom.coerceIn(bounds.y0, bounds.y1)
            )
            require(rect.x1 - rect.x0 > 1f && rect.y1 - rect.y0 > 1f)

            val redaction = page.createAnnotation(PDFAnnotation.TYPE_REDACT)
            try {
                redaction.setRect(rect)
                redaction.setColor(floatArrayOf(1f, 1f, 1f))
                redaction.setInteriorColor(floatArrayOf(1f, 1f, 1f))
                redaction.update()
                check(redaction.applyRedaction(false, PDFPage.REDACT_IMAGE_PIXELS)) {
                    "Could not remove selected PDF content."
                }
            } finally {
                redaction.destroy()
            }

            if (replacementText.isNotBlank()) {
                val annotation = page.createAnnotation(PDFAnnotation.TYPE_FREE_TEXT)
                try {
                    annotation.setRect(rect)
                    annotation.setContents(replacementText)
                    annotation.setColor(floatArrayOf(0f, 0f, 0f))
                    annotation.setDefaultAppearance(
                        "Helv",
                        fontSize.coerceIn(6f, 48f),
                        floatArrayOf(0f, 0f, 0f)
                    )
                    check(annotation.update()) { "Could not add replacement text." }
                } finally {
                    annotation.destroy()
                }
            }

            if (imageUri != null) {
                val bytes = context.contentResolver.openInputStream(imageUri)
                    ?.use { it.readBytes() } ?: return@withContext null
                image = Image(bytes)
                val annotation = page.createAnnotation(PDFAnnotation.TYPE_STAMP)
                try {
                    annotation.setRect(rect)
                    annotation.setAppearance(checkNotNull(image))
                    check(annotation.update()) { "Could not add replacement image." }
                } finally {
                    annotation.destroy()
                }
            }

            page.update()
            val output = File(context.cacheDir, "content_replaced_${System.nanoTime()}.pdf")
            document.save(output.absolutePath, "garbage=compact,appearance=yes")
            output.takeIf { it.isFile && it.length() > 0L }
        } catch (e: Exception) {
            com.iman.pdf.logSwallowed("PdfContentReplacement:104", e)
            null
        } finally {
            try { image?.destroy() } catch (e: Exception) { com.iman.pdf.logSwallowed("PdfContentReplacement:108", e) }
            try { page?.destroy() } catch (e: Exception) { com.iman.pdf.logSwallowed("PdfContentReplacement:109", e) }
            try { document?.destroy() } catch (e: Exception) { com.iman.pdf.logSwallowed("PdfContentReplacement:110", e) }
            try { source?.delete() } catch (e: Exception) { com.iman.pdf.logSwallowed("PdfContentReplacement:111", e) }
        }
    }
}
