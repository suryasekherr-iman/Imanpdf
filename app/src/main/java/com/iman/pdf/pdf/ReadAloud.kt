package com.iman.pdf.pdf

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class ReadAloud(context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null

    @Volatile
    private var ready = false

    @Volatile
    private var lastId = ""

    private var counter = 0
    private var rate = 1.0f

    var onPageFinished: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                    }

                    override fun onDone(utteranceId: String?) {
                        finishedIfLast(utteranceId)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        finishedIfLast(utteranceId)
                    }
                })
            }
        }
    }

    private fun finishedIfLast(utteranceId: String?) {
        if (utteranceId != null && utteranceId == lastId) {
            mainHandler.post { onPageFinished?.invoke() }
        }
    }

    fun isReady(): Boolean = ready

    fun setLanguage(tag: String): Boolean {
        val engine = tts ?: return false
        if (!ready) {
            return false
        }
        val locale = if (tag == "system") {
            Locale.getDefault()
        } else {
            Locale.forLanguageTag(tag)
        }
        val result = engine.setLanguage(locale)
        return result != TextToSpeech.LANG_MISSING_DATA &&
            result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun setSpeed(value: Float) {
        rate = value
        tts?.setSpeechRate(value)
    }

    fun speak(text: String): Boolean {
        val engine = tts ?: return false
        if (!ready) {
            return false
        }
        val chunks = split(text)
        if (chunks.isEmpty()) {
            return false
        }
        lastId = ""
        engine.stop()
        engine.setSpeechRate(rate)
        chunks.forEachIndexed { index, chunk ->
            counter++
            val id = "iman_" + counter
            if (index == chunks.lastIndex) {
                lastId = id
            }
            val mode = if (index == 0) {
                TextToSpeech.QUEUE_FLUSH
            } else {
                TextToSpeech.QUEUE_ADD
            }
            engine.speak(chunk, mode, null, id)
        }
        return true
    }

    fun stop() {
        lastId = ""
        tts?.stop()
    }

    fun shutdown() {
        lastId = ""
        onPageFinished = null
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
    }

    private fun split(text: String): List<String> {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty()) {
            return emptyList()
        }
        val maxLength = 3000
        val result = ArrayList<String>()
        var start = 0
        while (start < clean.length) {
            var end = minOf(start + maxLength, clean.length)
            if (end < clean.length) {
                val cutDot = clean.lastIndexOf(". ", end)
                val cutDanda = clean.lastIndexOf("। ", end)
                val cut = maxOf(cutDot, cutDanda)
                if (cut > start) {
                    end = cut + 1
                }
            }
            val piece = clean.substring(start, end).trim()
            if (piece.isNotEmpty()) {
                result.add(piece)
            }
            start = end
        }
        return result
    }
}
