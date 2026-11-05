package com.iman.pdf.pdf

import android.content.Context
import com.iman.pdf.core.pdf.MuPdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class SearchRect(val left: Float, val top: Float, val right: Float, val bottom: Float)
data class SearchHit(val page: Int, val snippet: String, val rect: SearchRect? = null)

object PdfText {
    suspend fun pageText(
        context: Context,
        file: File,
        pageIndex: Int,
        documentId: String = file.absolutePath
    ): String = withContext(Dispatchers.IO) {
        val engine = MuPdfEngine()
        try {
            if (!engine.openDocument(file.absolutePath)) return@withContext ""
            engine.pageText(pageIndex).ifBlank {
                OcrStore.loadPage(context, documentId, pageIndex)?.text.orEmpty()
            }
        } catch (_: Exception) {
            OcrStore.loadPage(context, documentId, pageIndex)?.text.orEmpty()
        } finally {
            engine.closeDocument()
        }
    }

    suspend fun search(
        context: Context,
        file: File,
        query: String,
        documentId: String = file.absolutePath
    ): List<SearchHit> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val engine = MuPdfEngine()
        val hits = ArrayList<SearchHit>()
        try {
            if (!engine.openDocument(file.absolutePath)) return@withContext emptyList()
            for (page in 0 until engine.pageCount) {
                val text = engine.pageText(page)
                val nativeHits = engine.searchPage(page, query)
                nativeHits.forEach { rect ->
                    val at = text.indexOf(query, ignoreCase = true)
                    val start = (at - 30).coerceAtLeast(0)
                    val end = if (at >= 0) (at + query.length + 30).coerceAtMost(text.length) else text.length.coerceAtMost(80)
                    hits.add(
                        SearchHit(
                            page,
                            if (at >= 0) text.substring(start, end).replace('\n', ' ').trim() else query,
                            SearchRect(rect[0], rect[1], rect[2], rect[3])
                        )
                    )
                    if (hits.size >= 500) return@withContext hits
                }
                if (nativeHits.isEmpty() && text.isBlank()) {
                    hits.addAll(OcrStore.searchHits(context, documentId, page, query).take(500 - hits.size))
                }
                if (hits.size >= 500) break
            }
        } catch (_: Exception) {
        } finally {
            engine.closeDocument()
        }
        hits
    }
}
