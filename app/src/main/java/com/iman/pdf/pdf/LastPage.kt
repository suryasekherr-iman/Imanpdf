package com.iman.pdf.pdf

import android.content.Context

/** Stores reading position and zoom separately for each source document URI. */
object LastPage {

    private const val PREFS = "iman_reading_state"

    private fun pageKey(documentId: String) =
        "page:" + documentId

    private fun zoomKey(documentId: String) = "zoom:" + documentId
    private fun offsetKey(documentId: String) = "offset:" + documentId
    private fun horizontalOffsetKey(documentId: String) = "horizontal_offset:" + documentId

    fun load(context: Context, documentId: String, pageCount: Int): Int {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(pageKey(documentId), 0)
        return saved.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    }

    fun save(context: Context, documentId: String, pageCount: Int, page: Int) {
        savePosition(context, documentId, pageCount, page, 0, 0)
    }

    fun loadOffset(context: Context, documentId: String): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(offsetKey(documentId), 0).coerceAtLeast(0)

    fun loadHorizontalOffset(context: Context, documentId: String): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(horizontalOffsetKey(documentId), 0).coerceAtLeast(0)

    fun savePosition(
        context: Context,
        documentId: String,
        pageCount: Int,
        page: Int,
        offset: Int,
        horizontalOffset: Int
    ) {
        // Reading position must be durable before the viewer is closed or the app is stopped.
        // commit() is safe here because the caller debounces scroll updates and also saves on dispose.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(pageKey(documentId), page.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
            .putInt(offsetKey(documentId), offset.coerceAtLeast(0))
            .putInt(horizontalOffsetKey(documentId), horizontalOffset.coerceAtLeast(0))
            .commit()
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
