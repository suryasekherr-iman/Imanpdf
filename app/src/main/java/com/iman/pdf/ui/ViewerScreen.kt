package com.iman.pdf.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.iman.pdf.pdf.PdfEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val MAX_ZOOM = 3f
private const val MAX_RENDER_WIDTH = 2200

@Composable
fun ViewerScreen(
    engine: PdfEngine,
    title: String,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val listState = rememberLazyListState()
    val horizontalState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = configuration.screenWidthDp * density.density

    var scale by remember { mutableFloatStateOf(1f) }
    var renderScale by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(scale) {
        delay(350)
        renderScale = scale
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
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
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
        }
    }
}

@Composable
private fun PdfPageItem(
    engine: PdfEngine,
    index: Int,
    renderWidth: Int
) {
    var bitmap by remember(index) { mutableStateOf<Bitmap?>(null) }
    var ratio by remember(index) { mutableFloatStateOf(1.414f) }

    LaunchedEffect(index, renderWidth) {
        ratio = engine.pageAspectRatio(index)
        bitmap = engine.renderPage(index, renderWidth)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f / ratio)
            .background(Color.White)
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "Page ${index + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }
    }
}
