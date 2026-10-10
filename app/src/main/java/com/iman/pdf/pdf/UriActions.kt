package com.iman.pdf.pdf

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object UriActions {

    suspend fun copyToCache(context: Context, uri: Uri): File? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.cacheDir, "temp")
                dir.mkdirs()
                val target = File(dir, "tmp_" + System.nanoTime() + ".pdf")
                val input = context.contentResolver.openInputStream(uri)
                    ?: return@withContext null
                input.use { source ->
                    target.outputStream().use { out ->
                        source.copyTo(out)
                    }
                }
                target
            } catch (e: Exception) {
                null
            }
        }

    suspend fun saveUriCopy(context: Context, source: Uri, destination: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val input = context.contentResolver.openInputStream(source)
                    ?: return@withContext false
                val output = context.contentResolver.openOutputStream(destination)
                if (output == null) {
                    input.close()
                    return@withContext false
                }
                input.use { i ->
                    output.use { o ->
                        i.copyTo(o)
                    }
                }
                true
            } catch (e: Exception) {
                false
            }
        }
}
