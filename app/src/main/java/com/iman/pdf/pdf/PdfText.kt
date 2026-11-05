package com.iman.pdf.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class SearchRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class SearchHit(
    val page: Int,
    val snippet: String,
    val rect: SearchRect? = null
)

object PdfText {

    private var initialized = false

    private fun prepare(context: Context) {
        if (!initialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            initialized = true
        }
    }

    suspend fun pageText(context: Context, file: File, pageIndex: Int): String =
        withContext(Dispatchers.IO) {
            prepare(context)
            try {
                PDDocument.load(file).use { document ->
                    val stripper = PDFTextStripper()
                    stripper.startPage = pageIndex + 1
                    stripper.endPage = pageIndex + 1
                    stripper.getText(document) ?: ""
                }
            } catch (e: Exception) {
                ""
            }
        }

    suspend fun search(context: Context, file: File, query: String): List<SearchHit> =
        withContext(Dispatchers.IO) {
            prepare(context)
            val hits = ArrayList<SearchHit>()
            if (query.isBlank()) return@withContext hits

            try {
                PDDocument.load(file).use { document ->
                    for (pageIndex in 0 until document.numberOfPages) {
                        val page = document.getPage(pageIndex)
                        val pageWidth = page.cropBox.width.coerceAtLeast(1f)
                        val pageHeight = page.cropBox.height.coerceAtLeast(1f)
                        val stripper = object : PDFTextStripper() {
                            override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                                var from = 0
                                while (true) {
                                    val at = text.indexOf(query, from, ignoreCase = true)
                                    if (at < 0) break
                                    val endIndex = (at + query.length).coerceAtMost(textPositions.size)
                                    if (at < endIndex) {
                                        val matched = textPositions.subList(at, endIndex)
                                        val left = matched.minOf { it.xDirAdj }.coerceIn(0f, pageWidth)
                                        val right = matched.maxOf { it.xDirAdj + it.widthDirAdj }.coerceIn(left, pageWidth)
                                        val top = matched.minOf { it.yDirAdj - it.heightDir }.coerceIn(0f, pageHeight)
                                        val bottom = matched.maxOf { it.yDirAdj }.coerceIn(top, pageHeight)
                                        val start = (at - 30).coerceAtLeast(0)
                                        val end = (at + query.length + 30).coerceAtMost(text.length)
                                        hits.add(
                                            SearchHit(
                                                page = pageIndex,
                                                snippet = text.substring(start, end).replace("\n", " ").trim(),
                                                rect = SearchRect(
                                                    left / pageWidth,
                                                    top / pageHeight,
                                                    right / pageWidth,
                                                    bottom / pageHeight
                                                )
                                            )
                                        )
                                    }
                                    from = at + query.length
                                    if (hits.size >= 500) return
                                }
                            }
                        }
                        stripper.startPage = pageIndex + 1
                        stripper.endPage = pageIndex + 1
                        stripper.getText(document)
                        if (hits.size >= 500) break
                    }
                }
            } catch (_: Exception) {
            }
            hits
        }
}
