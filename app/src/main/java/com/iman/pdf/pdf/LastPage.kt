package com.iman.pdf.pdf

import android.content.Context

/** Remembers the last page seen in each PDF (keyed like Bookmarks: title + page count). */
object LastPage {

    private const val PREFS = "iman_last_page"

    private fun key(name: String, pageCount: Int): String = name + "|" + pageCount

    fun load(context: Context, name: String, pageCount: Int): Int {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(key(name, pageCount), 0)
        return saved.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    }

    fun save(context: Context, name: String, pageCount: Int, page: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(key(name, pageCount), page)
            .apply()
    }
}
