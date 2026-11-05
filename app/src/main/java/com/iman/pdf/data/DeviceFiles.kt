package com.iman.pdf.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DevicePdf(
    val uri: Uri,
    val name: String,
    val size: Long,
    val modified: Long
)

object DeviceFiles {

    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    suspend fun scan(context: Context): List<DevicePdf> =
        withContext(Dispatchers.IO) {
            val result = ArrayList<DevicePdf>()
            try {
                val collection = MediaStore.Files.getContentUri("external")
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.DATE_MODIFIED
                )
                val selection = MediaStore.Files.FileColumns.MIME_TYPE + " = ?"
                val args = arrayOf("application/pdf")
                val order = MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC"

                context.contentResolver.query(
                    collection,
                    projection,
                    selection,
                    args,
                    order
                )?.use { cursor ->
                    val idColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val sizeColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val dateColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn) ?: "Document.pdf"
                        result.add(
                            DevicePdf(
                                uri = ContentUris.withAppendedId(collection, id),
                                name = name,
                                size = cursor.getLong(sizeColumn),
                                modified = cursor.getLong(dateColumn) * 1000L
                            )
                        )
                    }
                }
            } catch (e: Exception) {
            }
            result
        }
}
