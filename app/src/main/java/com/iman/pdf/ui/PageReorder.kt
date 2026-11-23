package com.iman.pdf.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.localToRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

/**
 * Reorders thumbnail items by long-pressing and dragging one page over another.
 * The thumbnail grid remains the primary visual page picker; callers can retain
 * separate arrow/button controls for precise one-step movement.
 */
@Composable
internal fun <T> DraggablePageGrid(
    items: List<T>,
    key: (T) -> Any,
    onMove: (dragged: T, target: T) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (item: T, index: Int) -> Unit
) {
    val coordinates = remember { mutableStateMapOf<Any, LayoutCoordinates>() }
    var draggedKey by remember { mutableStateOf<Any?>(null) }
    val latestItems by rememberUpdatedState(items)
    val latestOnMove by rememberUpdatedState(onMove)

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
    ) {
        items(items, key = key) { item ->
            val itemKey = key(item)
            Box(
                modifier = Modifier
                    .onGloballyPositioned { coordinates[itemKey] = it }
                    .graphicsLayer { alpha = if (draggedKey == itemKey) 0.68f else 1f }
                    .pointerInput(itemKey) {
                        var lastTargetKey: Any? = null
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedKey = itemKey
                                lastTargetKey = null
                            },
                            onDragEnd = { draggedKey = null },
                            onDragCancel = { draggedKey = null }
                        ) { change, _ ->
                            change.consume()
                            val sourceCoordinates = coordinates[itemKey] ?: return@detectDragGesturesAfterLongPress
                            val pointerInRoot = sourceCoordinates.localToRoot(change.position)
                            val target = latestItems.firstOrNull { candidate ->
                                val candidateKey = key(candidate)
                                candidateKey != itemKey &&
                                    coordinates[candidateKey]?.boundsInRoot()?.contains(pointerInRoot) == true
                            }
                            if (target == null) {
                                lastTargetKey = null
                            } else {
                                val targetKey = key(target)
                                if (targetKey != lastTargetKey) {
                                    latestOnMove(item, target)
                                    lastTargetKey = targetKey
                                }
                            }
                        }
                    }
            ) {
                val index = latestItems.indexOfFirst { key(it) == itemKey }
                if (index >= 0) content(item, index)
            }
        }
    }
}

/** Moves one item to another valid index without mutating the caller's list. */
internal fun <T> moveItem(items: List<T>, fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) {
        return items
    }
    return items.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}
