package com.iman.pdf.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.iman.pdf.pdf.PdfEngine
import com.iman.pdf.pdf.PdfText
import com.iman.pdf.pdf.ReadAloud
import com.iman.pdf.pdf.ReadAloudService
import com.iman.pdf.pdf.TtsHighlight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val speedValues = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private val speedLabels = listOf("0.75x", "1x", "1.25x", "1.5x", "2x")
private val voiceTags = listOf("system", "en", "hi", "bn")
private val voiceLabels = listOf("System", "English", "Hindi", "Bengali")

/** Read Aloud state of the viewer. */
internal class ViewerReading(
    private val context: Context,
    private val scope: CoroutineScope,
    private val engine: PdfEngine,
    private val documentId: String,
    private val listState: LazyListState,
    private val ocr: ViewerOcr
) {
    val reader = ReadAloud(context)
    var lifecycle: Lifecycle? = null
    var highlight by mutableStateOf<Pair<Int, Int>?>(null)
    var panelOpen by mutableStateOf(false)
    var reading by mutableStateOf(false)
        private set
    var readPage by mutableIntStateOf(0)
        private set
    private var speedIndex by mutableIntStateOf(1)
    private var voiceIndex by mutableIntStateOf(0)

    /** Applies the speed and voice chosen in Settings. */
    suspend fun applyDefaults(savedSpeed: Int, savedVoice: Int) {
        val speed = savedSpeed.coerceIn(0, speedValues.lastIndex)
        speedIndex = speed
        reader.setSpeed(speedValues[speed])
        val voice = savedVoice.coerceIn(0, voiceTags.lastIndex)
        if (voice > 0) {
            var tries = 0
            while (tries < 20 && !reader.isReady()) {
                delay(150)
                tries++
            }
            if (reader.setLanguage(voiceTags[voice])) {
                voiceIndex = voice
            }
        }
    }

    fun speakPage(page: Int) {
        scope.launch {
            if (!reading) {
                return@launch
            }
            if (page >= engine.pageCount) {
                highlight = null
                reading = false
                ReadAloudService.stop(context)
                return@launch
            }
            readPage = page
            // Scrolling waits for screen frames, which never come with the screen off,
            // so never let speech wait for it.
            if (lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true) {
                scope.launch { listState.animateScrollToItem(page) }
            }
            // PdfText uses MuPDF text first, then the saved OCR text for scanned pages.
            val storedText = PdfText.pageText(context, engine.file, page, documentId)
            val text = storedText.ifBlank {
                if (page == listState.firstVisibleItemIndex) ocr.extractedText.orEmpty() else ""
            }
            if (!reading) {
                return@launch
            }
            if (text.isBlank()) {
                speakPage(page + 1)
                return@launch
            }
            val started = reader.speak(text)
            if (!started) {
                reading = false
                ReadAloudService.stop(context)
                Toast.makeText(
                    context,
                    "Voice is not ready yet. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun stop() {
        reading = false
        highlight = null
        reader.stop()
        ReadAloudService.stop(context)
    }

    fun close() {
        stop()
        panelOpen = false
    }

    private fun toggle(currentPage: Int, title: String) {
        if (reading) {
            stop()
        } else {
            reading = true
            ReadAloudService.start(context, title)
            speakPage(currentPage)
        }
    }

    private fun previous(currentPage: Int) {
        if (reading) {
            speakPage((readPage - 1).coerceAtLeast(0))
        } else {
            scope.launch {
                listState.animateScrollToItem((currentPage - 1).coerceAtLeast(0))
            }
        }
    }

    private fun next(currentPage: Int) {
        if (reading) {
            speakPage(readPage + 1)
        } else {
            scope.launch {
                listState.animateScrollToItem(
                    (currentPage + 1).coerceAtMost(engine.pageCount - 1)
                )
            }
        }
    }

    private fun changeSpeed(index: Int) {
        speedIndex = index
        reader.setSpeed(speedValues[index])
        if (reading) {
            speakPage(readPage)
        }
    }

    private fun changeVoice(index: Int) {
        val worked = reader.setLanguage(voiceTags[index])
        if (worked) {
            voiceIndex = index
            if (reading) {
                speakPage(readPage)
            }
        } else {
            Toast.makeText(
                context,
                "This voice is not installed on your phone.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @Composable
    fun Panel(currentPage: Int, title: String) {
        ReaderBar(
            reading = reading,
            pageNumber = (if (reading) readPage else currentPage) + 1,
            pageCount = engine.pageCount,
            speedLabels = speedLabels,
            speedIndex = speedIndex,
            voiceLabels = voiceLabels,
            voiceIndex = voiceIndex,
            onClose = { close() },
            onToggle = { toggle(currentPage, title) },
            onStop = { stop() },
            onPrev = { previous(currentPage) },
            onNext = { next(currentPage) },
            onSpeed = { changeSpeed(it) },
            onVoice = { changeVoice(it) }
        )
    }
}

@Composable
internal fun rememberViewerReading(
    engine: PdfEngine,
    documentId: String,
    listState: LazyListState,
    ocr: ViewerOcr
): ViewerReading {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { ViewerReading(context, scope, engine, documentId, listState, ocr) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    state.lifecycle = lifecycle

    DisposableEffect(Unit) {
        onDispose {
            state.reader.shutdown()
            ReadAloudService.stop(context)
            ReadAloudService.onStopRequested = null
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && state.reading) {
                scope.launch { listState.scrollToItem(state.readPage) }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state) {
        state.reader.onRangeStart = { start, end -> state.highlight = start to end }
    }
    SideEffect {
        ReadAloudService.onStopRequested = { state.stop() }
        state.reader.onPageFinished = {
            if (state.reading) {
                state.speakPage(state.readPage + 1)
            }
        }
    }
    return state
}

/** The sentence being spoken, with the current word marked. */
@Composable
internal fun ViewerSpokenText(state: ViewerReading, modifier: Modifier = Modifier) {
    val spokenText by state.reader.currentSpokenText.collectAsState()
    if (!state.reading || spokenText.isBlank()) return
    val (start, end) = state.highlight ?: return
    SpokenSentenceHighlight(
        text = spokenText,
        range = TtsHighlight(start, end),
        modifier = modifier
    )
}

@Composable
private fun SpokenSentenceHighlight(
    text: String,
    range: TtsHighlight,
    modifier: Modifier = Modifier
) {
    val start = range.start.coerceIn(0, text.length)
    val end = range.end.coerceIn(start, text.length)
    val sentence = buildAnnotatedString {
        if (start >= end) {
            append(text)
        } else {
            val punctuation = charArrayOf('.', '!', '?', '।', '\n')
            val previous = if (start > 0) text.lastIndexOfAny(punctuation, start - 1) else -1
            var sentenceStart = previous + 1
            while (sentenceStart < start && text[sentenceStart].isWhitespace()) sentenceStart++
            val next = text.indexOfAny(punctuation, end)
            val sentenceEnd = if (next < 0) text.length else next + 1
            append(text.substring(sentenceStart, start))
            withStyle(
                SpanStyle(
                    background = Color(0xFFFFE082),
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(text.substring(start, end))
            }
            append(text.substring(end, sentenceEnd))
        }
    }
    Text(
        text = sentence,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .padding(12.dp),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 4
    )
}
