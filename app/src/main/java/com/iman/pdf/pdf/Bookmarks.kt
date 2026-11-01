package com.iman.pdf.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class TocEntry(
    val title: String,
    val page: Int,
    val level: Int
)

object Bookmarks {

    private const val PREFS = "iman_bookmarks"
    private var initialized = false

    private fun key(name: String, pageCount: Int): String = name + "|" + pageCount

    fun load(context: Context, name: String, pageCount: Int): List<Int> {
        val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(name, pageCount), "") ?: ""
        return text.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0 until pageCount }
            .distinct()
            .sorted()
    }

    private fun save(context: Context, name: String, pageCount: Int, pages: List<Int>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key(name, pageCount), pages.joinToString(","))
            .apply()
    }

    fun add(context: Context, name: String, pageCount: Int, page: Int): Boolean {
        val current = load(context, name, pageCount)
        if (current.contains(page)) {
            return false
        }
        save(context, name, pageCount, (current + page).sorted())
        return true
    }

    fun remove(context: Context, name: String, pageCount: Int, page: Int) {
        val current = load(context, name, pageCount)
        save(context, name, pageCount, current.filter { it != page })
    }

    suspend fun toc(context: Context, file: File): List<TocEntry> =
        withContext(Dispatchers.IO) {
            if (!initialized) {
                PDFBoxResourceLoader.init(context.applicationContext)
                initialized = true
            }
            val result = ArrayList<TocEntry>()
            try {
                PDDocument.load(file).use { document ->
                    val outline = document.documentCatalog.documentOutline
                    if (outline != null) {
                        walk(document, outline, 0, result)
                    }
                }
            } catch (e: Exception) {
            }
            result
        }

    private fun walk(
        document: PDDocument,
        node: PDOutlineNode,
        level: Int,
        out: MutableList<TocEntry>
    ) {
        var item: PDOutlineItem? = node.firstChild
        var guard = 0
        while (item != null && guard < 2000) {
            guard++
            var page = -1
            try {
                val target = item.findDestinationPage(document)
                if (target != null) {
                    page = document.pages.indexOf(target)
                }
            } catch (e: Exception) {
            }
            val title = item.title ?: ""
            if (title.isNotBlank()) {
                out.add(TocEntry(title.trim(), page, level))
            }
            if (level < 6) {
                walk(document, item, level + 1, out)
            }
            item = item.nextSibling
        }
    }
}
