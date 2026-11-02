package com.iman.pdf.pdf

import android.content.Context
import com.iman.pdf.annot.HighlightMark
import com.iman.pdf.annot.Mark
import com.iman.pdf.annot.SignMark
import com.iman.pdf.annot.StrokeMark
import com.iman.pdf.annot.TextMark
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class CommentItem(
    val page: Int,
    val type: String,
    val text: String,
    val saved: Boolean
)

object Comments {

    private val shown = mapOf(
        "Text" to "Note",
        "FreeText" to "Text",
        "Highlight" to "Highlight",
        "Underline" to "Underline",
        "StrikeOut" to "Strikethrough",
        "Squiggly" to "Squiggly",
        "Ink" to "Drawing"
    )

    suspend fun load(context: Context, file: File): List<CommentItem> =
        withContext(Dispatchers.IO) {
            val result = ArrayList<CommentItem>()
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                PDDocument.load(file).use { document ->
                    for (index in 0 until document.numberOfPages) {
                        try {
                            val annotations = document.getPage(index).annotations
                            annotations.forEach { annotation ->
                                val label = shown[annotation.subtype]
                                if (label != null) {
                                    val text = (annotation.contents ?: "").trim()
                                    result.add(CommentItem(index, label, text, true))
                                }
                            }
                        } catch (e: Exception) {
                            // skip a page whose annotations cannot be read
                        }
                    }
                }
            } catch (e: Exception) {
                // unreadable file: return what we have
            }
            result
        }

    fun fromMarks(marks: List<Mark>): List<CommentItem> =
        marks.map { mark ->
            when (mark) {
                is TextMark -> CommentItem(mark.page, "Text", mark.text, false)
                is StrokeMark -> CommentItem(mark.page, "Drawing", "", false)
                is HighlightMark -> CommentItem(mark.page, "Highlight", "", false)
                is SignMark -> CommentItem(mark.page, "Signature", "", false)
            }
        }
}
