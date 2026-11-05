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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.iman.pdf.annot.AnnotController
import com.iman.pdf.annot.AnnotTool
import com.iman.pdf.annot.SignatureStore
import com.iman.pdf.pdf.Bookmarks
import com.iman.pdf.pdf.CommentItem
import com.iman.pdf.pdf.Comments
import com.iman.pdf.pdf.LastPage
import com.iman.pdf.pdf.PdfActions
import com.iman.pdf.pdf.PdfEngine
import com.iman.pdf.pdf.PdfExport
import com.iman.pdf.pdf.PdfText
import com.iman.pdf.pdf.PdfOcr
import com.iman.pdf.pdf.OcrStore
import com.iman.pdf.pdf.ReadAloud
import com.iman.pdf.pdf.SearchHit
import com.iman.pdf.pdf.TocEntry
import com.iman.pdf.data.StudentStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_RENDER_WIDTH = 2200

private val speedValues = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f)
private val speedLabels = listOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x", "2.5x", "3x")
private val voiceTags = listOf("system", "en", "hi", "bn", "mr", "gu", "pa", "ta", "te", "kn", "ml", "ur")
private val voiceLabels = listOf(
    "System", "English", "Hindi", "Bengali", "Marathi", "Gujarati",
    "Punjabi", "Tamil", "Telugu", "Kannada", "Malayalam", "Urdu"
)

@Composable
fun ViewerScreen(
    engine: PdfEngine,
    title: String,
    documentId: String,
    startMode: String = "",
        onOpenTool: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = LastPage.load(context, documentId, engine.pageCount)
    )
    val horizontalState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val screenWidthPx = configuration.screenWidthDp * density.density

    val zoom = remember(documentId) { ZoomState().apply { scale = LastPage.loadZoom(context, documentId) } }
    val scale = zoom.scale
    var renderScale by remember { mutableFloatStateOf(1f) }

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var activeHit by remember { mutableIntStateOf(-1) }

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

    var bookmarksOpen by remember { mutableStateOf(false) }
    var viewSettingsOpen by remember { mutableStateOf(false) }
    var commentsOpen by remember { mutableStateOf(false) }
    var commentsLoading by remember { mutableStateOf(false) }
    var commentItems by remember { mutableStateOf<List<CommentItem>>(emptyList()) }
    var keepScreenOn by remember { mutableStateOf(false) }
    var nightMode by remember { mutableStateOf(false) }
    var sepiaMode by remember { mutableStateOf(false) }
    val hostView = LocalView.current
    DisposableEffect(keepScreenOn) {
        hostView.keepScreenOn = keepScreenOn
        onDispose { hostView.keepScreenOn = false }
    }
    var bookmarkList by remember {
        mutableStateOf(Bookmarks.load(context, documentId, engine.pageCount))
    }
    var tocList by remember { mutableStateOf<List<TocEntry>>(emptyList()) }
    var tocLoading by remember { mutableStateOf(false) }
    var ocrBusy by remember { mutableStateOf(false) }
    var ocrDone by remember { mutableIntStateOf(0) }
    var ocrTotal by remember { mutableIntStateOf(0) }
    var ocrRevision by remember { mutableIntStateOf(0) }
    val studentStore = remember { StudentStore(context) }
    var flashcardDialog by remember { mutableStateOf(false) }
    var flashcardQuestion by remember { mutableStateOf("") }
    var flashcardAnswer by remember { mutableStateOf("") }
    var flashcardLoading by remember { mutableStateOf(false) }

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

    var exportOpen by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf("") }
    val baseName = fileName.removeSuffix(".pdf").removeSuffix(".PDF")
    val textLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = PdfExport.writeText(context, uri, exportText)
                Toast.makeText(
                    context,
                    if (ok) "Text file saved" else "Could not save the text file",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                Toast.makeText(context, "Exporting pages...", Toast.LENGTH_SHORT).show()
                val saved = try {
                    PdfExport.writeImages(context, engine, uri, baseName)
                } catch (e: Exception) {
                    0
                }
                Toast.makeText(
                    context,
                    if (saved == engine.pageCount) {
                        "$saved images saved"
                    } else {
                        "$saved of ${engine.pageCount} images saved"
                    },
                    Toast.LENGTH_LONG
                ).show()
            }
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
        activeHit = -1
    }

    fun goToHit(index: Int) {
        if (hits.isEmpty()) {
            return
        }
        val target = ((index % hits.size) + hits.size) % hits.size
        activeHit = target
        scope.launch { listState.scrollToItem(hits[target].page) }
    }

    fun runSearch() {
        val text = query.trim()
        if (text.isEmpty()) {
            return
        }
        scope.launch {
            searching = true
            searched = false
            activeHit = -1
            keyboard?.hide()
            hits = PdfText.search(context, engine.file, text, documentId)
            searching = false
            searched = true
        }
    }

    fun runOcr(pages: IntRange) {
        if (ocrBusy || pages.isEmpty()) return
        reading = false
        reader.stop()
        ocrBusy = true
        ocrDone = 0
        ocrTotal = pages.count()
        scope.launch {
            var recognized = 0
            var failed = 0
            try {
                for (page in pages) {
                    val existingText = PdfText.pageText(context, engine.file, page, documentId)
                    if (existingText.isBlank()) {
                        val result = PdfOcr.recognize(context, engine.file, page)
                        if (result != null && result.text.isNotBlank()) {
                            OcrStore.savePage(context, documentId, result)
                            recognized++
                        } else {
                            failed++
                        }
                    }
                    ocrDone++
                }
            } catch (_: Exception) {
                failed++
            } finally {
                ocrRevision++
                ocrBusy = false
            }
            val message = when {
                recognized > 0 -> "OCR completed: $recognized page(s) recognized"
                failed > 0 -> "No readable text found. Check scan quality and try again."
                else -> "No scanned pages found; text is already available."
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
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
            val text = PdfText.pageText(context, engine.file, page, documentId)
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
            "export" -> exportOpen = true
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
        LastPage.saveZoom(context, documentId, scale)
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
    val hitsByPage = remember(hits) { hits.groupBy { it.page } }
    val activeSearchHit = hits.getOrNull(activeHit)

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

    LaunchedEffect(currentPage) {
        delay(500)
        LastPage.save(context, documentId, engine.pageCount, currentPage)
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
                    activeHit = -1
                },
                onBack = onBack,
                onCloseSearch = { closeSearch() },
                onOpenSearch = { searchOpen = true },
                onTool = { id -> onOpenTool(id) },
                onViewSettings = { viewSettingsOpen = true },
                onExport = { exportOpen = true },
                onOcrPage = { runOcr(currentPage..currentPage) },
                onOcrDocument = { runOcr(0 until engine.pageCount) },
                onComments = {
                    commentsOpen = true
                    commentsLoading = true
                    scope.launch {
                        val saved = Comments.load(context, engine.file)
                        commentItems = Comments.fromMarks(annot.marks.toList()) + saved
                        commentsLoading = false
                    }
                },
                onBookmarks = {
                    bookmarksOpen = true
                    scope.launch {
                        if (tocList.isEmpty()) {
                            tocLoading = true
                            tocList = Bookmarks.toc(context, engine.file)
                            tocLoading = false
                        }
                    }
                },
                onAddBookmark = {
                    val added = Bookmarks.add(context, documentId, engine.pageCount, currentPage)
                    bookmarkList = Bookmarks.load(context, documentId, engine.pageCount)
                    Toast.makeText(
                        context,
                        if (added) {
                            "Bookmark added: page " + (currentPage + 1)
                        } else {
                            "Page " + (currentPage + 1) + " is already bookmarked"
                        },
                        Toast.LENGTH_SHORT
                    ).show()
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
                                annot = annot,
                                nightMode = nightMode,
                                sepiaMode = sepiaMode,
                                searchHits = hitsByPage[index].orEmpty(),
                                activeSearchHit = activeSearchHit,
                                documentId = documentId,
                                ocrRevision = ocrRevision
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
                        onPick = { position, _ ->
                            searched = false
                            goToHit(position)
                        }
                    )
                }
            }
            if (searchOpen && !searching && !searched && activeHit >= 0 && hits.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { searched = true }) {
                        Text("List")
                    }
                    Text(
                        text = (activeHit + 1).toString() + " / " + hits.size,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = { goToHit(activeHit - 1) }) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous result")
                    }
                    IconButton(onClick = { goToHit(activeHit + 1) }) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next result")
                    }
                }
            }
                        if (!annotOpen && !readerOpen && !searchOpen) {
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        flashcardDialog = true
                        flashcardQuestion = ""
                        flashcardAnswer = ""
                        flashcardLoading = true
                        scope.launch {
                            flashcardQuestion = PdfText.pageText(context, engine.file, currentPage, documentId).trim()
                            flashcardLoading = false
                        }
                    }) { Text("Create flashcard from page") }
                }
                ViewerBottomBar(
                    onReadAloud = { readerOpen = true },
                    onTool = { tool ->
                        stopReading()
                        if (tool == AnnotTool.SIGN) {
                            annot.signature = SignatureStore.load(context)
                        }
                        annotOpen = true
                        annot.select(tool)
                    },
                    onLiquid = {
                        stopReading()
                        liquidOpen = true
                    },
                    onSearch = { searchOpen = true },
                    onShare = { PdfActions.share(context, engine.file, title) },
                    onPrint = { PdfActions.print(context, engine.file, title) },
                    onSave = { saveLauncher.launch(fileName) }
                )
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
                    pageCount = engine.pageCount,
                    speedLabels = speedLabels,
                    speedIndex = speedIndex,
                    voiceLabels = voiceLabels,
                    voiceIndex = voiceIndex,
                    onClose = { closeReader() },
                    onToggle = {
                        if (reading) {
                            stopReading()
                        } else {
                            reading = true
                            speakPage(currentPage)
                        }
                    },
                    onPrev = {
                        if (reading) {
                            speakPage((readPage - 1).coerceAtLeast(0))
                        } else {
                            scope.launch {
                                listState.animateScrollToItem((currentPage - 1).coerceAtLeast(0))
                            }
                        }
                    },
                    onNext = {
                        if (reading) {
                            speakPage(readPage + 1)
                        } else {
                            scope.launch {
                                listState.animateScrollToItem(
                                    (currentPage + 1).coerceAtMost(engine.pageCount - 1)
                                )
                            }
                        }
                    },
                    onSpeed = { index ->
                        speedIndex = index
                        reader.setSpeed(speedValues[index])
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

        if (flashcardDialog) {
            AlertDialog(
                onDismissRequest = { if (!flashcardLoading) flashcardDialog = false },
                title = { Text("Create flashcard · page ${currentPage + 1}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (flashcardLoading) {
                            CircularProgressIndicator()
                            Text("Extracting page text...")
                        } else {
                            OutlinedTextField(value = flashcardQuestion, onValueChange = { flashcardQuestion = it }, label = { Text("Question / source text") }, minLines = 3)
                            OutlinedTextField(value = flashcardAnswer, onValueChange = { flashcardAnswer = it }, label = { Text("Answer") }, minLines = 2)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (flashcardQuestion.isNotBlank() && flashcardAnswer.isNotBlank()) {
                            studentStore.addFlashcard(flashcardQuestion, flashcardAnswer)
                            flashcardDialog = false
                            Toast.makeText(context, "Flashcard saved offline", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Add both question and answer", Toast.LENGTH_SHORT).show()
                        }
                    }, enabled = !flashcardLoading && flashcardQuestion.isNotBlank() && flashcardAnswer.isNotBlank()) { Text("Save card") }
                },
                dismissButton = { TextButton(onClick = { flashcardDialog = false }) { Text("Cancel") } }
            )
        }

        if (ocrBusy) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Recognizing scanned text") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            "Page $ocrDone of $ocrTotal",
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                },
                confirmButton = {}
            )
        }

        if (bookmarksOpen) {
            BookmarksSheet(
                bookmarks = bookmarkList,
                toc = tocList,
                tocLoading = tocLoading,
                onJump = { page ->
                    bookmarksOpen = false
                    scope.launch { listState.scrollToItem(page) }
                },
                onRemove = { page ->
                    Bookmarks.remove(context, documentId, engine.pageCount, page)
                    bookmarkList = Bookmarks.load(context, documentId, engine.pageCount)
                },
                onDismiss = { bookmarksOpen = false }
            )
        }

        if (exportOpen) {
            ExportDialog(
                onText = {
                    exportOpen = false
                    scope.launch {
                        val text = PdfExport.allText(context, engine.file, documentId)
                        if (text.isBlank()) {
                            Toast.makeText(
                                context,
                                "No text found in this PDF (it may be a scan)",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            exportText = text
                            textLauncher.launch("$baseName.txt")
                        }
                    }
                },
                onImages = {
                    exportOpen = false
                    folderLauncher.launch(null)
                },
                onDismiss = { exportOpen = false }
            )
        }

        if (commentsOpen) {
            CommentsSheet(
                items = commentItems,
                loading = commentsLoading,
                onJump = { page ->
                    commentsOpen = false
                    scope.launch { listState.scrollToItem(page) }
                },
                onDismiss = { commentsOpen = false }
            )
        }

        if (viewSettingsOpen) {
            ViewSettingsSheet(
                keepScreenOn = keepScreenOn,
                nightMode = nightMode,
                sepiaMode = sepiaMode,
                onKeepScreenOn = { keepScreenOn = it },
                onNightMode = {
                    nightMode = it
                    if (it) sepiaMode = false
                },
                onSepiaMode = {
                    sepiaMode = it
                    if (it) nightMode = false
                },
                onDismiss = { viewSettingsOpen = false }
            )
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
