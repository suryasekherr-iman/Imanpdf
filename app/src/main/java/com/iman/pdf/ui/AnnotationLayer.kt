package com.iman.pdf.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.iman.pdf.annot.AnnotController
import com.iman.pdf.annot.AnnotTool
import com.iman.pdf.annot.HighlightMark
import com.iman.pdf.annot.MarkPainter
import com.iman.pdf.annot.StrokeMark

@Composable
fun AnnotationLayer(
    page: Int,
    annot: AnnotController,
    modifier: Modifier = Modifier
) {
    val marks = annot.marks.filter { it.page == page }
    val live = annot.live?.takeIf { it.page == page }
    val tool = annot.tool

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
                            val finished = annot.live
                            if (finished != null) {
                                annot.add(finished)
                            }
                            annot.live = null
                        },
                        onDragCancel = { annot.live = null }
                    )
                }

                AnnotTool.HIGHLIGHT -> {
                    var startX = 0f
                    var startY = 0f
                    detectDragGestures(
                        onDragStart = { start ->
                            startX = start.x / w
                            startY = start.y / h
                            annot.live = HighlightMark(
                                page,
                                annot.color,
                                startX,
                                startY,
                                startX,
                                startY
                            )
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val x = (change.position.x / w).coerceIn(0f, 1f)
                            val y = (change.position.y / h).coerceIn(0f, 1f)
                            annot.live = HighlightMark(
                                page,
                                annot.color,
                                minOf(startX, x),
                                minOf(startY, y),
                                maxOf(startX, x),
                                maxOf(startY, y)
                            )
                        },
                        onDragEnd = {
                            val finished = annot.live
                            if (finished != null) {
                                annot.add(finished)
                            }
                            annot.live = null
                        },
                        onDragCancel = { annot.live = null }
                    )
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
