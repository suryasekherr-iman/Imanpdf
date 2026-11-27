package com.iman.pdf.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.iman.pdf.annot.AnnotBake
import com.iman.pdf.annot.HighlightMark
import com.iman.pdf.annot.MarkRect
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.iman.pdf.core.ocr.OcrEngine
import com.iman.pdf.annot.AnnotController
import com.iman.pdf.annot.AnnotTool
import com.iman.pdf.annot.SignatureStore
import com.iman.pdf.pdf.Bookmarks
import com.iman.pdf.pdf.CommentItem
import com.iman.pdf.pdf.Comments
import com.iman.pdf.pdf.LastPage
import com.iman.pdf.pdf.PdfActions
import com.iman.pdf.pdf.PdfEngine
import com.iman.pdf.pdf.PdfFormField
import com.iman.pdf.pdf.PdfExport
import com.iman.pdf.pdf.PdfText
import com.iman.pdf.pdf.OcrLine
import com.iman.pdf.pdf.OcrPage
import com.iman.pdf.pdf.OcrStore
import com.iman.pdf.pdf.ReadAloudService
import com.iman.pdf.pdf.ReadAloud
import com.iman.pdf.pdf.TtsHighlight
import com.iman.pdf.pdf.SearchHit
import com.iman.pdf.pdf.TocEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

private const val MAX_RENDER_WIDTH = 2400
private const val MIN_HIGHLIGHT_HEIGHT = 0.016f

private val speedValues = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private val speedLabels = listOf("0.75x", "1x", "1.25x", "1.5x", "2x")
private val voiceTags = listOf("system", "en", "hi", "bn")
private val voiceLabels = listOf("System", "English", "Hindi", "Bengali")

@Composable
fun ViewerScreen(
    engine: PdfEngine,
    title: String,
    documentId: String,
    startMode: String = "",
    initialPage: Int? = null,
    /** Where "Save to this PDF" writes; null when the original must not be overwritten. */
    originalUri: Uri? = null,
    onOpenTool: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val initial = initialPage?.takeIf { it in 0 until engine.pageCount }
        ?: LastPage.load(context, documentId, engine.pageCount)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initial,
        initialFirstVisibleItemScrollOffset = LastPage.loadOffset(context, documentId)
    )
    val scope = rememberCoroutineScope()
    var barsVisible by remember { mutableStateOf(true) }
    var annotationRevision by remember { mutableIntStateOf(0) }
    var isSavingPdf by remember { mutableStateOf(false) }
    var formDialogOpen by remember { mutableStateOf(false) }
    var formLoading by remember { mutableStateOf(false) }
    var formSaving by remember { mutableStateOf(false) }
    var formFields by remember { mutableStateOf<List<PdfFormField>>(emptyList()) }
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
    var ocrRevision by remember { mutableIntStateOf(0) }
    var isOcrProcessing by remember { mutableStateOf(false) }
    var extractedOcrText by remember { mutableStateOf<String?>(null) }

    val reader = remember { ReadAloud(context) }
    val spokenText by reader.currentSpokenText.collectAsState()
    var ttsHighlight by remember { mutableStateOf<Pair<Int, Int>?>(null) }
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
    var pageSnap by remember { mutableStateOf(false) }
    val hostView = LocalView.current

    // Defaults chosen in Settings: reading mode, Read aloud speed and voice.
    LaunchedEffect(Unit) {
        val userPrefs = com.iman.pdf.data.UserPrefs(context)
        pageSnap = userPrefs.pageTransition.first() == "snap"
        when (userPrefs.readingMode.first()) {
            "night" -> nightMode = true
            "sepia" -> sepiaMode = true
        }
        val speed = userPrefs.ttsSpeedIndex.first().coerceIn(0, speedValues.lastIndex)
        speedIndex = speed
        reader.setSpeed(speedValues[speed])
        val voice = userPrefs.ttsVoiceIndex.first().coerceIn(0, voiceTags.lastIndex)
        if (voice > 0) {
            var tries = 0
            while (tries < 20 && !reader.isReady()) {
                kotlinx.coroutines.delay(150)
                tries++
            }
            if (reader.setLanguage(voiceTags[voice])) {
                voiceIndex = voice
            }
        }
    }
    DisposableEffect(keepScreenOn) {
        hostView.keepScreenOn = keepScreenOn
        onDispose { hostView.keepScreenOn = false }
    }
    var bookmarkList by remember {
        mutableStateOf(Bookmarks.load(context, documentId, engine.pageCount))
    }
    var tocList by remember { mutableStateOf<List<TocEntry>>(emptyList()) }
    var tocLoading by remember { mutableStateOf(false) }

    val fileName = if (title.endsWith(".pdf", ignoreCase = true)) title else "$title.pdf"

    // "unsaved" = the working copy differs from what the user has saved; pending marks count too.
    var unsaved by remember { mutableStateOf(false) }
    var saveDialogOpen by remember { mutableStateOf(false) }
    var exitDialogOpen by remember { mutableStateOf(false) }
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    var afterSave by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun finishSave() {
        unsaved = false
        val next = afterSave
        afterSave = null
        next?.invoke()
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri == null) {
            afterSave = null
        } else {
            scope.launch {
                isSavingPdf = true
                val ok = withContext(Dispatchers.IO) {
                    PdfActions.saveCopy(context, engine.file, uri)
                }
                isSavingPdf = false
                Toast.makeText(
                    context,
                    if (ok) "Saved successfully." else "Could not save the PDF.",
                    Toast.LENGTH_SHORT
                ).show()
                if (ok) {
                    finishSave()
                } else {
                    afterSave = null
                }
            }
        }
    }

    fun openFormFill() {
        if (formLoading || formSaving) return
        formLoading = true
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                (0 until engine.pageCount).flatMap { page -> engine.formFields(page) }
            }
            formFields = loaded
            formLoading = false
            formDialogOpen = true
            if (loaded.isEmpty()) {
                Toast.makeText(context, "No interactive PDF form fields found.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Writes pending marks into the working copy. */
    suspend fun commitMarks(): Boolean {
        val pending = annot.marks.toList()
        if (pending.isEmpty()) return true
        val baked = AnnotBake.bake(context, engine.file, pending) ?: return false
        val replaced = withContext(Dispatchers.IO) { engine.replaceWith(baked) }
        baked.delete()
        if (replaced) {
            annot.clearAll()
            unsaved = true
            annotationRevision++
        }
        return replaced
    }

    fun savePdf(overwrite: Boolean) {
        if (isSavingPdf) return
        scope.launch {
            isSavingPdf = true
            if (!commitMarks()) {
                isSavingPdf = false
                afterSave = null
                Toast.makeText(context, "Could not save your edits.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val target = originalUri
            if (overwrite && target != null) {
                val ok = withContext(Dispatchers.IO) {
                    PdfActions.saveCopyToUri(context, engine.file, target)
                }
                isSavingPdf = false
                if (ok) {
                    Toast.makeText(context, "Saved to this PDF.", Toast.LENGTH_SHORT).show()
                    finishSave()
                } else {
                    Toast.makeText(
                        context,
                        "This PDF cannot be changed here. Save a new copy.",
                        Toast.LENGTH_LONG
                    ).show()
                    saveLauncher.launch(fileName)
                }
            } else {
                isSavingPdf = false
                saveLauncher.launch(fileName)
            }
        }
    }

    fun requestSave() {
        afterSave = null
        saveDialogOpen = true
    }

    /** Runs [action] (which leaves the viewer), asking first if changes would be lost. */
    fun requestExit(action: () -> Unit) {
        if (unsaved || annot.marks.isNotEmpty()) {
            pendingExit = action
            exitDialogOpen = true
        } else {
            action()
        }
    }

    /** Gives [use] a file that includes marks not saved yet (for Share and Print). */
    fun withCurrentPdf(use: (File) -> Unit) {
        val pending = annot.marks.toList()
        if (pending.isEmpty()) {
            use(engine.file)
            return
        }
        scope.launch {
            isSavingPdf = true
            val baked = AnnotBake.bake(context, engine.file, pending)
            isSavingPdf = false
            if (baked == null) {
                Toast.makeText(context, "Could not include your edits.", Toast.LENGTH_LONG).show()
            } else {
                use(baked)
            }
        }
    }

    /** Snaps a dragged highlight/underline/strikethrough to the text lines under it. */
    fun addTextMark(mark: HighlightMark) {
        scope.launch {
            val areas = withContext(Dispatchers.IO) {
                val bounds = engine.pageBounds(mark.page) ?: return@withContext emptyList<MarkRect>()
                val width = bounds.width().coerceAtLeast(1f)
                val height = bounds.height().coerceAtLeast(1f)
                engine.getTextRectsForSelection(
                    mark.page,
                    bounds.left + mark.left * width,
                    bounds.top + mark.top * height,
                    bounds.left + mark.right * width,
                    bounds.top + mark.bottom * height
                ).map {
                    MarkRect(
                        (it.left - bounds.left) / width,
                        (it.top - bounds.top) / height,
                        (it.right - bounds.left) / width,
                        (it.bottom - bounds.top) / height
                    )
                }
            }
            annot.add(
                when {
                    areas.isNotEmpty() -> mark.copy(
                        left = areas.minOf { it.left },
                        top = areas.minOf { it.top },
                        right = areas.maxOf { it.right },
                        bottom = areas.maxOf { it.bottom },
                        rects = areas
                    )
                    // Scanned page: keep the dragged box, tall enough to be visible.
                    mark.kind == 0 && mark.bottom - mark.top < MIN_HIGHLIGHT_HEIGHT -> mark.copy(
                        top = (mark.top - MIN_HIGHLIGHT_HEIGHT / 2f).coerceAtLeast(0f),
                        bottom = (mark.bottom + MIN_HIGHLIGHT_HEIGHT / 2f).coerceAtMost(1f)
                    )
                    else -> mark
                }
            )
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
                    val pending = annot.marks.toList()
                    if (pending.isEmpty()) {
                        PdfExport.writeImages(context, engine, uri, baseName)
                    } else {
                        val baked = checkNotNull(AnnotBake.bake(context, engine.file, pending))
                        val marked = checkNotNull(PdfEngine.open(context, Uri.fromFile(baked)))
                        try {
                            PdfExport.writeImages(context, marked, uri, baseName)
                        } finally {
                            marked.close()
                        }
                    }
                } catch (e: Exception) {
                    com.iman.pdf.logSwallowed("ViewerScreen:323", e)
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
        onDispose {
            reader.shutdown()
            ReadAloudService.stop(context)
            ReadAloudService.onStopRequested = null
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && reading) {
                scope.launch { listState.scrollToItem(readPage) }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
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

    suspend fun recognizeAndStorePage(page: Int): String? {
        val bitmap = withContext(Dispatchers.IO) {
            engine.renderPageToBitmap(page, 2f)
        } ?: throw IllegalStateException("MuPDF could not render this page for OCR.")

        try {
            val recognizedLines = OcrEngine.extractLinesFromBitmap(bitmap)
            if (recognizedLines.isEmpty()) return null

            val ocrPage = OcrPage(
                page = page,
                lines = recognizedLines.map { line ->
                    OcrLine(
                        text = line.text,
                        left = line.left,
                        top = line.top,
                        right = line.right,
                        bottom = line.bottom
                    )
                }
            )
            val saved = withContext(Dispatchers.IO) {
                OcrStore.savePage(context, documentId, ocrPage)
            }
            if (!saved) throw IllegalStateException("OCR text could not be saved.")
            if (page == listState.firstVisibleItemIndex) {
                extractedOcrText = ocrPage.text
            }
            return ocrPage.text
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    fun runOcr(pages: IntRange) {
        if (isOcrProcessing || pages.isEmpty()) return
        scope.launch {
            isOcrProcessing = true
            var completed = 0
            var unreadable = 0
            var failed = 0
            try {
                for (page in pages) {
                    try {
                        if (recognizeAndStorePage(page).isNullOrBlank()) {
                            unreadable++
                        } else {
                            completed++
                        }
                    } catch (e: Exception) {
                        com.iman.pdf.logSwallowed("ViewerScreen:430", e)
                        failed++
                    }
                }
            } finally {
                ocrRevision++
                isOcrProcessing = false
            }

            val message = when {
                completed == 0 && failed > 0 ->
                    "OCR failed. Please try again with a clearer page."
                completed == 0 ->
                    "OCR could not detect readable text. Try a clearer scan."
                unreadable + failed > 0 ->
                    "OCR completed for $completed page(s); ${unreadable + failed} page(s) had low-quality text or failed."
                else -> "OCR Complete! Text is now searchable."
            }
            Toast.makeText(
                context,
                message,
                if (pages.count() > 1) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun runOcrOnCurrentPage() {
        if (engine.pageCount <= 0) return
        val page = listState.firstVisibleItemIndex.coerceIn(0, engine.pageCount - 1)
        runOcr(page..page)
    }

    fun speakPage(page: Int) {
        scope.launch {
            if (!reading) {
                return@launch
            }
            if (page >= engine.pageCount) {
                ttsHighlight = null
                reading = false
                ReadAloudService.stop(context)
                return@launch
            }
            readPage = page
            // Scrolling waits for screen frames, which never come with the screen off,
            // so never let speech wait for it.
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                scope.launch { listState.animateScrollToItem(page) }
            }
            // PdfText uses MuPDF text first, then the saved OCR text for scanned pages.
            val storedText = PdfText.pageText(context, engine.file, page, documentId)
            val text = storedText.ifBlank {
                if (page == listState.firstVisibleItemIndex) extractedOcrText.orEmpty() else ""
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

    fun stopReading() {
        reading = false
        ttsHighlight = null
        reader.stop()
        ReadAloudService.stop(context)
    }

    fun closeReader() {
        stopReading()
        readerOpen = false
    }

    LaunchedEffect(reader) {
        reader.onRangeStart = { start, end -> ttsHighlight = start to end }
    }

    SideEffect {
        ReadAloudService.onStopRequested = { stopReading() }
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
            } else if (annotOpen && annot.marks.isEmpty()) {
                annot.select(AnnotTool.NONE)
                annotOpen = false
            } else if (readerOpen && !annotOpen) {
                closeReader()
            } else {
                requestExit(onBack)
            }
        }
    )

    LaunchedEffect(scale) {
        delay(300)
        renderScale = when {
            scale > 2f -> 3f
            scale > 1.05f -> 2f
            else -> 1f
        }
    }

    LaunchedEffect(scale) {
        LastPage.saveZoom(context, documentId, scale)
    }

    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
                com.iman.pdf.logSwallowed("ViewerScreen:645", e)
            }
        }
    }

    val renderWidth = minOf((screenWidthPx * 1.2f * renderScale).toInt(), MAX_RENDER_WIDTH)

    val containerHeightPx = zoom.height
    var dragging by remember { mutableStateOf(false) }
    var pillVisible by remember { mutableStateOf(true) }

    val currentPage by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    LaunchedEffect(documentId, currentPage, ocrRevision) {
        extractedOcrText = withContext(Dispatchers.IO) {
            OcrStore.loadPage(context, documentId, currentPage)?.text
        }
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

    LaunchedEffect(
        currentPage,
        listState.firstVisibleItemScrollOffset
    ) {
        delay(250)
        LastPage.savePosition(
            context,
            documentId,
            engine.pageCount,
            currentPage,
            listState.firstVisibleItemScrollOffset,
            0
        )
    }

    DisposableEffect(documentId) {
        onDispose {
            LastPage.savePosition(
                context,
                documentId,
                engine.pageCount,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                0
            )
            LastPage.saveZoom(context, documentId, zoom.scale)
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
            AnimatedVisibility(
                visible = barsVisible || searchOpen,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut()
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
                onBack = { requestExit(onBack) },
                onCloseSearch = { closeSearch() },
                onOpenSearch = { searchOpen = true },
                onTool = { id -> requestExit { onOpenTool(id) } },
                onViewSettings = { viewSettingsOpen = true },
                onExport = { exportOpen = true },
                onOcrPage = { runOcrOnCurrentPage() },
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
                onShare = { withCurrentPdf { PdfActions.share(context, it, title) } },
                onPrint = { withCurrentPdf { PdfActions.print(context, it, title) } },
                onSave = { requestSave() }
            )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (!barsVisible && !searchOpen) Modifier.statusBarsPadding() else Modifier)
                    .then(
                        if (!barsVisible && !annotOpen && !readerOpen && !searchOpen) {
                            Modifier.navigationBarsPadding()
                        } else {
                            Modifier
                        }
                    )
                    .zoomGestures(
                        zoom, listState, scope,
                        panEnabled = { annot.tool == AnnotTool.NONE },
                        doubleTapEnabled = { annot.tool == AnnotTool.NONE },
                        onTap = {
                            if (annot.tool == AnnotTool.NONE && !searchOpen) {
                                barsVisible = !barsVisible
                            }
                        }
                    )
            ) {
                run {
                    val snapFling = androidx.compose.foundation.gestures.snapping
                        .rememberSnapFlingBehavior(listState)
                    LazyColumn(
                        state = listState,
                        flingBehavior = if (pageSnap) snapFling
                        else androidx.compose.foundation.gestures.ScrollableDefaults.flingBehavior(),
                        userScrollEnabled = !drawing,
                        modifier = Modifier
                            .layout { measurable, constraints ->
                                val h = (constraints.maxHeight / zoom.scale).roundToInt().coerceAtLeast(1)
                                val p = measurable.measure(Constraints.fixed(constraints.maxWidth, h))
                                layout(constraints.maxWidth, constraints.maxHeight) { p.place(0, 0) }
                            }
                            .graphicsLayer {
                                scaleX = zoom.scale
                                scaleY = zoom.scale
                                translationX = zoom.offsetX
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
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
                                searchHits = hits.filter { it.page == index },
                                activeSearchHit = hits.getOrNull(activeHit)?.takeIf { it.page == index },
                                documentId = documentId,
                                ocrRevision = ocrRevision,
                                annotationRevision = annotationRevision,
                                onTextMark = { addTextMark(it) }
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

                if (reading && spokenText.isNotBlank()) {
                    ttsHighlight?.let { (start, end) ->
                        SpokenSentenceHighlight(
                            text = spokenText,
                            range = TtsHighlight(start, end),
                            modifier = Modifier.align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(12.dp)
                        )
                    }
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
                AnimatedVisibility(
                    visible = barsVisible,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
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
                        onShare = { withCurrentPdf { PdfActions.share(context, it, title) } },
                        onPrint = { withCurrentPdf { PdfActions.print(context, it, title) } },
                        onSave = { requestSave() },
                        onOcrPage = { runOcrOnCurrentPage() },
                        onOcrDocument = { runOcr(0 until engine.pageCount) },
                        onFillForm = { openFormFill() }
                    )
                }
                        }
            if (annotOpen) {
                AnnotToolbar(
                    annot = annot,
                    onSaveAnnotations = { requestSave() },
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
                            ReadAloudService.start(context, title)
                            speakPage(currentPage)
                        }
                    },
                    onStop = { stopReading() },
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

        if (bookmarksOpen) {
            BookmarksSheet(
                bookmarks = bookmarkList,
                toc = tocList,
                tocLoading = tocLoading,
                notes = bookmarkList.associateWith { page ->
                    Bookmarks.note(context, documentId, engine.pageCount, page)
                },
                onSaveNote = { page, note ->
                    Bookmarks.saveNote(context, documentId, engine.pageCount, page, note)
                },
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

        if (formDialogOpen) {
            PdfFormFillDialog(
                fields = formFields,
                saving = formSaving,
                onDismiss = { formDialogOpen = false },
                onSave = { updates ->
                    if (updates.isNotEmpty() && !formSaving) {
                        scope.launch {
                            formSaving = true
                            val saved = withContext(Dispatchers.IO) {
                                try {
                                    engine.updateFormFields(updates) &&
                                        engine.saveDocument(engine.file.absolutePath)
                                } catch (e: Exception) {
                                    com.iman.pdf.logSwallowed("ViewerScreen:1085", e)
                                    false
                                }
                            }
                            formSaving = false
                            if (saved) {
                                unsaved = true
                                annotationRevision++
                                formDialogOpen = false
                                Toast.makeText(context, "Form updated. Tap Save to keep it.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Could not save form values. Check that the PDF allows editing.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            )
        }
        if (exitDialogOpen) {
            AlertDialog(
                onDismissRequest = { exitDialogOpen = false },
                title = { Text("Save changes?") },
                text = { Text("This PDF has changes that are not saved.") },
                confirmButton = {
                    TextButton(onClick = {
                        exitDialogOpen = false
                        afterSave = pendingExit
                        saveDialogOpen = true
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        exitDialogOpen = false
                        annot.clearAll()
                        unsaved = false
                        pendingExit?.invoke()
                    }) { Text("Discard") }
                }
            )
        }
        if (saveDialogOpen) {
            val dismiss = {
                saveDialogOpen = false
                afterSave = null
            }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text("Save PDF") },
                text = {
                    Text(
                        if (originalUri != null) {
                            "Save into this PDF, or keep it unchanged and save a new copy."
                        } else {
                            "This PDF was unlocked with a password. The new copy will not have a password."
                        }
                    )
                },
                confirmButton = {
                    Column(horizontalAlignment = Alignment.End) {
                        if (originalUri != null) {
                            TextButton(onClick = {
                                saveDialogOpen = false
                                savePdf(overwrite = true)
                            }) { Text("Save to this PDF") }
                        }
                        TextButton(onClick = {
                            saveDialogOpen = false
                            savePdf(overwrite = false)
                        }) { Text("Save as new copy") }
                        TextButton(onClick = dismiss) { Text("Cancel") }
                    }
                }
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

        if (isSavingPdf) {
            androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Text("Saving PDF...", modifier = Modifier.padding(top = 12.dp))
                }
            }
        }

        if (isOcrProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Recognizing PDF text...",
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
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
