package com.iman.pdf.pdf

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfSplit {

    /** "1-3, 4-6, 8" -> [0..2, 3..5, 7..7]. Returns null when the text is not valid. */
    fun parseRanges(text: String, pageCount: Int): List<IntRange>? {
        val result = ArrayList<IntRange>()
        for (part in text.split(",")) {
            val piece = part.trim()
            if (piece.isEmpty()) continue
            val bits = piece.split("-").map { it.trim() }
            val from = bits[0].toIntOrNull() ?: return null
            val to = if (bits.size == 1) from else (bits.getOrNull(1)?.toIntOrNull() ?: return null)
            if (bits.size > 2 || from < 1 || to < from || to > pageCount) return null
            result.add((from - 1)..(to - 1))
        }
        return if (result.isEmpty()) null else result
    }

    fun everyN(n: Int, pageCount: Int): List<IntRange> {
        val step = n.coerceAtLeast(1)
        val result = ArrayList<IntRange>()
        var start = 0
        while (start < pageCount) {
            result.add(start..minOf(start + step - 1, pageCount - 1))
            start += step
        }
        return result
    }

    /** Builds one PDF per range inside the cache folder. */
    suspend fun split(context: Context, source: File, ranges: List<IntRange>): List<File> {
        val out = ArrayList<File>()
        for (range in ranges) {
            val order = range.toList()
            val file = PdfOrganize.apply(context, source, order, order.map { 0 }) ?: return emptyList()
            out.add(file)
        }
        return out
    }

    /** Copies the built files into the folder chosen by the user. Returns how many were saved. */
    suspend fun writeToFolder(
        context: Context,
        files: List<File>,
        ranges: List<IntRange>,
        treeUri: Uri,
        baseName: String
    ): Int = withContext(Dispatchers.IO) {
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        val clean = baseName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "Document" }
        var saved = 0
        files.forEachIndexed { index, file ->
            try {
                val range = ranges[index]
                val name = clean + "_pages_" + (range.first + 1) + "-" + (range.last + 1) + ".pdf"
                val target = DocumentsContract.createDocument(
                    context.contentResolver,
                    parent,
                    "application/pdf",
                    name
                )
                val stream = if (target != null) context.contentResolver.openOutputStream(target) else null
                if (stream != null) {
                    stream.use { out -> file.inputStream().use { it.copyTo(out) } }
                    saved++
                }
            } catch (e: Exception) {
            }
        }
        saved
    }
}
