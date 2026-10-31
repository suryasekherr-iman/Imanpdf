package com.iman.pdf.data

import android.content.Context
import org.json.JSONArray

object SearchHistory {

    private const val PREFS = "iman_search"
    private const val KEY = "items"
    private const val MAX = 10

    fun load(context: Context): List<String> {
        return try {
            val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, null) ?: return emptyList()
            val array = JSONArray(text)
            val result = ArrayList<String>()
            for (i in 0 until array.length()) {
                result.add(array.getString(i))
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, list: List<String>) {
        val array = JSONArray()
        list.forEach { array.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }

    fun add(context: Context, query: String): List<String> {
        val text = query.trim()
        if (text.isEmpty()) {
            return load(context)
        }
        val list = load(context)
            .filterNot { it.equals(text, ignoreCase = true) }
            .toMutableList()
        list.add(0, text)
        val trimmed = list.take(MAX)
        save(context, trimmed)
        return trimmed
    }

    fun remove(context: Context, query: String): List<String> {
        val list = load(context).filterNot { it == query }
        save(context, list)
        return list
    }

    fun clear(context: Context) {
        save(context, emptyList())
    }
}
