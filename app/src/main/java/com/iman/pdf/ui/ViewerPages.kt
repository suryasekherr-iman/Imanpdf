package com.iman.pdf.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.iman.pdf.annot.AnnotController
import com.iman.pdf.pdf.LastPage
import com.iman.pdf.pdf.PdfEngine
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val MAX_RENDER_WIDTH = 2400

/** The zoomable list of pages. */
@Composable
internal fun ViewerPages(
    engine: PdfEngine,
    listState: LazyListState,
    zoom: ZoomState,
    display: ViewerDisplay,
    annot: AnnotController,
    search: ViewerSearch,
    documentId: String,
    ocrRevision: Int,
    annotationRevision: Int
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = configuration.screenWidthDp * density.density
    val scale = zoom.scale
    var renderScale by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(scale) {
        delay(300)
        renderScale = when {
            scale > 2f -> 3f
            scale > 1.05f -> 2f
            else -> 1f
        }
    }
    val renderWidth = minOf((screenWidthPx * 1.2f * renderScale).toInt(), MAX_RENDER_WIDTH)
    val drawing = annot.tool != com.iman.pdf.annot.AnnotTool.NONE

    val snapFling = androidx.compose.foundation.gestures.snapping
        .rememberSnapFlingBehavior(listState)
    LazyColumn(
        state = listState,
        flingBehavior = if (display.pageSnap) snapFling
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
                nightMode = display.nightMode,
                sepiaMode = display.sepiaMode,
                searchHits = search.hits.filter { it.page == index },
                activeSearchHit = search.hits.getOrNull(search.activeHit)?.takeIf { it.page == index },
                documentId = documentId,
                ocrRevision = ocrRevision,
                annotationRevision = annotationRevision
            )
        }
    }
}

/** Draggable page number shown while scrolling. */
@Composable
internal fun BoxScope.ViewerPagePill(
    pageCount: Int,
    listState: LazyListState,
    containerHeightPx: Float,
    drawing: Boolean
) {
    var dragging by remember { mutableStateOf(false) }
    var pillVisible by remember { mutableStateOf(true) }
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val progress by remember {
        derivedStateOf {
            if (pageCount <= 1) {
                0f
            } else {
                val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                val fraction = if (first != null && first.size > 0) {
                    (-first.offset).toFloat() / first.size.toFloat()
                } else {
                    0f
                }
                ((listState.firstVisibleItemIndex + fraction.coerceIn(0f, 1f)) /
                    (pageCount - 1).toFloat()).coerceIn(0f, 1f)
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
    if (pillAlpha > 0f) {
        PagePill(
            pageNumber = currentPage + 1,
            pageCount = pageCount,
            progress = progress,
            containerHeightPx = containerHeightPx,
            alpha = pillAlpha,
            listState = listState,
            onDragging = { dragging = it }
        )
    }
}

/** Remembers the reading position and zoom of this document. */
@Composable
internal fun ViewerPositionMemory(
    documentId: String,
    pageCount: Int,
    listState: LazyListState,
    zoom: ZoomState
) {
    val context = LocalContext.current
    val scale = zoom.scale
    LaunchedEffect(scale) {
        LastPage.saveZoom(context, documentId, scale)
    }
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(currentPage, listState.firstVisibleItemScrollOffset) {
        delay(250)
        LastPage.savePosition(
            context,
            documentId,
            pageCount,
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
                pageCount,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                0
            )
            LastPage.saveZoom(context, documentId, zoom.scale)
        }
    }
}
