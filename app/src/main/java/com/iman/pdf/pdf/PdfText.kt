package com.iman.pdf.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class SearchHit(
    val page: Int,
    val snippet: String
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
            if (query.isBlank()) {
                return@withContext hits
            }
            try {
                PDDocument.load(file).use { document ->
                    val stripper = PDFTextStripper()
                    val total = document.numberOfPages
                    for (page in 1..total) {
                        stripper.startPage = page
                        stripper.endPage = page
                        val text = stripper.getText(document) ?: ""
                        var from = 0
                        while (true) {
                            val at = text.indexOf(query, from, ignoreCase = true)
                            if (at < 0) {
                                break
                            }
                            val start = (at - 30).coerceAtLeast(0)
                            val end = (at + query.length + 30).coerceAtMost(text.length)
                            hits.add(
                                SearchHit(
                                    page = page - 1,
                                    snippet = text.substring(start, end)
                                        .replace("\n", " ")
                                        .trim()
                                )
                            )
                            from = at + query.length
                            if (hits.size >= 500) {
                                return@use
                            }
                        }
                    }
                }
            } catch (e: Exception) {
            }
            hits
        }
}
