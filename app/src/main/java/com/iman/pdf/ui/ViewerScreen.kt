package com.iman.pdf.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.iman.pdf.pdf.PdfActions
import com.iman.pdf.pdf.PdfEngine
import com.iman.pdf.pdf.PdfText
import com.iman.pdf.pdf.ReadAloud
import com.iman.pdf.pdf.SearchHit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val MAX_ZOOM = 3f
private const val MAX_RENDER_WIDTH = 2200

private val speedValues = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private val speedLabels = listOf("0.75x", "1x", "1.25x", "1.5x", "2x")
private val voiceTags = listOf("system", "en", "hi", "bn")
private val voiceLabels = listOf("System", "English", "Hindi", "Bengali")

@Composable
fun ViewerScreen(
    engine: PdfEngine,
    title: String,
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

    var scale by remember { mutableFloatStateOf(1f) }
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

    BackHandler(
        onBack = {
            if (searchOpen) {
                closeSearch()
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

    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    val renderWidth = minOf((screenWidthPx * 1.5f * renderScale).toInt(), MAX_RENDER_WIDTH)

    val pillHeight = 40.dp
    val pillHeightPx = with(density) { pillHeight.toPx() }
    var containerHeightPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
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
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (searchOpen) {
                IconButton(onClick = { closeSearch() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search in PDF") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                )
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            query = ""
                            hits = emptyList()
                            searched = false
                        }
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            } else {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = title,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = {
                        if (readerOpen) {
                            closeReader()
                        } else {
                            readerOpen = true
                        }
                    }
                ) {
                    Icon(Icons.Outlined.Headphones, contentDescription = "Read aloud")
                }
                IconButton(onClick = { searchOpen = true }) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search")
                }
                ViewerMenu(
                    onShare = { PdfActions.share(context, engine.file, title) },
                    onPrint = { PdfActions.print(context, engine.file, title) },
                    onSave = { saveLauncher.launch(fileName) }
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { containerHeightPx = it.height.toFloat() }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial
                        )
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.size >= 2) {
                                val zoomChange = event.calculateZoom()
                                if (zoomChange != 1f) {
                                    scale = (scale * zoomChange).coerceIn(1f, MAX_ZOOM)
                                }
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) {
                                        change.consume()
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = if (scale > 1.05f) 1f else 2f
                        }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalState)
            ) {
                LazyColumn(
                    state = listState,
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
                            renderWidth = renderWidth
                        )
                    }
                }
            }

            if (pillAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset {
                            val travel = (containerHeightPx - pillHeightPx).coerceAtLeast(0f)
                            IntOffset(0, (progress * travel).roundToInt())
                        }
                        .alpha(pillAlpha)
                        .height(pillHeight)
                        .background(
                            MaterialTheme.colorScheme.inverseSurface,
                            RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                        )
                        .pointerInput(engine.pageCount) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragging = true
                                    dragProgress = progress
                                },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    val travelPx =
                                        (containerHeightPx - pillHeightPx).coerceAtLeast(1f)
                                    dragProgress =
                                        (dragProgress + dragAmount / travelPx).coerceIn(0f, 1f)
                                    val position = dragProgress * (engine.pageCount - 1)
                                    val index = position.toInt()
                                        .coerceIn(0, engine.pageCount - 1)
                                    val fraction = position - index
                                    val size = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull()?.size ?: 0
                                    scope.launch {
                                        listState.scrollToItem(
                                            index,
                                            (fraction * size).roundToInt()
                                        )
                                    }
                                }
                            )
                        }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${currentPage + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                }
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
}
