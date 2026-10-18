package com.iman.pdf.annot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AnnotBake {

    suspend fun bake(context: Context, source: File, marks: List<Mark>): File? =
        withContext(Dispatchers.IO) {
            if (marks.isEmpty()) {
                return@withContext null
            }
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                PDDocument.load(source).use { document ->
                    val pages = marks.map { it.page }.distinct()
                    for (index in pages) {
                        if (index < 0 || index >= document.numberOfPages) {
                            continue
                        }
                        val page = document.getPage(index)
                        val box = page.cropBox
                        val rotation = ((page.rotation % 360) + 360) % 360
                        val sideways = rotation == 90 || rotation == 270
                        val visibleWidth = if (sideways) box.height else box.width
                        val visibleHeight = if (sideways) box.width else box.height

                        val pixelsWide = 1600
                        val pixelsHigh = (pixelsWide * visibleHeight / visibleWidth)
                            .toInt()
                            .coerceAtLeast(1)
                        val overlay = Bitmap.createBitmap(
                            pixelsWide,
                            pixelsHigh,
                            Bitmap.Config.ARGB_8888
                        )
                        MarkPainter.draw(
                            Canvas(overlay),
                            pixelsWide.toFloat(),
                            pixelsHigh.toFloat(),
                            marks.filter { it.page == index }
                        )

                        val aligned = if (rotation == 0) {
                            overlay
                        } else {
                            val matrix = Matrix()
                            matrix.postRotate((360 - rotation).toFloat())
                            val turned = Bitmap.createBitmap(
                                overlay,
                                0,
                                0,
                                overlay.width,
                                overlay.height,
                                matrix,
                                true
                            )
                            overlay.recycle()
                            turned
                        }

                        val image = LosslessFactory.createFromImage(document, aligned)
                        PDPageContentStream(
                            document,
                            page,
                            PDPageContentStream.AppendMode.APPEND,
                            true,
                            true
                        ).use { content ->
                            content.drawImage(
                                image,
                                box.lowerLeftX,
                                box.lowerLeftY,
                                box.width,
                                box.height
                            )
                        }
                        aligned.recycle()
                    }
                    val out = File(
                        context.cacheDir,
                        "annotated_" + System.nanoTime() + ".pdf"
                    )
                    document.save(out)
                    out
                }
            } catch (e: Exception) {
                null
            }
        }
}
