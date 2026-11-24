package com.iman.pdf.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

const val MAX_ZOOM = 3f

class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var offsetX by mutableFloatStateOf(0f)
    var width by mutableFloatStateOf(1f)
    var height by mutableFloatStateOf(0f)

    private fun clampX(x: Float): Float = x.coerceIn(width * (1f - scale), 0f)

    fun zoomAround(newScale: Float, fx: Float, fy: Float, list: LazyListState) {
        val old = scale
        val target = newScale.coerceIn(1f, MAX_ZOOM)
        if (target == old) return
        list.dispatchRawDelta(fy / old - fy / target)
        val x = fx - (fx - offsetX) * target / old
        scale = target
        offsetX = clampX(x)
    }

    fun pan(dx: Float, dy: Float, list: LazyListState) {
        offsetX = clampX(offsetX + dx)
        if (dy != 0f) list.dispatchRawDelta(-dy / scale)
    }
}

fun Modifier.zoomGestures(
    zoom: ZoomState,
    listState: LazyListState,
    scope: CoroutineScope,
    panEnabled: () -> Boolean,
    doubleTapEnabled: () -> Boolean
): Modifier = this
    .clipToBounds()
    .onSizeChanged {
        zoom.height = it.height.toFloat()
        zoom.width = it.width.toFloat()
    }
    fun Modifier.zoomGestures(
    zoom: ZoomState,
    listState: LazyListState,
    scope: CoroutineScope,
    panEnabled: () -> Boolean,
    doubleTapEnabled: () -> Boolean,
    onTap: () -> Unit = {}          // ← naya parameter
): Modifier = this
    // ... existing code ...
    .pointerInput(Unit) {
        detectTapGestures(
            onTap = { onTap() },     // ← single tap
            onDoubleTap = { p ->
                if (doubleTapEnabled()) {
                    // existing double-tap zoom code
                }
            }
        )
    }
    .pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.count { it.pressed }
                if (pressed >= 2) {
                    val zoomChange = event.calculateZoom()
                    val pan = event.calculatePan()
                    val c = event.calculateCentroid(useCurrent = true)
                    if (zoomChange != 1f) {
                        zoom.zoomAround(zoom.scale * zoomChange, c.x, c.y, listState)
                    }
                    zoom.pan(pan.x, pan.y, listState)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                } else if (pressed == 1 && zoom.scale > 1f && panEnabled()) {
                    zoom.pan(event.calculatePan().x, 0f, listState)
                }
            } while (event.changes.any { it.pressed })
        }
    }
    .pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = { p ->
                if (doubleTapEnabled()) {
                    val from = zoom.scale
                    val to = if (from > 1.05f) 1f else 2f
                    scope.launch {
                        animate(from, to, animationSpec = tween(250)) { v, _ ->
                            zoom.zoomAround(v, p.x, p.y, listState)
                        }
                    }
                }
            }
        )
    }
