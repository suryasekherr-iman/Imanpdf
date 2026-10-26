package com.iman.pdf.annot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object SignatureStore {

    private const val PREFS = "iman_signature"
    private const val KEY = "data"

    fun save(context: Context, data: SignatureData) {
        val strokes = JSONArray()
        data.strokes.forEach { stroke ->
            val points = JSONArray()
            stroke.forEach { point ->
                val pair = JSONArray()
                pair.put(point.first.toDouble())
                pair.put(point.second.toDouble())
                points.put(pair)
            }
            strokes.put(points)
        }
        val root = JSONObject()
        root.put("aspect", data.aspect.toDouble())
        root.put("strokes", strokes)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, root.toString())
            .apply()
    }

    fun load(context: Context): SignatureData? {
        return try {
            val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, null)
            if (text == null) {
                null
            } else {
                val root = JSONObject(text)
                val aspect = root.getDouble("aspect").toFloat()
                val strokesJson = root.getJSONArray("strokes")
                val strokes = ArrayList<List<Pair<Float, Float>>>()
                for (i in 0 until strokesJson.length()) {
                    val pointsJson = strokesJson.getJSONArray(i)
                    val points = ArrayList<Pair<Float, Float>>()
                    for (j in 0 until pointsJson.length()) {
                        val pair = pointsJson.getJSONArray(j)
                        points.add(
                            Pair(
                                pair.getDouble(0).toFloat(),
                                pair.getDouble(1).toFloat()
                            )
                        )
                    }
                    strokes.add(points)
                }
                if (strokes.isEmpty()) {
                    null
                } else {
                    SignatureData(strokes, aspect)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
