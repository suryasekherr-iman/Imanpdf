package com.iman.pdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class PdfEngine private constructor(
    val file: File,
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) {
    private val mutex = Mutex()

    val pageCount: Int = renderer.pageCount

    suspend fun pageAspectRatio(index: Int): Float = withContext(Dispatchers.IO) {
        mutex.withLock {
            renderer.openPage(index).use { page ->
                page.height.toFloat() / page.width.toFloat()
            }
        }
    }

    suspend fun renderPage(index: Int, targetWidth: Int): Bitmap =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                renderer.openPage(index).use { page ->
                    val width = targetWidth.coerceAtLeast(1)
                    val ratio = page.height.toFloat() / page.width.toFloat()
                    val height = (width * ratio).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }

    fun close() {
        try {
            renderer.close()
        } catch (_: Exception) {
        }
        try {
            descriptor.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        suspend fun open(context: Context, uri: Uri): PdfEngine? =
            withContext(Dispatchers.IO) {
                var pfd: ParcelFileDescriptor? = null
                try {
                    val cacheFile = File(context.cacheDir, "open_" + System.nanoTime() + ".pdf")
                    val input = context.contentResolver.openInputStream(uri)
                        ?: return@withContext null
                    input.use { source ->
                        cacheFile.outputStream().use { target ->
                            source.copyTo(target)
                        }
                    }
                    pfd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    PdfEngine(cacheFile, pfd, PdfRenderer(pfd))
                } catch (e: Exception) {
                    try {
                        pfd?.close()
                    } catch (_: Exception) {
                    }
                    null
                }
            }
    }
}
