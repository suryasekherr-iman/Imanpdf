package com.iman.pdf.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.iman.pdf.annot.AnnotController
import com.iman.pdf.annot.AnnotTool
import com.iman.pdf.annot.SignatureStore
import com.iman.pdf.pdf.PdfActions
import com.iman.pdf.pdf.PdfEngine
import com.iman.pdf.pdf.PdfText
import com.iman.pdf.pdf.ReadAloud
import com.iman.pdf.pdf.SearchHit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_RENDER_WIDTH = 2200

private val speedValues = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private val speedLabels = listOf("0.75x", "1x", "1.25x", "1.5x", "2x")
private val voiceTags = listOf("system", "en", "hi", "bn")
private val voiceLabels = listOf("System", "English", "Hindi", "Bengali")

@Composable
fun ViewerScreen(
    engine: PdfEngine,
    title: String,
    startMode: String = "",
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val horizontalState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val screenWidthPx = configuration.screenWidthDp * density.density

    val zoom = remember { ZoomState() }
    val scale = zoom.scale
    var renderScale by remember { mutableFloatStateOf(1f) }

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }

    val reader = remember { ReadAloud(context) }
    var readerOpen by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var readPage by remember { mutableIntStateOf(0) }
    var speedIndex by remember { mutableIntStateOf(1) }
    var voiceIndex by remember { mutableIntStateOf(0) }
    var liquidOpen by remember { mutableStateOf(false) }

    val annot = remember { AnnotController() }
    var annotOpen by remember { mutableStateOf(false) }
    val drawing = annot.tool != AnnotTool.NONE

    val fileName = if (title.endsWith(".pdf", ignoreCase = true)) title else "$title.pdf"
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            val ok = PdfActions.saveCopy(context, engine.file, uri)
            Toast.makeText(
                context,
                if (ok) "Copy saved" else "Could not save the copy",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose { reader.shutdown() }
    }

    fun closeSearch() {
        searchOpen = false
        hits = emptyList()
        searching = false
        searched = false
    }

    fun runSearch() {
        val text = query.trim()
        if (text.isEmpty()) {
            return
        }
        scope.launch {
            searching = true
            searched = false
            keyboard?.hide()
            hits = PdfText.search(context, engine.file, text)
            searching = false
            searched = true
        }
    }

    fun speakPage(page: Int) {
        scope.launch {
            if (!reading) {
                return@launch
            }
            if (page >= engine.pageCount) {
                reading = false
                return@launch
            }
            readPage = page
            listState.animateScrollToItem(page)
            val text = PdfText.pageText(context, engine.file, page)
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
                Toast.makeText(
                    context,
                    "Voice is not ready yet. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun stopReading() {
        reading = false
        reader.stop()
    }

    fun closeReader() {
        stopReading()
        readerOpen = false
    }

    SideEffect {
        reader.onPageFinished = {
            if (reading) {
                speakPage(readPage + 1)
            }
        }
    }

    LaunchedEffect(Unit) {
        when (startMode) {
            "fill_sign" -> {
                annot.signature = SignatureStore.load(context)
                annotOpen = true
                annot.select(AnnotTool.SIGN)
            }
            "comment" -> {
                annotOpen = true
                annot.select(AnnotTool.HIGHLIGHT)
            }
            "liquid" -> liquidOpen = true
            "read_aloud" -> readerOpen = true
        }
    }

    BackHandler(
        onBack = {
            if (searchOpen) {
                closeSearch()
            } else if (annotOpen) {
                if (annot.marks.isEmpty()) {
                    annot.select(AnnotTool.NONE)
                    annotOpen = false
                } else {
                    Toast.makeText(
                        context,
                        "Tap the X on the toolbar to close editing",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else if (readerOpen) {
                closeReader()
            } else {
                onBack()
            }
        }
    )

    LaunchedEffect(scale) {
        delay(350)
        renderScale = scale
    }

    LaunchedEffect(scale) {
        zoom.settle(listState, horizontalState)
    }

    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    val renderWidth = minOf((screenWidthPx * 1.5f * renderScale).toInt(), MAX_RENDER_WIDTH)

    val containerHeightPx = zoom.height
    var dragging by remember { mutableStateOf(false) }
    var pillVisible by remember { mutableStateOf(true) }

    val currentPage by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    val progress by remember {
        derivedStateOf {
            val total = engine.pageCount
            if (total <= 1) {
                0f
            } else {
                val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                val fraction = if (first != null && first.size > 0) {
                    (-first.offset).toFloat() / first.size.toFloat()
                } else {
                    0f
                }
                ((listState.firstVisibleItemIndex + fraction.coerceIn(0f, 1f)) /
                    (total - 1).toFloat()).coerceIn(0f, 1f)
            }
        }
    }

    val scrolling = listState.isScrollInProgress
    LaunchedEffect(scrolling, dragging, drawing) {
        if (scrolling || dragging || drawing) {
            pillVisible = true
        } else {
            delay(1500)
            pillVisible = false
        }
    }

    val pillAlpha by animateFloatAsState(
        targetValue = if (pillVisible) 1f else 0f,
        label = "pillAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            ViewerTopBar(
                title = title,
                searchOpen = searchOpen,
                query = query,
                focusRequester = focusRequester,
                onQueryChange = { query = it },
                onSearchSubmit = { runSearch() },
                onClearQuery = {
                    query = ""
                    hits = emptyList()
                    searched = false
                },
                onBack = onBack,
                onCloseSearch = { closeSearch() },
                onOpenSearch = { searchOpen = true },
                onAnnotate = {
                    stopReading()
                    readerOpen = false
                    annotOpen = true
                },
                onLiquid = {
                    stopReading()
                    liquidOpen = true
                },
                onReadAloud = {
                    if (readerOpen) {
                        closeReader()
                    } else {
                        readerOpen = true
                    }
                },
                onShare = { PdfActions.share(context, engine.file, title) },
                onPrint = { PdfActions.print(context, engine.file, title) },
                onSave = { saveLauncher.launch(fileName) }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .zoomGestures(zoom, listState, horizontalState, scope) {
                        annot.tool == AnnotTool.NONE
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom.gesture
                            scaleY = zoom.gesture
                            transformOrigin = TransformOrigin(zoom.originX, zoom.originY)
                        }
                        .horizontalScroll(horizontalState, enabled = !drawing)
                ) {
                    LazyColumn(
                        state = listState,
                        userScrollEnabled = !drawing,
                        modifier = Modifier
                            .fillMaxHeight()
                            .width((configuration.screenWidthDp * scale).dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(count = engine.pageCount, key = { it }) { index ->
                            PdfPageItem(
                                engine = engine,
                                index = index,
                                renderWidth = renderWidth,
                                annot = annot
                            )
                        }
                    }
                }

                if (pillAlpha > 0f) {
                    PagePill(
                        pageNumber = currentPage + 1,
                        pageCount = engine.pageCount,
                        progress = progress,
                        containerHeightPx = containerHeightPx,
                        alpha = pillAlpha,
                        listState = listState,
                        onDragging = { dragging = it }
                    )
                }

                if (searchOpen && (searching || searched)) {
                    SearchResults(
                        searching = searching,
                        hits = hits,
                        onPick = { page ->
                            closeSearch()
                            scope.launch { listState.scrollToItem(page) }
                        }
                    )
                }
            }

            if (annotOpen) {
                AnnotToolbar(
                    annot = annot,
                    engine = engine,
                    title = title,
                    onClose = {
                        annot.select(AnnotTool.NONE)
                        annotOpen = false
                    }
                )
            }

            if (readerOpen) {
                ReaderBar(
                    reading = reading,
                    pageNumber = (if (reading) readPage else currentPage) + 1,
                    speedLabel = speedLabels[speedIndex],
                    voiceLabel = voiceLabels[voiceIndex],
                    voiceLabels = voiceLabels,
                    onClose = { closeReader() },
                    onToggle = {
                        if (reading) {
                            stopReading()
                        } else {
                            reading = true
                            speakPage(currentPage)
                        }
                    },
                    onSpeed = {
                        speedIndex = (speedIndex + 1) % speedValues.size
                        reader.setSpeed(speedValues[speedIndex])
                        if (reading) {
                            speakPage(readPage)
                        }
                    },
                    onVoice = { index ->
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
                )
            }
        }

        if (liquidOpen) {
            LiquidScreen(
                engine = engine,
                startPage = currentPage,
                onClose = { page ->
                    liquidOpen = false
                    scope.launch { listState.scrollToItem(page) }
                }
            )
        }
    }
}
