package com.iman.pdf.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.iman.pdf.annot.AnnotController
import com.iman.pdf.annot.AnnotTool
import com.iman.pdf.annot.HighlightMark
import com.iman.pdf.annot.MarkPainter
import com.iman.pdf.annot.MarkRect
import com.iman.pdf.annot.StrokeMark
import com.iman.pdf.pdf.TextGlyph
import com.iman.pdf.pdf.TextSelection
import kotlinx.coroutines.launch

private const val MIN_HIGHLIGHT_HEIGHT = 0.016f

@Composable
fun AnnotationLayer(
    page: Int,
    annot: AnnotController,
    modifier: Modifier = Modifier,
    loadText: suspend (Int) -> List<List<TextGlyph>> = { emptyList() }
) {
    val marks = annot.marks.filter { it.page == page }
    val live = annot.live?.takeIf { it.page == page }
    val tool = annot.tool

    // Text layout of this page, loaded only while a text marking tool is active.
    val textTool = tool == AnnotTool.HIGHLIGHT || tool == AnnotTool.UNDERLINE ||
        tool == AnnotTool.STRIKE
    var textLines by remember(page) { mutableStateOf<List<List<TextGlyph>>>(emptyList()) }
    LaunchedEffect(page, textTool) {
        if (textTool) textLines = loadText(page)
    }
    val currentLines by rememberUpdatedState(textLines)
    val currentLoadText by rememberUpdatedState(loadText)
    val scope = rememberCoroutineScope()

    val gestureModifier = if (tool == AnnotTool.NONE) {
        Modifier
    } else {
        Modifier.pointerInput(tool, annot.color, annot.strokeWidth, annot.textSize) {
            val w = size.width.toFloat().coerceAtLeast(1f)
            val h = size.height.toFloat().coerceAtLeast(1f)
            when (tool) {
                AnnotTool.DRAW -> {
                    val points = ArrayList<Pair<Float, Float>>()
                    detectDragGestures(
                        onDragStart = { start ->
                            points.clear()
                            points.add(Pair(start.x / w, start.y / h))
                            annot.live = StrokeMark(
                                page,
                                annot.color,
                                annot.strokeWidth,
                                points.toList()
                            )
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            points.add(Pair(change.position.x / w, change.position.y / h))
                            annot.live = StrokeMark(
                                page,
                                annot.color,
                                annot.strokeWidth,
                                points.toList()
                            )
                        },
                        onDragEnd = {
                            annot.live?.let { annot.add(it) }
                            annot.live = null
                        },
                        onDragCancel = { annot.live = null }
                    )
                }

                AnnotTool.HIGHLIGHT, AnnotTool.UNDERLINE, AnnotTool.STRIKE -> {
                    val kind = when (tool) {
                        AnnotTool.UNDERLINE -> 1
                        AnnotTool.STRIKE -> 2
                        else -> 0
                    }
                    fun markFor(
                        start: Offset,
                        end: Offset,
                        lines: List<List<TextGlyph>>
                    ): HighlightMark {
                        // Read the size now: it changes once the real page ratio is known.
                        val pageW = size.width.toFloat().coerceAtLeast(1f)
                        val pageH = size.height.toFloat().coerceAtLeast(1f)
                        return textMark(
                            page,
                            annot.color,
                            kind,
                            lines,
                            start.x / pageW,
                            start.y / pageH,
                            end.x / pageW,
                            end.y / pageH
                        )
                    }
                    // Starts at the finger-down point, so the first word is not skipped.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val first = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                            change.consume()
                        } ?: return@awaitEachGesture
                        var end = first.position
                        annot.live = markFor(down.position, end, currentLines)
                        val completed = drag(first.id) { change ->
                            change.consume()
                            end = change.position
                            annot.live = markFor(down.position, end, currentLines)
                        }
                        val finished = annot.live as? HighlightMark
                        if (!completed || finished == null) {
                            annot.live = null
                        } else if (currentLines.isNotEmpty()) {
                            annot.add(finished)
                            annot.live = null
                        } else {
                            // The text was not loaded yet (or the page is a scan).
                            val start = down.position
                            val last = end
                            scope.launch {
                                annot.add(markFor(start, last, currentLoadText(page)))
                                annot.live = null
                            }
                        }
                    }
                }

                else -> {
                    detectTapGestures(
                        onTap = { position ->
                            annot.placeAt(page, position.x / w, position.y / h)
                        }
                    )
                }
            }
        }
    }

    Box(modifier = modifier.then(gestureModifier)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawIntoCanvas { canvas ->
                MarkPainter.draw(canvas.nativeCanvas, size.width, size.height, marks, live)
            }
        }
    }
}

/**
 * A highlight, underline or strikethrough for a drag from (startX, startY) to (endX, endY).
 * It follows the text; on pages without text under the finger it keeps the dragged box.
 */
private fun textMark(
    page: Int,
    color: Int,
    kind: Int,
    lines: List<List<TextGlyph>>,
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float
): HighlightMark {
    val areas = TextSelection.select(lines, startX, startY, endX, endY).map {
        MarkRect(it.left, it.top, it.right, it.bottom)
    }
    if (areas.isNotEmpty()) {
        return HighlightMark(
            page,
            color,
            areas.minOf { it.left },
            areas.minOf { it.top },
            areas.maxOf { it.right },
            areas.maxOf { it.bottom },
            kind,
            areas
        )
    }
    val left = minOf(startX, endX).coerceIn(0f, 1f)
    val right = maxOf(startX, endX).coerceIn(0f, 1f)
    var top = minOf(startY, endY).coerceIn(0f, 1f)
    var bottom = maxOf(startY, endY).coerceIn(0f, 1f)
    if (kind == 0 && bottom - top < MIN_HIGHLIGHT_HEIGHT) {
        // Scanned page: keep the dragged box, tall enough to be visible.
        top = (top - MIN_HIGHLIGHT_HEIGHT / 2f).coerceAtLeast(0f)
        bottom = (bottom + MIN_HIGHLIGHT_HEIGHT / 2f).coerceAtMost(1f)
    }
    return HighlightMark(page, color, left, top, right, bottom, kind)
}
