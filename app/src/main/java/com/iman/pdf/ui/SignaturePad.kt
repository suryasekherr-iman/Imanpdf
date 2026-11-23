package com.iman.pdf.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iman.pdf.annot.SignatureData

private enum class SignatureMode { DRAW, TYPE, IMPORT }

@Composable
fun SignaturePadDialog(
    onSave: (SignatureData) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(SignatureMode.DRAW) }
    var strokes by remember { mutableStateOf<List<List<Pair<Float, Float>>>>(emptyList()) }
    var current by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var typedName by remember { mutableStateOf("") }
    var signatureAspect by remember { mutableFloatStateOf(2f) }
    var importError by remember { mutableStateOf<String?>(null) }
    val shape = RoundedCornerShape(8.dp)
    val ink = Color(0xFF1A237E)

    fun acceptImage(uri: Uri?) {
        if (uri == null) return
        try {
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            } ?: throw IllegalArgumentException("Could not read this image.")
            val scaled = if (bitmap.width > 640 || bitmap.height > 320) {
                val factor = minOf(640f / bitmap.width, 320f / bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * factor).toInt().coerceAtLeast(1),
                    (bitmap.height * factor).toInt().coerceAtLeast(1),
                    true
                ).also { if (it !== bitmap) bitmap.recycle() }
            } else bitmap
            val converted = bitmapToSignature(scaled)
            scaled.recycle()
            if (converted == null) {
                importError = "No signature found. Choose a darker signature on a plain background."
            } else {
                strokes = converted.strokes
                signatureAspect = converted.aspect
                mode = SignatureMode.IMPORT
                importError = null
            }
        } catch (_: Exception) {
            importError = "Could not open this image. Try a PNG or JPG file."
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = ::acceptImage
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add your signature") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { mode = SignatureMode.DRAW; importError = null }) {
                        Text(if (mode == SignatureMode.DRAW) "• Draw" else "Draw")
                    }
                    TextButton(onClick = { mode = SignatureMode.TYPE; importError = null }) {
                        Text(if (mode == SignatureMode.TYPE) "• Type" else "Type")
                    }
                    TextButton(onClick = { imagePicker.launch("image/*") }) {
                        Text(if (mode == SignatureMode.IMPORT) "• Import" else "Import image")
                    }
                }
                when (mode) {
                    SignatureMode.DRAW -> {
                        SignatureCanvas(
                            strokes = strokes,
                            current = current,
                            onCurrent = { current = it },
                            onStroke = { stroke ->
                                if (stroke.isNotEmpty()) strokes = strokes + listOf(stroke)
                                current = emptyList()
                            }
                        )
                    }
                    SignatureMode.TYPE -> {
                        OutlinedTextField(
                            value = typedName,
                            onValueChange = { typedName = it; importError = null },
                            label = { Text("Type your name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(
                            onClick = {
                                val bitmap = Bitmap.createBitmap(640, 220, Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(bitmap)
                                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                    color = android.graphics.Color.rgb(26, 35, 126)
                                    textSize = 104f
                                    typeface = Typeface.create("cursive", Typeface.NORMAL)
                                }
                                while (paint.measureText(typedName.trim()) > 580f && paint.textSize > 32f) {
                                    paint.textSize -= 4f
                                }
                                canvas.drawText(
                                    typedName.trim(),
                                    24f,
                                    145f,
                                    paint
                                )
                                val converted = bitmapToSignature(bitmap)
                                bitmap.recycle()
                                if (converted == null) {
                                    importError = "Enter your name to create a signature."
                                } else {
                                    strokes = converted.strokes
                                    signatureAspect = converted.aspect
                                    importError = null
                                }
                            },
                            enabled = typedName.isNotBlank()
                        ) {
                            Text("Create signature preview")
                        }
                    }
                    SignatureMode.IMPORT -> {
                        Text(
                            if (strokes.isEmpty()) "Choose a PNG or JPG image." else "Image signature loaded. You can save it now.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                if (mode != SignatureMode.TYPE && strokes.isEmpty() && current.isEmpty()) {
                    Text(
                        text = if (mode == SignatureMode.IMPORT) "Select an image to import." else "Sign here",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                importError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(SignatureData(strokes, signatureAspect)) },
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
                        signatureAspect = 2f
                        importError = null
                    }
                ) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun SignatureCanvas(
    strokes: List<List<Pair<Float, Float>>>,
    current: List<Pair<Float, Float>>,
    onCurrent: (List<Pair<Float, Float>>) -> Unit,
    onStroke: (List<Pair<Float, Float>>) -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    val ink = Color(0xFF1A237E)
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
                var points = emptyList<Pair<Float, Float>>()
                detectDragGestures(
                    onDragStart = { start ->
                        points = listOf(start.x / w to start.y / h)
                        onCurrent(points)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        points = points + (change.position.x / w to change.position.y / h)
                        onCurrent(points)
                    },
                    onDragEnd = { onStroke(points); points = emptyList() },
                    onDragCancel = { points = emptyList(); onCurrent(emptyList()) }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            (strokes + listOf(current)).forEach { stroke ->
                if (stroke.size == 1) {
                    drawCircle(ink, radius = 3f, center = androidx.compose.ui.geometry.Offset(
                        stroke[0].first * size.width, stroke[0].second * size.height
                    ))
                } else if (stroke.size > 1) {
                    val path = Path().apply {
                        moveTo(stroke[0].first * size.width, stroke[0].second * size.height)
                        for (i in 1 until stroke.size) {
                            lineTo(stroke[i].first * size.width, stroke[i].second * size.height)
                        }
                    }
                    drawPath(
                        path,
                        ink,
                        style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
    }
}

/** Converts a dark/transparent signature image into compact normalized ink strokes. */
private fun bitmapToSignature(bitmap: Bitmap): SignatureData? {
    val width = bitmap.width.coerceAtMost(640)
    val height = bitmap.height.coerceAtMost(320)
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, width, height)

    fun isInk(x: Int, y: Int): Boolean {
        val pixel = pixels[y * width + x]
        val alpha = (pixel ushr 24) and 255
        if (alpha < 32) return false
        val red = (pixel ushr 16) and 255
        val green = (pixel ushr 8) and 255
        val blue = pixel and 255
        return (red * 299 + green * 587 + blue * 114) / 1000 < 238
    }

    var left = width
    var top = height
    var right = -1
    var bottom = -1
    for (y in 0 until height step 2) {
        for (x in 0 until width step 2) {
            if (isInk(x, y)) {
                left = minOf(left, x); top = minOf(top, y)
                right = maxOf(right, x); bottom = maxOf(bottom, y)
            }
        }
    }
    if (right <= left || bottom <= top) return null

    val contentWidth = (right - left + 1).toFloat()
    val contentHeight = (bottom - top + 1).toFloat()
    val rowStep = maxOf(1, contentHeight.toInt() / 180)
    val columnStep = maxOf(1, contentWidth.toInt() / 500)
    val result = ArrayList<List<Pair<Float, Float>>>()
    var y = top
    while (y <= bottom) {
        var x = left
        while (x <= right) {
            while (x <= right && !isInk(x, y)) x += columnStep
            if (x > right) break
            val start = x
            while (x <= right && isInk(x, y)) x += columnStep
            val end = (x - columnStep).coerceAtLeast(start)
            val normalizedY = ((y - top) / contentHeight).coerceIn(0f, 1f)
            result.add(
                listOf(
                    ((start - left) / contentWidth).coerceIn(0f, 1f) to normalizedY,
                    ((end - left) / contentWidth).coerceIn(0f, 1f) to normalizedY
                )
            )
        }
        y += rowStep
    }
    return result.takeIf { it.isNotEmpty() }?.let {
        SignatureData(it, contentWidth / contentHeight)
    }
}
