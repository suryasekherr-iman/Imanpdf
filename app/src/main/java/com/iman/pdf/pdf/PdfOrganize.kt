package com.iman.pdf.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfOrganize {

    suspend fun apply(
        context: Context,
        source: File,
        order: List<Int>,
        rotations: List<Int>,
        second: File? = null
    ): File? =
        withContext(Dispatchers.IO) {
            if (order.isEmpty() || order.size != rotations.size) {
                return@withContext null
            }
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                val other = if (second != null) PDDocument.load(second) else null
                PDDocument.load(source).use { input ->
                    val output = PDDocument()
                    try {
                        order.forEachIndexed { position, original ->
                            // original >= 0: page of source, -1: blank page, <= -2: page of second PDF
                            val imported = if (original == -1) {
                                val size = if (input.numberOfPages > 0) {
                                    input.getPage(0).mediaBox
                                } else {
                                    PDRectangle.A4
                                }
                                val blank = PDPage(PDRectangle(size.width, size.height))
                                output.addPage(blank)
                                blank
                            } else if (original <= -2) {
                                output.importPage(checkNotNull(other) { "Second PDF missing" }.getPage(-original - 2))
                            } else {
                                output.importPage(input.getPage(original))
                            }
                            val extra = rotations[position]
                            if (extra != 0) {
                                val total = (imported.rotation + extra) % 360
                                imported.rotation = (total + 360) % 360
                            }
                        }
                        val out = File(
                            context.cacheDir,
                            "organized_" + System.nanoTime() + ".pdf"
                        )
                        output.save(out)
                        out
                    } finally {
                        output.close()
                        other?.close()
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
}
