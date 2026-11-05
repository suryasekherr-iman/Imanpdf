package com.iman.pdf.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.content.pm.PackageManager
import android.Manifest
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DevicePdf(
    val uri: Uri,
    val name: String,
    val size: Long,
    val modified: Long
)

object DeviceFiles {

    fun hasAllFilesAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    suspend fun scan(context: Context): List<DevicePdf> =
        withContext(Dispatchers.IO) {
            val filesByUri = LinkedHashMap<String, DevicePdf>()
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )
            val selection = "(" +
                MediaStore.Files.FileColumns.MIME_TYPE + " = ? OR " +
                MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE ?)"
            val args = arrayOf("application/pdf", "%.pdf")
            val order = MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC"

            // Query every shared-storage volume, not just the primary one.
            val volumes = MediaStore.getExternalVolumeNames(context)
            for (volume in volumes) {
                val collection = MediaStore.Files.getContentUri(volume)
                context.contentResolver.query(
                    collection,
                    projection,
                    selection,
                    args,
                    order
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn) ?: "Document.pdf"
                        val uri = ContentUris.withAppendedId(collection, id)
                        filesByUri[uri.toString()] = DevicePdf(
                            uri = uri,
                            name = name,
                            size = if (cursor.isNull(sizeColumn)) 0L else cursor.getLong(sizeColumn),
                            modified = if (cursor.isNull(dateColumn)) 0L else cursor.getLong(dateColumn) * 1000L
                        )
                    }
                }
            }

            filesByUri.values.sortedByDescending { it.modified }
        }
}
