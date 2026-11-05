package com.iman.pdf.pdf

import android.content.Context
import com.iman.pdf.annot.HighlightMark
import com.iman.pdf.annot.Mark
import com.iman.pdf.annot.SignMark
import com.iman.pdf.annot.StrokeMark
import com.iman.pdf.annot.TextMark
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import android.graphics.RectF
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import com.tom_roush.pdfbox.text.PDFTextStripperByArea
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
                                    val text = if (annotation is PDAnnotationTextMarkup) {
                                        extractMarkedText(document, index, annotation)
                                    } else {
                                        (annotation.contents ?: "").trim()
                                    }
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

    private fun extractMarkedText(
        document: PDDocument,
        pageIndex: Int,
        annotation: PDAnnotationTextMarkup
    ): String {
        val points = annotation.quadPoints ?: return (annotation.contents ?: "").trim()
        if (points.size < 8) return (annotation.contents ?: "").trim()
        return try {
            val stripper = PDFTextStripperByArea()
            val regionNames = ArrayList<String>()
            for (offset in points.indices step 8) {
                if (offset + 7 >= points.size) break
                val xs = listOf(points[offset], points[offset + 2], points[offset + 4], points[offset + 6])
                val ys = listOf(points[offset + 1], points[offset + 3], points[offset + 5], points[offset + 7])
                val left = xs.minOrNull() ?: continue
                val right = xs.maxOrNull() ?: continue
                val bottom = ys.minOrNull() ?: continue
                val top = ys.maxOrNull() ?: continue
                if (right <= left || top <= bottom) continue
                val name = "highlight_${regionNames.size}"
                val pageHeight = document.getPage(pageIndex).mediaBox.height
                stripper.addRegion(name, RectF(left, pageHeight - top, right, pageHeight - bottom))
                regionNames.add(name)
            }
            if (regionNames.isEmpty()) return (annotation.contents ?: "").trim()
            stripper.extractRegions(document.getPage(pageIndex))
            regionNames.map { stripper.getTextForRegion(it).trim() }
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { (annotation.contents ?: "").trim() }
        } catch (_: Exception) {
            (annotation.contents ?: "").trim()
        }
    }

    fun fromMarks(marks: List<Mark>): List<CommentItem> =
        marks.map { mark ->
            when (mark) {
                is TextMark -> CommentItem(mark.page, "Text", mark.text, false)
                is StrokeMark -> CommentItem(mark.page, "Drawing", "", false)
                is HighlightMark -> CommentItem(
                    mark.page,
                    when (mark.kind) {
                        1 -> "Underline"
                        2 -> "Strikethrough"
                        else -> "Highlight"
                    },
                    "",
                    false
                )
                is SignMark -> CommentItem(mark.page, "Signature", "", false)
            }
        }
}
