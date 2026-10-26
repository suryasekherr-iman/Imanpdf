package com.iman.pdf.annot

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AnnotTool { NONE, HIGHLIGHT, DRAW, TEXT, NOTE, SIGN, CHECK, CROSS, DATE }

class SignatureData(
    val strokes: List<List<Pair<Float, Float>>>,
    val aspect: Float
)

sealed class Mark {
    abstract val page: Int
}

data class StrokeMark(
    override val page: Int,
    val color: Int,
    val width: Float,
    val points: List<Pair<Float, Float>>
) : Mark()

data class HighlightMark(
    override val page: Int,
    val color: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) : Mark()

data class TextMark(
    override val page: Int,
    val x: Float,
    val y: Float,
    val text: String,
    val color: Int,
    val size: Float,
    val boxed: Boolean
) : Mark()

data class SignMark(
    override val page: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val color: Int,
    val signature: SignatureData
) : Mark()

data class PendingText(
    val page: Int,
    val x: Float,
    val y: Float
)

class AnnotController {
    var tool by mutableStateOf(AnnotTool.NONE)
    var color by mutableIntStateOf(0xFF1E88E5.toInt())
    var strokeWidth by mutableFloatStateOf(0.004f)
    var textSize by mutableFloatStateOf(0.03f)
    var live by mutableStateOf<Mark?>(null)
    var pendingText by mutableStateOf<PendingText?>(null)
    var signature by mutableStateOf<SignatureData?>(null)
    var needSignature by mutableStateOf(false)
    val marks = mutableStateListOf<Mark>()

    fun select(newTool: AnnotTool) {
        tool = newTool
        live = null
        when (newTool) {
            AnnotTool.HIGHLIGHT -> color = 0xFFFFEB3B.toInt()
            AnnotTool.DRAW -> color = 0xFF1E88E5.toInt()
            AnnotTool.TEXT -> color = 0xFF000000.toInt()
            AnnotTool.NOTE -> color = 0xFF000000.toInt()
            AnnotTool.SIGN -> color = 0xFF1A237E.toInt()
            AnnotTool.CHECK -> color = 0xFF000000.toInt()
            AnnotTool.CROSS -> color = 0xFF000000.toInt()
            AnnotTool.DATE -> color = 0xFF000000.toInt()
            AnnotTool.NONE -> {
            }
        }
        if (newTool == AnnotTool.SIGN && signature == null) {
            needSignature = true
        }
    }

    fun placeAt(page: Int, x: Float, y: Float) {
        when (tool) {
            AnnotTool.CHECK -> add(TextMark(page, x - 0.025f, y - 0.025f, "✓", color, 0.05f, false))
            AnnotTool.CROSS -> add(TextMark(page, x - 0.025f, y - 0.025f, "✗", color, 0.05f, false))
            AnnotTool.DATE -> {
                val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                add(TextMark(page, x, y, today, color, textSize, false))
            }
            AnnotTool.SIGN -> {
                val sig = signature
                if (sig == null) {
                    needSignature = true
                } else {
                    add(
                        SignMark(
                            page,
                            (x - 0.15f).coerceAtLeast(0f),
                            (y - 0.04f).coerceAtLeast(0f),
                            0.3f,
                            color,
                            sig
                        )
                    )
                }
            }
            else -> pendingText = PendingText(page, x, y)
        }
    }

    fun add(mark: Mark) {
        marks.add(mark)
    }

    fun undo() {
        if (marks.isNotEmpty()) {
            marks.removeAt(marks.size - 1)
        }
    }

    fun clearAll() {
        marks.clear()
        live = null
        pendingText = null
    }
}

object MarkPainter {

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        marks: List<Mark>,
        live: Mark? = null
    ) {
        marks.forEach { drawMark(canvas, width, height, it) }
        if (live != null) {
            drawMark(canvas, width, height, live)
        }
    }

    private fun drawMark(canvas: Canvas, width: Float, height: Float, mark: Mark) {
        when (mark) {
            is StrokeMark -> drawStroke(canvas, width, height, mark)
            is HighlightMark -> drawHighlight(canvas, width, height, mark)
            is TextMark -> drawText(canvas, width, height, mark)
            is SignMark -> drawSign(canvas, width, height, mark)
        }
    }

    private fun drawStroke(canvas: Canvas, width: Float, height: Float, mark: StrokeMark) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = mark.color
        paint.strokeWidth = mark.width * width
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        val points = mark.points
        if (points.size == 1) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(
                points[0].first * width,
                points[0].second * height,
                paint.strokeWidth / 2f,
                paint
            )
        } else if (points.size > 1) {
            paint.style = Paint.Style.STROKE
            val path = Path()
            path.moveTo(points[0].first * width, points[0].second * height)
            for (i in 1 until points.size) {
                path.lineTo(points[i].first * width, points[i].second * height)
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun drawHighlight(canvas: Canvas, width: Float, height: Float, mark: HighlightMark) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = mark.color
        paint.alpha = 100
        paint.style = Paint.Style.FILL
        canvas.drawRect(
            mark.left * width,
            mark.top * height,
            mark.right * width,
            mark.bottom * height,
            paint
        )
    }

    private fun drawText(canvas: Canvas, width: Float, height: Float, mark: TextMark) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = mark.size * width
        paint.color = mark.color
        val lines = mark.text.split("\n")
        val metrics = paint.fontMetrics
        val lineHeight = metrics.descent - metrics.ascent
        val left = mark.x * width
        val top = mark.y * height

        if (mark.boxed) {
            var widest = 0f
            lines.forEach { widest = maxOf(widest, paint.measureText(it)) }
            val pad = paint.textSize * 0.4f
            val background = Paint(Paint.ANTI_ALIAS_FLAG)
            background.color = 0xFFFFF59D.toInt()
            canvas.drawRoundRect(
                left - pad,
                top - pad,
                left + widest + pad,
                top + lineHeight * lines.size + pad,
                pad,
                pad,
                background
            )
        }

        lines.forEachIndexed { index, line ->
            canvas.drawText(line, left, top - metrics.ascent + index * lineHeight, paint)
        }
    }

    private fun drawSign(canvas: Canvas, width: Float, height: Float, mark: SignMark) {
        val boxWidth = mark.width * width
        val boxHeight = boxWidth / mark.signature.aspect
        val left = mark.x * width
        val top = mark.y * height
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = mark.color
        paint.strokeWidth = boxWidth * 0.015f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        mark.signature.strokes.forEach { stroke ->
            if (stroke.size == 1) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(
                    left + stroke[0].first * boxWidth,
                    top + stroke[0].second * boxHeight,
                    paint.strokeWidth / 2f,
                    paint
                )
            } else if (stroke.size > 1) {
                paint.style = Paint.Style.STROKE
                val path = Path()
                path.moveTo(
                    left + stroke[0].first * boxWidth,
                    top + stroke[0].second * boxHeight
                )
                for (i in 1 until stroke.size) {
                    path.lineTo(
                        left + stroke[i].first * boxWidth,
                        top + stroke[i].second * boxHeight
                    )
                }
                canvas.drawPath(path, paint)
            }
        }
    }
}
