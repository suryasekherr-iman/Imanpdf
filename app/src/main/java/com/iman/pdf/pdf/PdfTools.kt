package com.iman.pdf.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
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

    suspend fun needsPassword(context: Context, source: Uri): Boolean =
        withContext(Dispatchers.IO) {
            var copy: File? = null
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                copy = UriActions.copyToCache(context, source)
                if (copy == null) {
                    false
                } else {
                    PDDocument.load(copy).use { false }
                }
            } catch (e: Exception) {
                e.javaClass.simpleName == "InvalidPasswordException"
            } finally {
                copy?.delete()
            }
        }

    suspend fun protect(context: Context, source: Uri, password: String): File? =
        withContext(Dispatchers.IO) {
            var copy: File? = null
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                copy = UriActions.copyToCache(context, source)
                if (copy == null) {
                    null
                } else {
                    PDDocument.load(copy).use { document ->
                        val permissions = AccessPermission()
                        val policy = StandardProtectionPolicy(password, password, permissions)
                        policy.encryptionKeyLength = 128
                        document.protect(policy)
                        val out = File(
                            context.cacheDir,
                            "protected_" + System.nanoTime() + ".pdf"
                        )
                        document.save(out)
                        out
                    }
                }
            } catch (e: Exception) {
                null
            } finally {
                copy?.delete()
            }
        }

    suspend fun unlock(context: Context, source: Uri, password: String): File? =
        withContext(Dispatchers.IO) {
            var copy: File? = null
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                copy = UriActions.copyToCache(context, source)
                if (copy == null) {
                    null
                } else {
                    PDDocument.load(copy, password).use { document ->
                        document.isAllSecurityToBeRemoved = true
                        val out = File(
                            context.cacheDir,
                            "unlocked_" + System.nanoTime() + ".pdf"
                        )
                        document.save(out)
                        out
                    }
                }
            } catch (e: Exception) {
                null
            } finally {
                copy?.delete()
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
