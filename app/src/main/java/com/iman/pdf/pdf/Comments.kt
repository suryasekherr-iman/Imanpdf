package com.iman.pdf.pdf

import android.content.Context
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.PDFAnnotation
import com.artifex.mupdf.fitz.PDFPage
import com.iman.pdf.annot.HighlightMark
import com.iman.pdf.annot.Mark
import com.iman.pdf.annot.SignMark
import com.iman.pdf.annot.StrokeMark
import com.iman.pdf.annot.TextMark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class CommentItem(val page: Int, val type: String, val text: String, val saved: Boolean)

object Comments {
    private val labels = mapOf(
        PDFAnnotation.TYPE_TEXT to "Note",
        PDFAnnotation.TYPE_FREE_TEXT to "Text",
        PDFAnnotation.TYPE_HIGHLIGHT to "Highlight",
        PDFAnnotation.TYPE_UNDERLINE to "Underline",
        PDFAnnotation.TYPE_STRIKE_OUT to "Strikethrough",
        PDFAnnotation.TYPE_SQUIGGLY to "Squiggly",
        PDFAnnotation.TYPE_INK to "Drawing"
    )

    suspend fun load(context: Context, file: File): List<CommentItem> = withContext(Dispatchers.IO) {
        val result = ArrayList<CommentItem>()
        var document: Document? = null
        try {
            document = Document.openDocument(file.absolutePath)
            for (index in 0 until document.countPages()) {
                val page = document.loadPage(index) as? PDFPage ?: continue
                try {
                    page.annotations.forEach { annotation ->
                        val label = labels[annotation.type] ?: return@forEach
                        result.add(CommentItem(index, label, annotation.contents.orEmpty().trim(), true))
                        annotation.destroy()
                    }
                } catch (_: Exception) {
                } finally { page.destroy() }
            }
        } catch (_: Exception) {
        } finally { try { document?.destroy() } catch (_: Exception) {} }
        result
    }

    fun fromMarks(marks: List<Mark>): List<CommentItem> = marks.map { mark ->
        when (mark) {
            is TextMark -> CommentItem(mark.page, "Text", mark.text, false)
            is StrokeMark -> CommentItem(mark.page, "Drawing", "", false)
            is HighlightMark -> CommentItem(
                mark.page,
                when (mark.kind) { 1 -> "Underline"; 2 -> "Strikethrough"; else -> "Highlight" },
                "",
                false
            )
            is SignMark -> CommentItem(mark.page, "Signature", "", false)
        }
    }
}
