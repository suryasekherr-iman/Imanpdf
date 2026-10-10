package com.iman.pdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfTools {

    suspend fun merge(context: Context, sources: List<Uri>): File? =
        withContext(Dispatchers.IO) {
            val copies = ArrayList<File>()
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                val merger = PDFMergerUtility()
                for (uri in sources) {
                    val copy = UriActions.copyToCache(context, uri)
                    if (copy == null) {
                        return@withContext null
                    }
                    copies.add(copy)
                    merger.addSource(copy)
                }
                val out = File(context.cacheDir, "merged_" + System.nanoTime() + ".pdf")
                merger.destinationFileName = out.absolutePath
                merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
                out
            } catch (e: Exception) {
                null
            } finally {
                copies.forEach { it.delete() }
            }
        }

    suspend fun imagesToPdf(context: Context, images: List<Uri>): File? =
        withContext(Dispatchers.IO) {
            val document = PdfDocument()
            try {
                var count = 0
                for (uri in images) {
                    val bitmap = decodeScaled(context, uri, 1600)
                    if (bitmap == null) {
                        continue
                    }
                    count++
                    val info = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, count)
                        .create()
                    val page = document.startPage(info)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    document.finishPage(page)
                    bitmap.recycle()
                }
                if (count == 0) {
                    null
                } else {
                    val out = File(context.cacheDir, "images_" + System.nanoTime() + ".pdf")
                    out.outputStream().use { stream -> document.writeTo(stream) }
                    out
                }
            } catch (e: Exception) {
                null
            } finally {
                document.close()
            }
        }

    private fun decodeScaled(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            var sample = 1
            while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) {
                sample *= 2
            }
            val options = BitmapFactory.Options()
            options.inSampleSize = sample
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: Exception) {
            null
        }
    }
}
