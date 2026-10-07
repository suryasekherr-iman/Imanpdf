package com.iman.pdf.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
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

@Composable
fun ViewerScreen(
    engine: PdfEngine,
    title: String,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val renderWidth = remember(configuration.screenWidthDp, density.density) {
        minOf((configuration.screenWidthDp * density.density * 1.5f).toInt(), 1800)
    }

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
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
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

            AnimatedVisibility(
                visible = pillVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        val travel = (containerHeightPx - pillHeightPx).coerceAtLeast(0f)
                        IntOffset(0, (progress * travel).roundToInt())
                    }
            ) {
                Box(
                    modifier = Modifier
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
                                    val page =
                                        (dragProgress * (engine.pageCount - 1)).roundToInt()
                                    scope.launch { listState.scrollToItem(page) }
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
    var bitmap by remember(index, renderWidth) { mutableStateOf<Bitmap?>(null) }
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
