package com.iman.pdf.pdf

import android.content.Context

/** Stores reading position and zoom separately for each source document URI. */
object LastPage {

    private const val PREFS = "iman_reading_state"

    private fun pageKey(documentId: String) =
        "page:" + documentId

    private fun zoomKey(documentId: String) = "zoom:" + documentId

    fun load(context: Context, documentId: String, pageCount: Int): Int {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(pageKey(documentId), 0)
        return saved.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    }

    fun save(context: Context, documentId: String, pageCount: Int, page: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(pageKey(documentId), page.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
            .apply()
    }

    fun loadZoom(context: Context, documentId: String): Float {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(zoomKey(documentId), 1f)
        return if (saved.isFinite()) saved.coerceIn(1f, 3f) else 1f
    }

    fun saveZoom(context: Context, documentId: String, scale: Float) {
        val safeScale = if (scale.isFinite()) scale.coerceIn(1f, 3f) else 1f
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(zoomKey(documentId), safeScale)
            .apply()
    }
}
