package com.iman.pdf.pdf

import android.content.Context
import android.net.Uri
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.PDFDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.SecureRandom

/**
 * Owns the complete document-password lifecycle.
 *
 * Every operation writes to a private temporary file, reopens that exact file,
 * validates its encryption state and page count, and only then returns it.
 */
object PdfProtectionService {
    @Volatile
    var lastError: String? = null
        private set

    suspend fun protect(context: Context, source: Uri, password: String): File? =
        withContext(Dispatchers.IO) {
            lastError = null
            if (password.length < 4) {
                lastError = "Password must contain at least 4 characters"
                return@withContext null
            }
            if (password.toByteArray(Charsets.UTF_8).size > 127) {
                lastError = "Password is too long (maximum 127 UTF-8 bytes)"
                return@withContext null
            }

            var inputCopy: File? = null
            var sourceDocument: PDFDocument? = null
            var verification: Document? = null
            var output: File? = null
            var keepOutput = false
            try {
                inputCopy = UriActions.copyToCache(context, source)
                    ?: run { lastError = "Could not read the selected PDF"; return@withContext null }
                sourceDocument = Document.openDocument(inputCopy.absolutePath) as? PDFDocument
                    ?: run { lastError = "The selected file is not a readable PDF"; return@withContext null }

                if (sourceDocument.needsPassword()) {
                    lastError = "This PDF is already password-protected"
                    return@withContext null
                }
                val originalPages = sourceDocument.countPages()
                if (originalPages <= 0) {
                    lastError = "The selected PDF has no readable pages"
                    return@withContext null
                }

                val permissions = Document.PERMISSION_PRINT or Document.PERMISSION_COPY or
                    Document.PERMISSION_EDIT or Document.PERMISSION_ANNOTATE or
                    Document.PERMISSION_FORM or Document.PERMISSION_ASSEMBLE or
                    Document.PERMISSION_PRINT_HQ
                val ownerPassword = newOwnerPassword()
                val safeUserPassword = password.replace("\"", "\"\"")
                val options = "encrypt=aes-256,user-password=\"$safeUserPassword\"," +
                    "owner-password=\"$ownerPassword\",permissions=$permissions,garbage=compact"

                output = File(context.cacheDir, "protected_${System.nanoTime()}.pdf")
                sourceDocument.save(output.absolutePath, options)
                if (!output.isFile || output.length() <= 0L) {
                    lastError = "The PDF engine did not create a valid output file"
                    return@withContext null
                }

                sourceDocument.destroy()
                sourceDocument = null
                verification = Document.openDocument(output.absolutePath)
                if (!verification.needsPassword()) {
                    lastError = "The saved PDF is not encrypted"
                    return@withContext null
                }
                if (!verification.authenticatePassword(password)) {
                    lastError = "The saved PDF rejected the new password"
                    return@withContext null
                }
                if (verification.countPages() != originalPages) {
                    lastError = "The saved PDF page count does not match the original"
                    return@withContext null
                }

                keepOutput = true
                output
            } catch (error: Exception) {
                lastError = error.message?.take(180) ?: error.javaClass.simpleName
                null
            } finally {
                try { verification?.destroy() } catch (_: Exception) {}
                try { sourceDocument?.destroy() } catch (_: Exception) {}
                inputCopy?.delete()
                if (!keepOutput) output?.delete()
            }
        }

    suspend fun removePassword(context: Context, source: Uri, password: String): File? =
        withContext(Dispatchers.IO) {
            lastError = null
            if (password.isEmpty()) {
                lastError = "Enter the current PDF password"
                return@withContext null
            }

            var inputCopy: File? = null
            var sourceDocument: PDFDocument? = null
            var verification: Document? = null
            var output: File? = null
            var keepOutput = false
            try {
                inputCopy = UriActions.copyToCache(context, source)
                    ?: run { lastError = "Could not read the selected PDF"; return@withContext null }
                sourceDocument = Document.openDocument(inputCopy.absolutePath) as? PDFDocument
                    ?: run { lastError = "The selected file is not a readable PDF"; return@withContext null }

                if (!sourceDocument.needsPassword()) {
                    lastError = "This PDF does not have an opening password"
                    return@withContext null
                }
                if (!sourceDocument.authenticatePassword(password)) {
                    lastError = "The current password is incorrect"
                    return@withContext null
                }

                val originalPages = sourceDocument.countPages()
                if (originalPages <= 0) {
                    lastError = "The PDF has no readable pages"
                    return@withContext null
                }

                output = File(context.cacheDir, "unprotected_${System.nanoTime()}.pdf")
                sourceDocument.save(output.absolutePath, "encrypt=none,garbage=compact")
                if (!output.isFile || output.length() <= 0L) {
                    lastError = "The PDF engine did not create an output file"
                    return@withContext null
                }

                sourceDocument.destroy()
                sourceDocument = null
                verification = Document.openDocument(output.absolutePath)
                if (verification.needsPassword()) {
                    lastError = "The saved PDF still requires a password"
                    return@withContext null
                }
                if (verification.countPages() != originalPages) {
                    lastError = "The saved PDF page count does not match the original"
                    return@withContext null
                }

                keepOutput = true
                output
            } catch (error: Exception) {
                lastError = error.message?.take(180) ?: error.javaClass.simpleName
                null
            } finally {
                try { verification?.destroy() } catch (_: Exception) {}
                try { sourceDocument?.destroy() } catch (_: Exception) {}
                inputCopy?.delete()
                if (!keepOutput) output?.delete()
            }
        }

    suspend fun verifyProtected(context: Context, source: Uri, password: String): Boolean =
        withContext(Dispatchers.IO) {
            verify(context, source) { document ->
                document.needsPassword() &&
                    document.authenticatePassword(password) &&
                    document.countPages() > 0
            }
        }

    suspend fun verifyUnprotected(context: Context, source: Uri): Boolean =
        withContext(Dispatchers.IO) {
            verify(context, source) { document ->
                !document.needsPassword() && document.countPages() > 0
            }
        }

    private suspend fun verify(
        context: Context,
        source: Uri,
        check: (Document) -> Boolean
    ): Boolean {
        var copy: File? = null
        var document: Document? = null
        return try {
            copy = UriActions.copyToCache(context, source) ?: return false
            document = Document.openDocument(copy.absolutePath)
            check(document)
        } catch (_: Exception) {
            false
        } finally {
            try { document?.destroy() } catch (_: Exception) {}
            copy?.delete()
        }
    }

    private fun newOwnerPassword(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
