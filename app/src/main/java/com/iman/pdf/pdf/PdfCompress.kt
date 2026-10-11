package com.iman.pdf.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfCompress {

    suspend fun apply(
        context: Context,
        source: File,
        dpi: Int,
        quality: Int,
        onProgress: (Int, Int) -> Unit
    ): File? =
        withContext(Dispatchers.IO) {
            var engine: PdfEngine? = null
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                val opened = PdfEngine.open(context, Uri.fromFile(source))
                if (opened == null) {
                    return@withContext null
                }
                engine = opened
                PDDocument.load(source).use { original ->
                    val output = PDDocument()
                    try {
                        val total = original.numberOfPages
                        for (index in 0 until total) {
                            val page = original.getPage(index)
                            val box = page.cropBox
                            val rotation = ((page.rotation % 360) + 360) % 360
                            val sideways = rotation == 90 || rotation == 270
                            val widthPts = if (sideways) box.height else box.width
                            val heightPts = if (sideways) box.width else box.height
                            val pixels = ((widthPts / 72f) * dpi).toInt().coerceIn(300, 2400)

                            val bitmap = opened.renderPage(index, pixels)
                            val newPage = PDPage(PDRectangle(widthPts, heightPts))
                            output.addPage(newPage)
                            val image = JPEGFactory.createFromImage(
                                output,
                                bitmap,
                                quality / 100f
                            )
                            PDPageContentStream(output, newPage).use { content ->
                                content.drawImage(image, 0f, 0f, widthPts, heightPts)
                            }
                            bitmap.recycle()
                            onProgress(index + 1, total)
                        }
                        val out = File(
                            context.cacheDir,
                            "compressed_" + System.nanoTime() + ".pdf"
                        )
                        output.save(out)
                        out
                    } finally {
                        output.close()
                    }
                }
            } catch (e: Exception) {
                null
            } finally {
                engine?.close()
            }
        }
}
