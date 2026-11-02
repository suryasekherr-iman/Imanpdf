package com.iman.pdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfExport {

    /** Whole-document text, or "" when the PDF has no readable text (for example a scan). */
    suspend fun allText(context: Context, file: File): String =
        withContext(Dispatchers.IO) {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                PDDocument.load(file).use { document ->
                    val builder = StringBuilder()
                    val stripper = PDFTextStripper()
                    for (index in 0 until document.numberOfPages) {
                        stripper.startPage = index + 1
                        stripper.endPage = index + 1
                        val text = (stripper.getText(document) ?: "").trim()
                        if (text.isNotEmpty()) {
                            if (builder.isNotEmpty()) builder.append("\n\n")
                            builder.append("--- Page ").append(index + 1).append(" ---\n")
                            builder.append(text)
                        }
                    }
                    builder.toString()
                }
            } catch (e: Exception) {
                ""
            }
        }

    suspend fun writeText(context: Context, uri: Uri, text: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openOutputStream(uri)
                if (stream == null) {
                    false
                } else {
                    stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    true
                }
            } catch (e: Exception) {
                false
            }
        }

    /** Saves every page as a JPG inside the chosen folder. Returns how many pages were saved. */
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
                val bitmap = engine.renderPage(index, 1600)
                try {
                    withContext(Dispatchers.IO) {
                        val name = clean + "_page_" + (index + 1).toString().padStart(3, '0') + ".jpg"
                        val target = DocumentsContract.createDocument(
                            context.contentResolver,
                            parent,
                            "image/jpeg",
                            name
                        )
                        val stream = if (target != null) {
                            context.contentResolver.openOutputStream(target)
                        } else {
                            null
                        }
                        if (stream == null) {
                            false
                        } else {
                            stream.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                        }
                    }
                } finally {
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                false
            }
            if (ok) saved++
        }
        return saved
    }
}
