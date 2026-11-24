package com.iman.pdf.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

const val PATTERN_MIN_DOTS = 4

/** 3x3 dot grid. Dots are numbered 1..9; onComplete gets the touched dots as a digit string, e.g. "1245". */
@Composable
fun PatternPad(
    modifier: Modifier = Modifier,
    error: Boolean = false,
    enabled: Boolean = true,
    onComplete: (String) -> Unit
) {
    var selected by remember { mutableStateOf<List<Int>>(emptyList()) }
    var finger by remember { mutableStateOf<Offset?>(null) }
    val latestComplete by rememberUpdatedState(onComplete)
    val idle = MaterialTheme.colorScheme.outline
    val active = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier.pointerInput(enabled) {
            if (!enabled) {
                return@pointerInput
            }
            val cell = size.width / 3f
            fun dotAt(p: Offset): Int {
                for (i in 0 until 9) {
                    val c = Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell)
                    if ((p - c).getDistance() <= cell * 0.3f) {
                        return i
                    }
                }
                return -1
            }
            fun touch(p: Offset) {
                finger = p
                val hit = dotAt(p)
                if (hit >= 0 && !selected.contains(hit)) {
                    selected = selected + hit
                }
            }
            detectDragGestures(
                onDragStart = { start ->
                    selected = emptyList()
                    touch(start)
                },
                onDrag = { change, _ ->
                    change.consume()
                    touch(change.position)
                },
                onDragEnd = {
                    val code = selected.joinToString("") { (it + 1).toString() }
                    selected = emptyList()
                    finger = null
                    if (code.isNotEmpty()) {
                        latestComplete(code)
                    }
                },
                onDragCancel = {
                    selected = emptyList()
                    finger = null
                }
            )
        }
    ) {
        val cell = size.width / 3f
        fun center(i: Int) = Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell)
        for (k in 1 until selected.size) {
            drawLine(
                color = active,
                start = center(selected[k - 1]),
                end = center(selected[k]),
                strokeWidth = 8f,
                cap = StrokeCap.Round
            )
        }
        val tail = finger
        if (selected.isNotEmpty() && tail != null) {
            drawLine(
                color = active,
                start = center(selected.last()),
                end = tail,
                strokeWidth = 8f,
                cap = StrokeCap.Round
            )
        }
        for (i in 0 until 9) {
            val on = selected.contains(i)
            drawCircle(color = if (on) active else idle, radius = cell * 0.08f, center = center(i))
            if (on) {
                drawCircle(
                    color = active,
                    radius = cell * 0.2f,
                    center = center(i),
                    style = Stroke(width = 4f)
                )
            }
        }
    }
}

@Composable
fun PatternSetupDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("Draw a pattern (at least 4 dots)") }
    var bad by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set pattern") },
        text = {
            Column {
                Text(
                    text = message,
                    color = if (bad) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                PatternPad(modifier = Modifier.size(240.dp), error = bad) { code ->
                    val saved = first
                    if (code.length < PATTERN_MIN_DOTS) {
                        bad = true
                        message = "Connect at least 4 dots"
                    } else if (saved == null) {
                        first = code
                        bad = false
                        message = "Draw the same pattern again"
                    } else if (saved == code) {
                        onSave(code)
                    } else {
                        first = null
                        bad = true
                        message = "Did not match. Start again"
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
