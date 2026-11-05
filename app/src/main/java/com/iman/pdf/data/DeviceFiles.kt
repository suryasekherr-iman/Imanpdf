package com.iman.pdf.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DevicePdf(
    val uri: Uri,
    val name: String,
    val size: Long,
    val modified: Long
)

/** Lists PDFs only inside a folder explicitly selected by the user through SAF. */
object DeviceFiles {
    private const val PREFS = "iman_storage"
    private const val TREE_URI = "pdf_tree_uri"

    fun hasFolderAccess(context: Context): Boolean =
        savedTreeUri(context) != null

    fun saveTreeUri(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(TREE_URI, uri.toString()).apply()
    }

    private fun savedTreeUri(context: Context): Uri? {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(TREE_URI, null) ?: return null
        val uri = Uri.parse(value)
        val stillGranted = context.contentResolver.persistedUriPermissions
            .any { it.uri == uri && it.isReadPermission }
        return if (stillGranted) uri else null
    }

    suspend fun scan(context: Context): List<DevicePdf> = withContext(Dispatchers.IO) {
        val tree = savedTreeUri(context)?.let { DocumentFile.fromTreeUri(context, it) }
            ?: return@withContext emptyList()
        val result = ArrayList<DevicePdf>()
        fun visit(folder: DocumentFile, depth: Int) {
            if (depth > 12) return
            folder.listFiles().forEach { item ->
                if (item.isDirectory) {
                    visit(item, depth + 1)
                } else if (item.isFile &&
                    (item.type == "application/pdf" ||
                        item.name?.endsWith(".pdf", ignoreCase = true) == true)
                ) {
                    result.add(
                        DevicePdf(
                            uri = item.uri,
                            name = item.name ?: "Document.pdf",
                            size = item.length(),
                            modified = item.lastModified()
                        )
                    )
                }
            }
        }
        try { visit(tree, 0) } catch (_: Exception) { }
        result.sortedByDescending { it.modified }
    }
}
