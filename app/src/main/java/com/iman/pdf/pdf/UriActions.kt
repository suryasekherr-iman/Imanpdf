package com.iman.pdf.pdf

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest

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
                com.iman.pdf.logSwallowed("UriActions:25", e)
                null
            }
        }

    /**
     * Copies [uri] into app storage. The same file imported again reuses the earlier copy,
     * so its reading position and saved edits are kept.
     */
    suspend fun importCopy(context: Context, uri: Uri, name: String): File? =
        withContext(Dispatchers.IO) {
            var temp: File? = null
            try {
                val dir = File(context.filesDir, "imported")
                dir.mkdirs()
                val partial = File(dir, "part_" + System.nanoTime())
                temp = partial
                val digest = MessageDigest.getInstance("SHA-256")
                val input = context.contentResolver.openInputStream(uri)
                    ?: return@withContext null
                DigestInputStream(input, digest).use { source ->
                    partial.outputStream().use { out ->
                        source.copyTo(out)
                    }
                }
                val key = digest.digest().take(8).joinToString("") { "%02x".format(it) }
                val cleaned = name.filterNot { it in "\\/:*?\"<>|" }.take(100)
                    .ifBlank { "Document" }
                val safe = if (cleaned.endsWith(".pdf", ignoreCase = true)) cleaned else "$cleaned.pdf"
                val folder = File(dir, key)
                folder.mkdirs()
                val target = folder.listFiles()?.firstOrNull { it.isFile } ?: File(folder, safe)
                if (target.isFile || partial.renameTo(target)) target else null
            } catch (e: Exception) {
                com.iman.pdf.logSwallowed("UriActions:46", e)
                null
            } finally {
                temp?.delete()
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
                com.iman.pdf.logSwallowed("UriActions:68", e)
                false
            }
        }
}
