package com.iman.pdf.pdf

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FileOps {

    suspend fun rename(context: Context, uri: Uri, newName: String): Uri? =
        withContext(Dispatchers.IO) {
            try {
                if (DocumentsContract.isDocumentUri(context, uri)) {
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
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    DocumentsContract.deleteDocument(context.contentResolver, uri)
                } else {
                    context.contentResolver.delete(uri, null, null) > 0
                }
            } catch (e: Exception) {
                false
            }
        }
}
