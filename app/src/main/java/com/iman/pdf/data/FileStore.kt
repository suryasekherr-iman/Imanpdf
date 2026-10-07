package com.iman.pdf.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.filesStore by preferencesDataStore(name = "iman_files")

data class PdfEntry(
    val uri: String,
    val name: String,
    val lastOpened: Long,
    val starred: Boolean,
    val thumb: String?
)

class FileStore(private val context: Context) {

    private val entriesKey = stringPreferencesKey("entries")

    val entries: Flow<List<PdfEntry>> =
        context.filesStore.data.map { parse(it[entriesKey]) }

    suspend fun recordOpened(uri: String, name: String, thumb: String?) {
        context.filesStore.edit { prefs ->
            val list = parse(prefs[entriesKey]).toMutableList()
            val old = list.firstOrNull { it.uri == uri }
            list.removeAll { it.uri == uri }
            list.add(
                0,
                PdfEntry(
                    uri = uri,
                    name = name,
                    lastOpened = System.currentTimeMillis(),
                    starred = old?.starred ?: false,
                    thumb = thumb ?: old?.thumb
                )
            )
            prefs[entriesKey] = write(trim(list))
        }
    }

    suspend fun setStarred(uri: String, starred: Boolean) {
        context.filesStore.edit { prefs ->
            val list = parse(prefs[entriesKey]).map {
                if (it.uri == uri) it.copy(starred = starred) else it
            }
            prefs[entriesKey] = write(list)
        }
    }

    suspend fun remove(uri: String) {
        context.filesStore.edit { prefs ->
            val list = parse(prefs[entriesKey]).filter { it.uri != uri }
            prefs[entriesKey] = write(list)
        }
    }

    private fun trim(list: List<PdfEntry>): List<PdfEntry> {
        val result = ArrayList<PdfEntry>()
        var unstarred = 0
        for (entry in list) {
            if (entry.starred) {
                result.add(entry)
            } else if (unstarred < MAX_RECENT) {
                result.add(entry)
                unstarred++
            }
        }
        return result
    }

    private fun parse(text: String?): List<PdfEntry> {
        if (text.isNullOrEmpty()) return emptyList()
        return try {
            val array = JSONArray(text)
            val result = ArrayList<PdfEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val thumb = obj.optString("thumb", "")
                result.add(
                    PdfEntry(
                        uri = obj.getString("uri"),
                        name = obj.getString("name"),
                        lastOpened = obj.optLong("lastOpened", 0L),
                        starred = obj.optBoolean("starred", false),
                        thumb = if (thumb.isEmpty()) null else thumb
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun write(list: List<PdfEntry>): String {
        val array = JSONArray()
        list.forEach { entry ->
            val obj = JSONObject()
            obj.put("uri", entry.uri)
            obj.put("name", entry.name)
            obj.put("lastOpened", entry.lastOpened)
            obj.put("starred", entry.starred)
            obj.put("thumb", entry.thumb ?: "")
            array.put(obj)
        }
        return array.toString()
    }

    companion object {
        private const val MAX_RECENT = 50
    }
}
