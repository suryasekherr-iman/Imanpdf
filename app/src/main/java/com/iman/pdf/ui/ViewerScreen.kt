package com.iman.pdf.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.iman.pdf.annot.AnnotController
import com.iman.pdf.annot.AnnotTool
import com.iman.pdf.annot.SignatureStore
import com.iman.pdf.pdf.LastPage
import com.iman.pdf.pdf.PdfEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Keep this composable small. A very large one compiles to code that Android rejects
// (VerifyError), which closes the app as soon as a PDF is opened. Put new viewer
// features in the Viewer*.kt files next to this one.
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
    var liquidOpen by remember { mutableStateOf(false) }
    val zoom = remember(documentId) { ZoomState().apply { scale = LastPage.loadZoom(context, documentId) } }

    val annot = remember { AnnotController() }
    var annotOpen by remember { mutableStateOf(false) }
    val drawing = annot.tool != AnnotTool.NONE

    val fileName = if (title.endsWith(".pdf", ignoreCase = true)) title else "$title.pdf"
    val baseName = fileName.removeSuffix(".pdf").removeSuffix(".PDF")

    val display = remember { ViewerDisplay() }
    val search = remember { ViewerSearch(context, scope, engine, documentId, listState) }
    val ocr = remember { ViewerOcr(context, scope, engine, documentId, listState) }
    val panels = remember { ViewerPanels(context, scope, engine, documentId, annot) }
    val reading = rememberViewerReading(engine, documentId, listState, ocr)
    val saving = rememberViewerSaving(engine, annot, originalUri, fileName)

    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    val openLiquid = {
        reading.stop()
        liquidOpen = true
    }

    // Defaults chosen in Settings: reading mode, Read aloud speed and voice.
    LaunchedEffect(Unit) {
        val userPrefs = com.iman.pdf.data.UserPrefs(context)
        display.pageSnap = userPrefs.pageTransition.first() == "snap"
        when (userPrefs.readingMode.first()) {
            "night" -> display.nightMode = true
            "sepia" -> display.sepiaMode = true
        }
        reading.applyDefaults(
            userPrefs.ttsSpeedIndex.first(),
            userPrefs.ttsVoiceIndex.first()
        )
    }

    val hostView = LocalView.current
    DisposableEffect(display.keepScreenOn) {
        hostView.keepScreenOn = display.keepScreenOn
        onDispose { hostView.keepScreenOn = false }
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
            "read_aloud" -> reading.panelOpen = true
            "export" -> panels.exportOpen = true
        }
    }

    BackHandler(
        onBack = {
            if (search.open) {
                search.close()
            } else if (annotOpen && annot.marks.isEmpty()) {
                annot.select(AnnotTool.NONE)
                annotOpen = false
            } else if (reading.panelOpen && !annotOpen) {
                reading.close()
            } else {
                saving.requestExit(onBack)
            }
        }
    )

    LaunchedEffect(search.open) {
        if (search.open) {
            try {
                search.focusRequester.requestFocus()
            } catch (e: Exception) {
                com.iman.pdf.logSwallowed("ViewerScreen:focus", e)
            }
        }
    }

    LaunchedEffect(documentId, currentPage, ocr.revision) {
        ocr.loadPageText(currentPage)
    }

    ViewerPositionMemory(documentId, engine.pageCount, listState, zoom)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            ViewerTopSection(
                visible = barsVisible || search.open,
                title = title,
                currentPage = currentPage,
                search = search,
                saving = saving,
                panels = panels,
                ocr = ocr,
                reading = reading,
                onBack = onBack,
                onOpenTool = onOpenTool,
                onLiquid = openLiquid
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(if (!barsVisible && !search.open) Modifier.statusBarsPadding() else Modifier)
                    .then(
                        if (!barsVisible && !annotOpen && !reading.panelOpen && !search.open) {
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
                            if (annot.tool == AnnotTool.NONE && !search.open) {
                                barsVisible = !barsVisible
                            }
                        }
                    )
            ) {
                ViewerPages(
                    engine = engine,
                    listState = listState,
                    zoom = zoom,
                    display = display,
                    annot = annot,
                    search = search,
                    documentId = documentId,
                    ocrRevision = ocr.revision,
                    annotationRevision = saving.annotationRevision
                )

                ViewerPagePill(engine.pageCount, listState, zoom.height, drawing)

                ViewerSpokenText(
                    state = reading,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp)
                )

                if (search.open && (search.searching || search.searched)) {
                    SearchResults(
                        searching = search.searching,
                        hits = search.hits,
                        onPick = { position, _ ->
                            search.searched = false
                            search.goToHit(position)
                        }
                    )
                }
            }
            ViewerSearchNav(search)
            if (!annotOpen && !reading.panelOpen && !search.open) {
                ViewerBottomSection(
                    visible = barsVisible,
                    title = title,
                    search = search,
                    saving = saving,
                    panels = panels,
                    ocr = ocr,
                    reading = reading,
                    onAnnotTool = { tool ->
                        reading.stop()
                        if (tool == AnnotTool.SIGN) {
                            annot.signature = SignatureStore.load(context)
                        }
                        annotOpen = true
                        annot.select(tool)
                    },
                    onLiquid = openLiquid
                )
            }
            if (annotOpen) {
                AnnotToolbar(
                    annot = annot,
                    onSaveAnnotations = { saving.requestSave() },
                    onClose = {
                        annot.select(AnnotTool.NONE)
                        annotOpen = false
                    }
                )
            }

            if (reading.panelOpen) {
                reading.Panel(currentPage, title)
            }
        }

        panels.Sheets(display, listState, onFormSaved = { saving.markChanged() })
        ViewerSaveDialogs(saving)
        ViewerExport(
            open = panels.exportOpen,
            engine = engine,
            documentId = documentId,
            baseName = baseName,
            annot = annot,
            onDismiss = { panels.exportOpen = false }
        )

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

        ViewerOcrOverlay(ocr)
    }
}
