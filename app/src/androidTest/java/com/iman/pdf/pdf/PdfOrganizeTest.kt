package com.iman.pdf.pdf

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.PDFPage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfOrganizeTest {

    @Test
    fun applyReordersDeletesInsertsBlankDuplicatesAndRotatesPages() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = createPdf(context, "Original", 3)
        val inserted = createPdf(context, "Inserted", 2)
        var output: File? = null
        var document: PDFDocument? = null
        try {
            output = PdfOrganize.apply(
                context = context,
                source = source,
                order = listOf(2, -2, -1, 0, 0),
                rotations = listOf(90, 0, 0, 0, 0),
                second = inserted
            )
            assertNotNull("Page organization should create an output PDF", output)
            document = Document.openDocument(requireNotNull(output).absolutePath) as? PDFDocument
            assertNotNull("Organized output must be a readable PDF", document)
            val pdf = requireNotNull(document)
            assertEquals(5, pdf.countPages())
            assertTrue(readPageText(pdf, 0).contains("Original page 3"))
            assertTrue(readPageText(pdf, 1).contains("Inserted page 1"))
            assertTrue(readPageText(pdf, 2).isBlank())
            assertTrue(readPageText(pdf, 3).contains("Original page 1"))
            assertTrue(readPageText(pdf, 4).contains("Original page 1"))

            val firstPage = pdf.loadPage(0) as PDFPage
            try {
                assertEquals(90, firstPage.getObject().get("Rotate").asInteger())
            } finally {
                firstPage.destroy()
            }
        } finally {
            document?.destroy()
            output?.delete()
            source.delete()
            inserted.delete()
        }
    }

    @Test
    fun applyRejectsEmptyOrderAndMismatchedRotationMetadata() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = createPdf(context, "Original", 1)
        try {
            assertNull(PdfOrganize.apply(context, source, emptyList(), emptyList()))
            assertNull(PdfOrganize.apply(context, source, listOf(0), emptyList()))
        } finally {
            source.delete()
        }
    }

    private fun readPageText(document: PDFDocument, index: Int): String {
        val page = document.loadPage(index)
        try {
            val text = page.toStructuredText()
            try {
                return buildString {
                    text.blocks.forEach { block ->
                        block.lines.forEach { line ->
                            line.chars.forEach { appendCodePoint(it.c) }
                            append('\n')
                        }
                    }
                }
            } finally {
                text.destroy()
            }
        } finally {
            page.destroy()
        }
    }

    private fun createPdf(context: Context, label: String, count: Int): File {
        val file = File(context.cacheDir, "organize_${label}_${System.nanoTime()}.pdf")
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f }
        try {
            for (number in 1..count) {
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(595, 842, number).create()
                )
                page.canvas.drawText("${label} page ${number}", 40f, 80f, paint)
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        return file
    }
}
