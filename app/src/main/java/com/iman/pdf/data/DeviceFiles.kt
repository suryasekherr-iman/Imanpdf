package com.iman.pdf.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DevicePdf(val uri: Uri, val name: String, val size: Long, val modified: Long)

/** MediaStore + user-selected SAF folders; intentionally no all-files permission. */
object DeviceFiles {
    private const val PREFS_NAME = "iman_saf_storage"
    private const val KEY_TREE_URIS = "granted_tree_uris"

    fun addFolder(context: Context, treeUri: Uri) {
        val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(treeUri, readWrite)
        } catch (_: SecurityException) {
            context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uris = prefs.getStringSet(KEY_TREE_URIS, emptySet()).orEmpty().toMutableSet()
        uris.add(treeUri.toString())
        prefs.edit().putStringSet(KEY_TREE_URIS, uris).apply()
    }

    private fun grantedFolders(context: Context): List<Uri> {
        val persisted = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }.map { it.uri.toString() }.toSet()
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_TREE_URIS, emptySet()).orEmpty()
            .filter { it in persisted }.map(Uri::parse)
    }

    suspend fun scan(context: Context): List<DevicePdf> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, DevicePdf>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        val selection = "(" + MediaStore.Files.FileColumns.MIME_TYPE + " = ? OR " +
            MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE ?)"
        val args = arrayOf("application/pdf", "%.pdf")
        val order = MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC"

        for (volume in MediaStore.getExternalVolumeNames(context)) {
            val collection = MediaStore.Files.getContentUri(volume)
            try {
                context.contentResolver.query(collection, projection, selection, args, order)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    while (cursor.moveToNext()) {
                        val uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol))
                        found[uri.toString()] = DevicePdf(
                            uri, cursor.getString(nameCol) ?: "Document.pdf",
                            if (cursor.isNull(sizeCol)) 0L else cursor.getLong(sizeCol),
                            if (cursor.isNull(dateCol)) 0L else cursor.getLong(dateCol) * 1000L
                        )
                    }
                }
            } catch (_: SecurityException) {
                // Other documents are available through explicit SAF folder grants.
            }
        }

        grantedFolders(context).forEach { uri ->
            DocumentFile.fromTreeUri(context, uri)?.let { collectTree(it, found, 0) }
        }
        found.values.sortedByDescending { it.modified }
    }

    private fun collectTree(directory: DocumentFile, output: MutableMap<String, DevicePdf>, depth: Int) {
        if (depth > 32 || !directory.isDirectory) return
        val children = try { directory.listFiles() } catch (_: SecurityException) { return }
        children.forEach { child ->
            if (child.isDirectory) collectTree(child, output, depth + 1)
            else if (child.isFile && (
                child.type.equals("application/pdf", true) || child.name?.endsWith(".pdf", true) == true
            )) {
                output[child.uri.toString()] = DevicePdf(
                    child.uri, child.name ?: "Document.pdf", child.length(), child.lastModified()
                )
            }
        }
    }
}
