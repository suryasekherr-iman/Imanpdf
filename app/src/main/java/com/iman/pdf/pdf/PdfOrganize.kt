package com.iman.pdf.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfOrganize {

    suspend fun apply(
        context: Context,
        source: File,
        order: List<Int>,
        rotations: List<Int>
    ): File? =
        withContext(Dispatchers.IO) {
            if (order.isEmpty() || order.size != rotations.size) {
                return@withContext null
            }
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                PDDocument.load(source).use { input ->
                    val output = PDDocument()
                    try {
                        order.forEachIndexed { position, original ->
                            val page = input.getPage(original)
                            val imported = output.importPage(page)
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
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
}
