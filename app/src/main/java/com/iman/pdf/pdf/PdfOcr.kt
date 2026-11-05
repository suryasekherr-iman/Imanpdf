package com.iman.pdf.pdf

import android.content.Context
import android.graphics.Bitmap
import com.iman.pdf.core.ocr.OcrEngine
import com.iman.pdf.core.pdf.MuPdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Compatibility entry point for OCR callers that only have a PDF file and page index. */
object PdfOcr {
    @Suppress("UNUSED_PARAMETER")
    suspend fun recognize(context: Context, file: File, pageIndex: Int): OcrPage? =
        withContext(Dispatchers.IO) {
            val engine = MuPdfEngine()
            var bitmap: Bitmap? = null
            try {
                if (!engine.openDocument(file.absolutePath)) return@withContext null
                val rendered = engine.renderPageToBitmap(pageIndex, 2f)
                    ?: return@withContext null
                bitmap = rendered
                val recognized = OcrEngine.extractLinesFromBitmap(rendered)
                val lines = recognized.map { line ->
                    OcrLine(
                        text = line.text,
                        left = line.left,
                        top = line.top,
                        right = line.right,
                        bottom = line.bottom
                    )
                }
                OcrPage(pageIndex, lines).takeIf { it.lines.isNotEmpty() }
            } catch (_: Exception) {
                null
            } finally {
                bitmap?.takeIf { !it.isRecycled }?.recycle()
                engine.closeDocument()
            }
        }
}
