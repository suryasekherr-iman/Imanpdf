package com.iman.pdf.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class StudyFlashcard(val id: String, val question: String, val answer: String)
data class StudyNote(val id: String, val title: String, val body: String, val page: Int?)
data class RevisionItem(val id: String, val text: String, val done: Boolean)
data class ImportantQuestion(val id: String, val text: String)

class StudentStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("student_spaces", Context.MODE_PRIVATE)

    fun flashcards(): List<StudyFlashcard> = readArray("flashcards") {
        StudyFlashcard(it.getString("id"), it.getString("question"), it.getString("answer"))
    }
    fun notes(): List<StudyNote> = readArray("notes") {
        StudyNote(it.getString("id"), it.getString("title"), it.getString("body"),
            if (it.isNull("page")) null else it.optInt("page"))
    }
    fun revisionItems(): List<RevisionItem> = readArray("revision") {
        RevisionItem(it.getString("id"), it.getString("text"), it.optBoolean("done"))
    }
    fun addFlashcard(question: String, answer: String) {
        val values = flashcards() + StudyFlashcard(UUID.randomUUID().toString(), question.trim(), answer.trim())
        writeArray("flashcards", values.map { JSONObject().put("id", it.id).put("question", it.question).put("answer", it.answer) })
    }
    fun removeFlashcard(id: String) = writeArray("flashcards", flashcards().filterNot { it.id == id }.map {
        JSONObject().put("id", it.id).put("question", it.question).put("answer", it.answer)
    })
    fun addNote(title: String, body: String, page: Int?) {
        val values = notes() + StudyNote(UUID.randomUUID().toString(), title.trim(), body.trim(), page)
        writeArray("notes", values.map { JSONObject().put("id", it.id).put("title", it.title).put("body", it.body).put("page", it.page ?: JSONObject.NULL) })
    }
    fun removeNote(id: String) = writeArray("notes", notes().filterNot { it.id == id }.map {
        JSONObject().put("id", it.id).put("title", it.title).put("body", it.body).put("page", it.page ?: JSONObject.NULL)
    })
    fun addRevisionItem(text: String) {
        val values = revisionItems() + RevisionItem(UUID.randomUUID().toString(), text.trim(), false)
        writeArray("revision", values.map { JSONObject().put("id", it.id).put("text", it.text).put("done", it.done) })
    }
    fun setRevisionDone(id: String, done: Boolean) = writeArray("revision", revisionItems().map {
        JSONObject().put("id", it.id).put("text", it.text).put("done", if (it.id == id) done else it.done)
    })
    fun removeRevisionItem(id: String) = writeArray("revision", revisionItems().filterNot { it.id == id }.map {
        JSONObject().put("id", it.id).put("text", it.text).put("done", it.done)
    })

    private inline fun <T> readArray(key: String, decode: (JSONObject) -> T): List<T> = try {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { runCatching { decode(it) }.getOrNull() }
        }
    } catch (_: Exception) { emptyList() }

    private fun writeArray(key: String, values: List<JSONObject>) {
        val array = JSONArray()
        values.forEach { array.put(it) }
        prefs.edit().putString(key, array.toString()).apply()
    }
}
