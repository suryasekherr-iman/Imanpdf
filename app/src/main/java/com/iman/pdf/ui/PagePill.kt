package com.iman.pdf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun BoxScope.PagePill(
    pageNumber: Int,
    pageCount: Int,
    progress: Float,
    containerHeightPx: Float,
    alpha: Float,
    listState: LazyListState,
    onDragging: (Boolean) -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val pillHeight = 40.dp
    val pillHeightPx = with(density) { pillHeight.toPx() }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val latestProgress by rememberUpdatedState(progress)
    val latestHeight by rememberUpdatedState(containerHeightPx)

    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset {
                val travel = (containerHeightPx - pillHeightPx).coerceAtLeast(0f)
                IntOffset(0, (progress * travel).roundToInt())
            }
            .alpha(alpha)
            .height(pillHeight)
            .background(
                MaterialTheme.colorScheme.inverseSurface,
                RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
            )
            .pointerInput(pageCount) {
                detectVerticalDragGestures(
                    onDragStart = {
                        onDragging(true)
                        dragProgress = latestProgress
                    },
                    onDragEnd = { onDragging(false) },
                    onDragCancel = { onDragging(false) },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val travelPx = (latestHeight - pillHeightPx).coerceAtLeast(1f)
                        dragProgress = (dragProgress + dragAmount / travelPx).coerceIn(0f, 1f)
                        val position = dragProgress * (pageCount - 1)
                        val index = position.toInt().coerceIn(0, pageCount - 1)
                        val fraction = position - index
                        val size = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull()?.size ?: 0
                        scope.launch {
                            listState.scrollToItem(index, (fraction * size).roundToInt())
                        }
                    }
                )
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$pageNumber",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.inverseOnSurface
        )
    }
}
