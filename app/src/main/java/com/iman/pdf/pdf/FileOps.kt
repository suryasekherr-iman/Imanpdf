package com.iman.pdf.pdf

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FileOps {

    suspend fun rename(context: Context, uri: Uri, newName: String): Uri? =
        withContext(Dispatchers.IO) {
            try {
                if (uri.scheme == "file") {
                    val old = File(uri.path ?: return@withContext null)
                    val raw = old.name
                    val cut = raw.indexOf('_')
                    val prefix = if (cut > 0 && raw.substring(0, cut).all { it.isDigit() }) {
                        raw.substring(0, cut + 1)
                    } else {
                        ""
                    }
                    val target = File(old.parentFile, prefix + newName)
                    if (old.renameTo(target)) Uri.fromFile(target) else null
                } else if (DocumentsContract.isDocumentUri(context, uri)) {
                    DocumentsContract.renameDocument(context.contentResolver, uri, newName)
                } else {
                    val values = ContentValues()
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                    val changed = context.contentResolver.update(uri, values, null, null)
                    if (changed > 0) uri else null
                }
            } catch (e: Exception) {
                null
            }
        }

    suspend fun delete(context: Context, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
                if (uri.scheme == "file") {
                    File(uri.path ?: "").delete()
                } else if (DocumentsContract.isDocumentUri(context, uri)) {
                    DocumentsContract.deleteDocument(context.contentResolver, uri)
                } else {
                    context.contentResolver.delete(uri, null, null) > 0
                }
            } catch (e: Exception) {
                false
            }
        }
}
