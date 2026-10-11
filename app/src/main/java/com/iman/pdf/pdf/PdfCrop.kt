package com.iman.pdf.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfCrop {

    suspend fun apply(
        context: Context,
        source: File,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): File? =
        withContext(Dispatchers.IO) {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                PDDocument.load(source).use { document ->
                    for (index in 0 until document.numberOfPages) {
                        val page = document.getPage(index)
                        val box = page.cropBox
                        val rotation = ((page.rotation % 360) + 360) % 360

                        // order: left, top, right, bottom of the unrotated page
                        val m = when (rotation) {
                            90 -> floatArrayOf(top, right, bottom, left)
                            180 -> floatArrayOf(right, bottom, left, top)
                            270 -> floatArrayOf(bottom, left, top, right)
                            else -> floatArrayOf(left, top, right, bottom)
                        }

                        val width = box.width
                        val height = box.height
                        val x0 = box.lowerLeftX + width * m[0]
                        val y0 = box.lowerLeftY + height * m[3]
                        val x1 = box.upperRightX - width * m[2]
                        val y1 = box.upperRightY - height * m[1]
                        page.cropBox = PDRectangle(x0, y0, x1 - x0, y1 - y0)
                    }
                    val out = File(
                        context.cacheDir,
                        "cropped_" + System.nanoTime() + ".pdf"
                    )
                    document.save(out)
                    out
                }
            } catch (e: Exception) {
                null
            }
        }
}
