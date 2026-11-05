package com.iman.pdf.pdf

import android.content.Context
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Outline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class TocEntry(val title: String, val page: Int, val level: Int)

object Bookmarks {
    private const val PREFS = "iman_bookmarks"
    private fun key(documentId: String, pageCount: Int) = documentId + "|" + pageCount

    fun load(context: Context, documentId: String, pageCount: Int): List<Int> =
        (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(documentId, pageCount), "") ?: "")
            .split(",").mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0 until pageCount }.distinct().sorted()

    private fun save(context: Context, documentId: String, pageCount: Int, pages: List<Int>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(documentId, pageCount), pages.joinToString(",")).apply()
    }

    fun add(context: Context, documentId: String, pageCount: Int, page: Int): Boolean {
        val current = load(context, documentId, pageCount)
        if (page in current) return false
        save(context, documentId, pageCount, (current + page).sorted())
        return true
    }

    private fun noteKey(documentId: String, pageCount: Int, page: Int) =
        "note|" + key(documentId, pageCount) + "|" + page

    fun note(context: Context, documentId: String, pageCount: Int, page: Int): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(noteKey(documentId, pageCount, page), "") ?: ""

    fun saveNote(context: Context, documentId: String, pageCount: Int, page: Int, note: String) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (note.isBlank()) editor.remove(noteKey(documentId, pageCount, page))
        else editor.putString(noteKey(documentId, pageCount, page), note.trim())
        editor.apply()
    }

    fun remove(context: Context, documentId: String, pageCount: Int, page: Int) {
        save(context, documentId, pageCount, load(context, documentId, pageCount).filter { it != page })
        saveNote(context, documentId, pageCount, page, "")
    }

    suspend fun toc(context: Context, file: File): List<TocEntry> = withContext(Dispatchers.IO) {
        var document: Document? = null
        val result = ArrayList<TocEntry>()
        try {
            document = Document.openDocument(file.absolutePath)
            walk(document, document.loadOutline() ?: emptyArray(), 0, result)
        } catch (_: Exception) {
        } finally { try { document?.destroy() } catch (_: Exception) {} }
        result
    }

    private fun walk(document: Document, nodes: Array<Outline>, level: Int, out: MutableList<TocEntry>) {
        if (level > 6 || out.size >= 2000) return
        nodes.forEach { node ->
            if (out.size >= 2000) return
            val location = try { document.resolveLink(node) } catch (_: Exception) { null }
            val page = try { document.pageNumberFromLocation(location) } catch (_: Exception) { -1 }
            val title = node.title.orEmpty().trim()
            if (title.isNotEmpty()) out.add(TocEntry(title, page, level))
            node.down?.let { walk(document, it, level + 1, out) }
        }
    }
}
