package com.iman.pdf.pdf

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class TtsHighlight(val start: Int, val end: Int)

class ReadAloud(context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private val utteranceOffsets = ConcurrentHashMap<String, Int>()
    private val _currentSpokenRange = MutableStateFlow<TtsHighlight?>(null)
    val currentSpokenRange: StateFlow<TtsHighlight?> = _currentSpokenRange
    private val _currentSpokenText = MutableStateFlow("")
    val currentSpokenText: StateFlow<String> = _currentSpokenText

    @Volatile private var ready = false
    @Volatile private var lastId = ""
    private var counter = 0
    private var rate = 1.0f
    var onPageFinished: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                        val offset = utteranceId?.let { utteranceOffsets[it] } ?: return
                        val range = TtsHighlight(
                            start = (offset + start).coerceAtLeast(0),
                            end = (offset + end).coerceAtLeast(offset + start)
                        )
                        mainHandler.post { _currentSpokenRange.value = range }
                    }

                    override fun onDone(utteranceId: String?) {
                        utteranceId?.let { utteranceOffsets.remove(it) }
                        clearSpokenRange()
                        finishedIfLast(utteranceId)
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        utteranceId?.let { utteranceOffsets.remove(it) }
                        clearSpokenRange()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        handleError(utteranceId)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        handleError(utteranceId)
                    }

                    private fun handleError(utteranceId: String?) {
                        utteranceId?.let { utteranceOffsets.remove(it) }
                        clearSpokenRange()
                        finishedIfLast(utteranceId)
                    }
                })
            }
        }
    }

    private fun clearSpokenRange() {
        mainHandler.post { _currentSpokenRange.value = null }
    }

    private fun finishedIfLast(utteranceId: String?) {
        if (utteranceId != null && utteranceId == lastId) {
            mainHandler.post { onPageFinished?.invoke() }
        }
    }

    fun isReady(): Boolean = ready

    fun setLanguage(tag: String): Boolean {
        val engine = tts ?: return false
        if (!ready) return false
        val locale = if (tag == "system") Locale.getDefault() else Locale.forLanguageTag(tag)
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
        if (!ready) return false
        val clean = text.replace(Regex("\\s+"), " ").trim()
        val chunks = splitWithOffsets(clean)
        if (chunks.isEmpty()) return false

        lastId = ""
        utteranceOffsets.clear()
        _currentSpokenText.value = clean
        _currentSpokenRange.value = null
        engine.stop()
        engine.setSpeechRate(rate)

        chunks.forEachIndexed { index, (chunk, offset) ->
            counter++
            val id = "iman_" + counter
            utteranceOffsets[id] = offset
            if (index == chunks.lastIndex) lastId = id
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(chunk, mode, null, id)
        }
        return true
    }

    fun stop() {
        lastId = ""
        utteranceOffsets.clear()
        _currentSpokenRange.value = null
        tts?.stop()
    }

    fun shutdown() {
        lastId = ""
        utteranceOffsets.clear()
        _currentSpokenRange.value = null
        onPageFinished = null
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
    }

    private fun splitWithOffsets(text: String): List<Pair<String, Int>> {
        if (text.isEmpty()) return emptyList()
        val maxLength = 3000
        val result = ArrayList<Pair<String, Int>>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + maxLength, text.length)
            if (end < text.length) {
                val cutDot = text.lastIndexOf(". ", end)
                val cutDanda = text.lastIndexOf("। ", end)
                val cut = maxOf(cutDot, cutDanda)
                if (cut > start) end = cut + 1
            }
            val raw = text.substring(start, end)
            val leading = raw.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            val piece = raw.trim()
            if (piece.isNotEmpty()) result.add(piece to (start + leading))
            start = end
        }
        return result
    }
}
