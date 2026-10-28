package com.iman.pdf.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.iman.pdf.annot.SignatureData
import androidx.compose.ui.unit.dp

@Composable
fun SignaturePadDialog(
    onSave: (SignatureData) -> Unit,
    onDismiss: () -> Unit
) {
    var strokes by remember { mutableStateOf<List<List<Pair<Float, Float>>>>(emptyList()) }
    var current by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    val shape = RoundedCornerShape(8.dp)
    val ink = Color(0xFF1A237E)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Draw your signature") },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f)
                        .clip(shape)
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                        .pointerInput(Unit) {
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            detectDragGestures(
                                onDragStart = { start ->
                                    current = listOf(Pair(start.x / w, start.y / h))
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    current = current +
                                        Pair(change.position.x / w, change.position.y / h)
                                },
                                onDragEnd = {
                                    if (current.isNotEmpty()) {
                                        strokes = strokes + listOf(current)
                                    }
                                    current = emptyList()
                                },
                                onDragCancel = { current = emptyList() }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val all = strokes + listOf(current)
                        all.forEach { stroke ->
                            if (stroke.size > 1) {
                                val path = Path()
                                path.moveTo(stroke[0].first * size.width, stroke[0].second * size.height)
                                for (i in 1 until stroke.size) {
                                    path.lineTo(
                                        stroke[i].first * size.width,
                                        stroke[i].second * size.height
                                    )
                                }
                                drawPath(
                                    path = path,
                                    color = ink,
                                    style = Stroke(
                                        width = 6f,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }
                    if (strokes.isEmpty() && current.isEmpty()) {
                        Text(
                            text = "Sign here",
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.Gray
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(SignatureData(strokes, 2f)) },
                enabled = strokes.isNotEmpty()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        strokes = emptyList()
                        current = emptyList()
                    }
                ) {
                    Text("Clear")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
