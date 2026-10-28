package com.iman.pdf.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
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

    suspend fun apply(
        listState: LazyListState,
        horizontal: ScrollState,
        containerWidth: Float
    ) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        if (item != null) {
            val desiredTop = fy - fraction * item.size
            listState.scrollBy(item.offset - desiredTop)
        }
        val total = horizontal.maxValue + containerWidth
        horizontal.scrollTo((xFraction * total - fx).roundToInt())
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
    private val focus = ZoomFocus()

    fun begin(listState: LazyListState, horizontal: ScrollState, x: Float, y: Float) {
        originX = x / width.coerceAtLeast(1f)
        originY = y / height.coerceAtLeast(1f)
        focus.capture(listState, horizontal, width, x, y)
    }

    fun commit() {
        val finalScale = (scale * gesture).coerceIn(1f, MAX_ZOOM)
        gesture = 1f
        if (finalScale == scale) {
            focus.active = false
        } else {
            scale = finalScale
        }
    }

    suspend fun settle(listState: LazyListState, horizontal: ScrollState) {
        if (focus.active) {
            withFrameNanos { }
            focus.apply(listState, horizontal, width)
        }
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
