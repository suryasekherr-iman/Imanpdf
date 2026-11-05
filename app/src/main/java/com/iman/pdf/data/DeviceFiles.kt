package com.iman.pdf.data

import android.content.Context
import android.content.Intent
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
        // This feature only reads documents. Requesting write access as well can cause
        // providers that grant read-only access to reject the whole persist operation.
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
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
        val treeUri = savedTreeUri(context) ?: return@withContext emptyList()
        val tree = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext emptyList()
        val result = ArrayList<DevicePdf>()
        val visited = HashSet<String>()

        fun visit(folder: DocumentFile, depth: Int) {
            if (depth > 12 || !visited.add(folder.uri.toString())) return
            // Providers can fail for a single directory. Keep scanning siblings instead
            // of losing every result from the selected tree.
            val children = try {
                folder.listFiles()
            } catch (_: Exception) {
                return
            }
            children.forEach { item ->
                try {
                    when {
                        item.isDirectory -> visit(item, depth + 1)
                        item.isFile && (item.type == "application/pdf" ||
                            item.name?.endsWith(".pdf", ignoreCase = true) == true) -> {
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
                } catch (_: Exception) {
                    // A document may disappear or become unavailable while the tree is scanned.
                }
            }
        }

        visit(tree, 0)
        result.sortedByDescending { it.modified }
    }
}
