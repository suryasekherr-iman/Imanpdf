package com.iman.pdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.PDFDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfTools {
    @Volatile var lastProtectError: String? = null
    suspend fun merge(context: Context, sources: List<Uri>): File? = withContext(Dispatchers.IO) {
        val copies = ArrayList<File>()
        val output = PDFDocument()
        val opened = ArrayList<PDFDocument>()
        try {
            for (uri in sources) {
                val copy = UriActions.copyToCache(context, uri) ?: return@withContext null
                copies.add(copy)
                val source = Document.openDocument(copy.absolutePath) as? PDFDocument
                    ?: return@withContext null
                opened.add(source)
                for (page in 0 until source.countPages()) {
                    output.graftPage(output.countPages(), source, page)
                }
            }
            if (output.countPages() == 0) return@withContext null
            val result = File(context.cacheDir, "merged_${System.nanoTime()}.pdf")
            output.save(result.absolutePath, "garbage=compact")
            result.takeIf { it.isFile && it.length() > 0 }
        } catch (_: Exception) { null }
        finally {
            output.destroy()
            opened.forEach { try { it.destroy() } catch (_: Exception) {} }
            copies.forEach { it.delete() }
        }
    }

    suspend fun imagesToPdf(context: Context, images: List<Uri>): File? = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        try {
            var count = 0
            for (uri in images) {
                val bitmap = decodeScaled(context, uri, 1600) ?: continue
                count++
                val info = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, count).create()
                val page = document.startPage(info)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                document.finishPage(page)
                bitmap.recycle()
            }
            if (count == 0) null else {
                val out = File(context.cacheDir, "images_${System.nanoTime()}.pdf")
                out.outputStream().use { document.writeTo(it) }
                out
            }
        } catch (_: Exception) { null }
        finally { document.close() }
    }

    suspend fun needsPassword(context: Context, source: Uri): Boolean = withContext(Dispatchers.IO) {
        var copy: File? = null
        var doc: Document? = null
        try {
            copy = UriActions.copyToCache(context, source) ?: return@withContext false
            doc = Document.openDocument(copy.absolutePath)
            doc.needsPassword()
        } catch (_: Exception) { false }
        finally { try { doc?.destroy() } catch (_: Exception) {}; copy?.delete() }
    }

    suspend fun protect(context: Context, source: Uri, password: String): File? = withContext(Dispatchers.IO) {
        lastProtectError = null
        if (password.isEmpty()) {
            lastProtectError = "Password cannot be empty"
            return@withContext null
        }
        if (password.toByteArray(Charsets.UTF_8).size > 127) {
            lastProtectError = "Password is too long (maximum 127 UTF-8 bytes)"
            return@withContext null
        }
        var copy: File? = null
        var doc: PDFDocument? = null
        var verification: Document? = null
        var out: File? = null
        var keepOutput = false
        try {
            copy = UriActions.copyToCache(context, source) ?: run { lastProtectError = "Could not read selected PDF"; return@withContext null }
            doc = Document.openDocument(copy.absolutePath) as? PDFDocument ?: run { lastProtectError = "Selected file is not a readable PDF"; return@withContext null }
            if (doc.needsPassword()) { lastProtectError = "Selected PDF is already password-protected"; return@withContext null }

            val permissions = Document.PERMISSION_PRINT or Document.PERMISSION_COPY or
                Document.PERMISSION_EDIT or Document.PERMISSION_ANNOTATE or
                Document.PERMISSION_FORM or Document.PERMISSION_ASSEMBLE or
                Document.PERMISSION_PRINT_HQ
            val escaped = password.replace("\"", "\"\"")
            val options = "encrypt=aes-256,user-password=\"$escaped\",owner-password=\"$escaped\",permissions=$permissions,garbage=compact"
            out = File(context.cacheDir, "protected_${System.nanoTime()}.pdf")
            doc.save(out.absolutePath, options)
            if (!out.isFile || out.length() == 0L) { lastProtectError = "MuPDF did not create an output file"; return@withContext null }

            // Verify the saved PDF itself, not just the in-memory document.
            doc.destroy()
            doc = null
            verification = Document.openDocument(out.absolutePath)
            if (!verification.needsPassword()) { lastProtectError = "Output PDF was not encrypted"; return@withContext null }
            if (!verification.authenticatePassword(password)) { lastProtectError = "Output PDF rejected password in verification"; return@withContext null }
            if (verification.countPages() <= 0) { lastProtectError = "Protected PDF has no readable pages"; return@withContext null }
            keepOutput = true
            out
        } catch (e: Exception) {
            lastProtectError = e.message?.take(180) ?: e.javaClass.simpleName
            null
        }
        finally {
            try { verification?.destroy() } catch (_: Exception) {}
            try { doc?.destroy() } catch (_: Exception) {}
            copy?.delete()
            if (!keepOutput) out?.delete()
        }
    }

    /** Re-open the exact saved destination and confirm that its password unlocks a readable PDF. */
    suspend fun verifyProtected(context: Context, source: Uri, password: String): Boolean =
        withContext(Dispatchers.IO) {
            var copy: File? = null
            var doc: Document? = null
            try {
                copy = UriActions.copyToCache(context, source) ?: return@withContext false
                doc = Document.openDocument(copy.absolutePath)
                doc.needsPassword() &&
                    doc.authenticatePassword(password) &&
                    doc.countPages() > 0
            } catch (_: Exception) {
                false
            } finally {
                try { doc?.destroy() } catch (_: Exception) {}
                copy?.delete()
            }
        }

    suspend fun unlock(context: Context, source: Uri, password: String): File? = withContext(Dispatchers.IO) {
        var copy: File? = null
        var doc: Document? = null
        var verification: Document? = null
        var out: File? = null
        var keepOutput = false
        try {
            copy = UriActions.copyToCache(context, source) ?: return@withContext null
            doc = Document.openDocument(copy.absolutePath)
            if (doc.needsPassword() && !doc.authenticatePassword(password)) return@withContext null
            val pdf = doc as? PDFDocument ?: return@withContext null
            out = File(context.cacheDir, "unlocked_${System.nanoTime()}.pdf")
            pdf.save(out.absolutePath, "encrypt=none,garbage=compact")
            if (!out.isFile || out.length() == 0L) return@withContext null

            // Confirm the decrypted copy opens without a password before handing it to the viewer.
            doc.destroy()
            doc = null
            verification = Document.openDocument(out.absolutePath)
            if (verification.needsPassword() || verification.countPages() <= 0) return@withContext null
            keepOutput = true
            out
        } catch (_: Exception) { null }
        finally {
            try { verification?.destroy() } catch (_: Exception) {}
            try { doc?.destroy() } catch (_: Exception) {}
            copy?.delete()
            if (!keepOutput) out?.delete()
        }
    }

    private fun decodeScaled(context: Context, uri: Uri, maxSide: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    } catch (_: Exception) { null }
}
