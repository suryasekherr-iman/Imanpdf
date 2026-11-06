package com.iman.pdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfExport {
    suspend fun allText(
        context: Context,
        file: File,
        documentId: String = file.absolutePath
    ): String = withContext(Dispatchers.IO) {
        val engine = com.iman.pdf.core.pdf.MuPdfEngine()
        try {
            if (!engine.openDocument(file.absolutePath)) return@withContext ""
            buildString {
                for (index in 0 until engine.pageCount) {
                    val native = engine.pageText(index).trim()
                    val text = if (native.isNotEmpty()) native
                    else OcrStore.loadPage(context, documentId, index)?.text.orEmpty().trim()
                    if (text.isNotEmpty()) {
                        if (isNotEmpty()) append("\n\n")
                        append("--- Page ").append(index + 1).append(" ---\n").append(text)
                    }
                }
            }
        } catch (_: Exception) {
            ""
        } finally {
            engine.closeDocument()
        }
    }

    suspend fun writeText(context: Context, uri: Uri, text: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openOutputStream(uri) ?: return@withContext false
                stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                true
            } catch (_: Exception) { false }
        }

    suspend fun writeImages(
        context: Context,
        engine: PdfEngine,
        treeUri: Uri,
        baseName: String
    ): Int {
        val parent = withContext(Dispatchers.IO) {
            DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
        }
        val clean = baseName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "Document" }
        var saved = 0
        for (index in 0 until engine.pageCount) {
            val ok = try {
                val bitmap = withContext(Dispatchers.IO) {
                    engine.renderPage(index, 1600)
                }
                try {
                    withContext(Dispatchers.IO) {
                        val name = clean + "_page_" + (index + 1).toString().padStart(3, '0') + ".jpg"
                        val target = DocumentsContract.createDocument(context.contentResolver, parent, "image/jpeg", name)
                        val stream = if (target != null) context.contentResolver.openOutputStream(target) else null
                        if (stream == null) false else stream.use {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)
                        }
                    }
                } finally {
                    withContext(Dispatchers.IO) { if (!bitmap.isRecycled) bitmap.recycle() }
                }
            } catch (_: Exception) { false }
            if (ok) saved++
        }
        return saved
    }
}
