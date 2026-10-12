package com.iman.pdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ScanTools {

    fun decode(file: File, maxSide: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) {
                sample *= 2
            }
            val options = BitmapFactory.Options()
            options.inSampleSize = sample
            val raw = BitmapFactory.decodeFile(file.absolutePath, options)
            if (raw == null) {
                null
            } else {
                val orientation = ExifInterface(file.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                val degrees = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (degrees == 0f) {
                    raw
                } else {
                    val matrix = Matrix()
                    matrix.postRotate(degrees)
                    val rotated = Bitmap.createBitmap(
                        raw,
                        0,
                        0,
                        raw.width,
                        raw.height,
                        matrix,
                        true
                    )
                    if (rotated !== raw) {
                        raw.recycle()
                    }
                    rotated
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun enhance(source: Bitmap): Bitmap {
        val matrix = ColorMatrix()
        matrix.setSaturation(0f)
        val contrast = 1.4f
        val shift = (-0.5f * contrast + 0.5f) * 255f
        val boost = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, shift,
                0f, contrast, 0f, 0f, shift,
                0f, 0f, contrast, 0f, shift,
                0f, 0f, 0f, 1f, 0f
            )
        )
        matrix.postConcat(boost)
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        Canvas(result).drawBitmap(source, 0f, 0f, paint)
        return result
    }

    suspend fun buildPdf(context: Context, photos: List<File>, blackWhite: Boolean): File? =
        withContext(Dispatchers.IO) {
            val document = PdfDocument()
            try {
                var count = 0
                for (file in photos) {
                    val bitmap = decode(file, 1800)
                    if (bitmap == null) {
                        continue
                    }
                    val output = if (blackWhite) enhance(bitmap) else bitmap
                    count++
                    val pageWidth = 595
                    val pageHeight = (pageWidth.toFloat() * output.height / output.width).toInt()
                    val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, count).create()
                    val page = document.startPage(info)
                    page.canvas.drawBitmap(
                        output,
                        null,
                        Rect(0, 0, pageWidth, pageHeight),
                        null
                    )
                    document.finishPage(page)
                    if (output !== bitmap) {
                        output.recycle()
                    }
                    bitmap.recycle()
                }
                if (count == 0) {
                    null
                } else {
                    val out = File(context.cacheDir, "scan_" + System.nanoTime() + ".pdf")
                    out.outputStream().use { stream -> document.writeTo(stream) }
                    out
                }
            } catch (e: Exception) {
                null
            } finally {
                document.close()
            }
        }
}
