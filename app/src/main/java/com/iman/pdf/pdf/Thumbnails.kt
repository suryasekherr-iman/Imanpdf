package com.iman.pdf.pdf

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Thumbnails {

    suspend fun create(context: Context, engine: PdfEngine, uri: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.filesDir, "thumbs")
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val safeName = uri.hashCode().toString().replace("-", "n")
                val file = File(dir, "t_$safeName.jpg")
                val bitmap = engine.renderPage(0, 360)
                file.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                bitmap.recycle()
                file.absolutePath
            } catch (e: Exception) {
                null
            }
        }
}
