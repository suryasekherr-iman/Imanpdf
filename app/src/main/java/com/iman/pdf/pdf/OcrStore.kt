package com.iman.pdf.pdf

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class OcrLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class OcrPage(
    val page: Int,
    val lines: List<OcrLine>
) {
    val text: String
        get() = lines.joinToString("\n") { it.text }
}

object OcrStore {
    private fun documentFile(context: Context, documentId: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(documentId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val directory = File(context.filesDir, "ocr")
        if (!directory.exists()) directory.mkdirs()
        return File(directory, "$digest.json")
    }

    fun loadPage(context: Context, documentId: String, page: Int): OcrPage? {
        return try {
            val root = JSONObject(documentFile(context, documentId).readText())
            val data = root.optJSONObject(page.toString()) ?: return null
            val array = data.optJSONArray("lines") ?: return null
            val lines = ArrayList<OcrLine>(array.length())
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val text = item.optString("text").trim()
                if (text.isNotEmpty()) {
                    lines.add(
                        OcrLine(
                            text,
                            item.optDouble("left").toFloat(),
                            item.optDouble("top").toFloat(),
                            item.optDouble("right").toFloat(),
                            item.optDouble("bottom").toFloat()
                        )
                    )
                }
            }
            OcrPage(page, lines).takeIf { lines.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    fun savePage(context: Context, documentId: String, page: OcrPage) {
        try {
            val file = documentFile(context, documentId)
            val root = if (file.exists()) JSONObject(file.readText()) else JSONObject()
            val lines = JSONArray()
            page.lines.forEach { line ->
                lines.put(
                    JSONObject()
                        .put("text", line.text)
                        .put("left", line.left)
                        .put("top", line.top)
                        .put("right", line.right)
                        .put("bottom", line.bottom)
                )
            }
            root.put(page.page.toString(), JSONObject().put("lines", lines))
            val temporary = File(file.parentFile, file.name + ".tmp")
            temporary.writeText(root.toString())
            if (!temporary.renameTo(file)) {
                file.writeText(root.toString())
                temporary.delete()
            }
        } catch (_: Exception) {
        }
    }

    fun searchHits(
        context: Context,
        documentId: String,
        page: Int,
        query: String
    ): List<SearchHit> {
        val result = ArrayList<SearchHit>()
        if (query.isBlank()) return result
        val ocrPage = loadPage(context, documentId, page) ?: return result
        ocrPage.lines.forEach { line ->
            var from = 0
            while (from < line.text.length) {
                val at = line.text.indexOf(query, from, ignoreCase = true)
                if (at < 0) break
                val start = (at - 30).coerceAtLeast(0)
                val end = (at + query.length + 30).coerceAtMost(line.text.length)
                result.add(
                    SearchHit(
                        page = page,
                        snippet = line.text.substring(start, end),
                        rect = SearchRect(line.left, line.top, line.right, line.bottom)
                    )
                )
                from = at + query.length
                if (result.size >= 500) return result
            }
        }
        return result
    }
}
