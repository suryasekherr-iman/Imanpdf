package com.iman.pdf.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

const val MAX_ZOOM = 3f

class ZoomFocus {
    var active = false
    private var index = 0
    private var fraction = 0f
    private var xFraction = 0f
    private var fx = 0f
    private var fy = 0f
    private var panX = 0f
    private var panY = 0f

    fun capture(
        listState: LazyListState,
        horizontal: ScrollState,
        containerWidth: Float,
        x: Float,
        y: Float
    ) {
        val items = listState.layoutInfo.visibleItemsInfo
        val hit = items.firstOrNull { y >= it.offset && y <= it.offset + it.size }
            ?: items.firstOrNull()
        if (hit == null || hit.size <= 0) {
            active = false
            return
        }
        index = hit.index
        fraction = (y - hit.offset) / hit.size
        val total = (horizontal.maxValue + containerWidth).coerceAtLeast(1f)
        xFraction = (horizontal.value + x) / total
        fx = x
        fy = y
        active = true
    }

    fun updatePan(dx: Float, dy: Float) {
        panX = dx
        panY = dy
    }

    suspend fun apply(
        listState: LazyListState,
        horizontal: ScrollState,
        containerWidth: Float
    ) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        if (item != null) {
            val desiredTop = fy + panY - fraction * item.size
            listState.scrollToItem(index, (-desiredTop).roundToInt())
        }
        val total = horizontal.maxValue + containerWidth
        horizontal.scrollTo((xFraction * total - (fx + panX)).roundToInt())
        active = false
    }
}

class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var gesture by mutableFloatStateOf(1f)
    var originX by mutableFloatStateOf(0.5f)
    var originY by mutableFloatStateOf(0.5f)
    var width by mutableFloatStateOf(1f)
    var height by mutableFloatStateOf(0f)
    var translationX by mutableFloatStateOf(0f)
    var translationY by mutableFloatStateOf(0f)
    private val focus = ZoomFocus()
    private var startX = 0f
    private var startY = 0f

    fun begin(listState: LazyListState, horizontal: ScrollState, x: Float, y: Float) {
        startX = x
        startY = y
        translationX = 0f
        translationY = 0f
        originX = x / width.coerceAtLeast(1f)
        originY = y / height.coerceAtLeast(1f)
        focus.capture(listState, horizontal, width, x, y)
    }

    fun updateCentroid(x: Float, y: Float) {
        translationX = x - startX
        translationY = y - startY
        focus.updatePan(translationX, translationY)
    }

    fun commit() {
        val finalScale = (scale * gesture).coerceIn(1f, MAX_ZOOM)
        if (finalScale == scale) {
            gesture = 1f
            translationX = 0f
            translationY = 0f
            focus.active = false
        } else {
            // Keep the live transform until the scaled LazyColumn has remeasured.
            // Removing it immediately causes the visible jump at finger release.
            scale = finalScale
        }
    }

    suspend fun settle(listState: LazyListState, horizontal: ScrollState) {
        if (focus.active) {
            // One frame lets the scale state reach composition; the next lets
            // LazyColumn publish its new item sizes and horizontal scroll range.
            withFrameNanos { }
            withFrameNanos { }
            focus.apply(listState, horizontal, width)
        }
        gesture = 1f
        translationX = 0f
        translationY = 0f
    }
}

fun Modifier.zoomGestures(
    zoom: ZoomState,
    listState: LazyListState,
    horizontal: ScrollState,
    scope: CoroutineScope,
    doubleTapEnabled: () -> Boolean
): Modifier = this
    .clipToBounds()
    .onSizeChanged {
        zoom.height = it.height.toFloat()
        zoom.width = it.width.toFloat()
    }
    .pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var pinching = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size >= 2) {
                    if (!pinching) {
                        pinching = true
                        val center = event.calculateCentroid(useCurrent = true)
                        zoom.begin(listState, horizontal, center.x, center.y)
                    }
                    val center = event.calculateCentroid(useCurrent = true)
                    zoom.updateCentroid(center.x, center.y)
                    val zoomChange = event.calculateZoom()
                    if (zoomChange != 1f) {
                        zoom.gesture = (zoom.gesture * zoomChange)
                            .coerceIn(1f / zoom.scale, MAX_ZOOM / zoom.scale)
                    }
                    event.changes.forEach { change ->
                        if (change.positionChanged()) {
                            change.consume()
                        }
                    }
                }
            } while (event.changes.any { it.pressed })
            if (pinching) {
                zoom.commit()
            }
        }
    }
    .pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = { position ->
                if (doubleTapEnabled()) {
                    val target = if (zoom.scale > 1.05f) 1f else 2f
                    zoom.begin(listState, horizontal, position.x, position.y)
                    scope.launch {
                        animate(1f, target / zoom.scale, animationSpec = tween(250)) { value, _ ->
                            zoom.gesture = value
                        }
                        zoom.commit()
                    }
                }
            }
        )
    }
