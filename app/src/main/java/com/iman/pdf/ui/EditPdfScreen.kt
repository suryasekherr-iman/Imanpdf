package com.iman.pdf.ui

import android.content.Intent
import android.graphics.RectF
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iman.pdf.R
import com.iman.pdf.pdf.PdfActions
import com.iman.pdf.pdf.PdfContentReplacement
import com.iman.pdf.pdf.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

@Composable
fun EditPdfScreen(onBack: () -> Unit, onOpen: (Uri) -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pdf by remember { mutableStateOf<Uri?>(null) }
    var picture by remember { mutableStateOf<Uri?>(null) }
    var name by remember { mutableStateOf("Document") }
    var text by remember { mutableStateOf("") }
    var page by remember { mutableStateOf("1") }
    var fontSize by remember { mutableFloatStateOf(18f) }
    var busy by remember { mutableStateOf(false) }
    var resultFile by remember { mutableStateOf<File?>(null) }
    var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var pageBounds by remember { mutableStateOf<RectF?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    var selection by remember { mutableStateOf<RectF?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var confirmOverwrite by remember { mutableStateOf(false) }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pdf = uri
            selection = null
            resultFile = null
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val displayName = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
                }
            }.getOrNull()
            name = (displayName ?: "Document.pdf").removeSuffix(".pdf").removeSuffix(".PDF").ifBlank { "Document" }
            page = "1"
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { picture = it }

    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { target ->
        val file = resultFile
        if (target != null && file != null) scope.launch {
            busy = true
            val saved = withContext(Dispatchers.IO) { PdfActions.saveCopy(context, file, target) }
            busy = false
            if (saved) {
                Toast.makeText(context, R.string.edit_pdf_saved, Toast.LENGTH_SHORT).show()
                onOpen(target)
            } else Toast.makeText(context, R.string.edit_pdf_save_failed, Toast.LENGTH_LONG).show()
        }
    }

    fun saveToOriginal() {
        val source = pdf
        val edited = resultFile
        if (source == null || edited == null) return
        scope.launch {
            busy = true
            val saved = withContext(Dispatchers.IO) { PdfActions.saveCopyToUri(context, edited, source) }
            busy = false
            if (saved) {
                Toast.makeText(context, "Changes permanently saved to the original PDF.", Toast.LENGTH_LONG).show()
                onOpen(source)
            } else {
                Toast.makeText(context, "Could not overwrite this PDF. Use Save as new PDF instead.", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(pdf, page) {
        preview?.recycle()
        preview = null
        pageBounds = null
        previewError = null
        selection = null
        val source = pdf ?: return@LaunchedEffect
        val index = (page.toIntOrNull() ?: 1) - 1
        if (index < 0) { previewError = "Enter a valid page number."; return@LaunchedEffect }
        busy = true
        val loaded = withContext(Dispatchers.IO) {
            val engine = PdfEngine.open(context, source)
            if (engine == null) null else try {
                if (index !in 0 until engine.pageCount) null
                else Triple(engine.renderPage(index, 1200), engine.pageBounds(index), engine.pageCount)
            } catch (_: Exception) { null } finally { engine.close() }
        }
        val bounds = loaded?.second
        if (loaded == null || bounds == null || bounds.width() <= 0f || bounds.height() <= 0f) {
            previewError = "Could not load this PDF page. Check the page number or PDF file."
        } else {
            preview = loaded.first
            pageBounds = bounds
            pageCount = loaded.third
        }
        busy = false
    }

    DisposableEffect(Unit) { onDispose { preview?.recycle() } }

    if (confirmOverwrite) {
        AlertDialog(
            onDismissRequest = { confirmOverwrite = false },
            title = { Text("Permanently replace original PDF?") },
            text = { Text("This will overwrite the selected PDF. The original content in the selected area will be permanently removed. Save as new PDF if you want to keep the original unchanged.") },
            confirmButton = {
                TextButton(onClick = { confirmOverwrite = false; saveToOriginal() }) { Text("Overwrite original") }
            },
            dismissButton = { TextButton(onClick = { confirmOverwrite = false }) { Text("Cancel") } }
        )
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.edit_pdf_back))
                }
                Text(stringResource(R.string.edit_pdf_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text("Select an area, then replace it with text, an image, or both.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (pdf == null) R.string.edit_pdf_choose else R.string.edit_pdf_change))
            }
            if (pdf != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { page = ((page.toIntOrNull() ?: 1) - 1).coerceAtLeast(1).toString() },
                        enabled = (page.toIntOrNull() ?: 1) > 1 && !busy) { Text("Previous") }
                    OutlinedTextField(value = page, onValueChange = {
                        page = it.filter(Char::isDigit).take(5).ifBlank { "1" }
                    }, label = { Text(stringResource(R.string.edit_pdf_page_number)) }, singleLine = true,
                        modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { page = ((page.toIntOrNull() ?: 1) + 1).coerceAtMost(pageCount).toString() },
                        enabled = (page.toIntOrNull() ?: 1) < pageCount && !busy) { Text("Next") }
                }
                Text("Page $page of $pageCount", style = MaterialTheme.typography.labelLarge)
                previewError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                preview?.let { bitmap ->
                    Text("Drag across the page to select the exact area to replace.", style = MaterialTheme.typography.bodyMedium)
                    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val ratio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
                        val imageHeight = maxWidth / ratio
                        Box(Modifier.fillMaxWidth().height(imageHeight).clip(RoundedCornerShape(8.dp))) {
                            Image(bitmap.asImageBitmap(), contentDescription = "PDF page preview",
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                            Canvas(Modifier.fillMaxSize().pointerInput(bitmap, pageBounds) {
                                detectDragGestures(
                                    onDragStart = { point ->
                                        dragStart = Offset(point.x.coerceIn(0f, size.width.toFloat()), point.y.coerceIn(0f, size.height.toFloat()))
                                        dragEnd = dragStart
                                        selection = null
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        dragEnd = Offset(change.position.x.coerceIn(0f, size.width.toFloat()), change.position.y.coerceIn(0f, size.height.toFloat()))
                                    },
                                    onDragEnd = {
                                        val start = dragStart
                                        val end = dragEnd
                                        val b = pageBounds
                                        if (start != null && end != null && b != null && size.width > 0f && size.height > 0f &&
                                            b.width() > 0f && b.height() > 0f) {
                                            val sx = b.left + start.x / size.width * b.width()
                                            val sy = b.top + start.y / size.height * b.height()
                                            val ex = b.left + end.x / size.width * b.width()
                                            val ey = b.top + end.y / size.height * b.height()
                                            val rect = RectF(min(sx, ex), min(sy, ey), max(sx, ex), max(sy, ey))
                                            if (rect.width() >= 3f && rect.height() >= 3f) selection = rect
                                        }
                                        dragStart = null
                                        dragEnd = null
                                    }
                                )
                            }) {
                                val active = selection
                                val b = pageBounds
                                if (active != null && b != null && b.width() > 0f && b.height() > 0f) {
                                    val left = (active.left - b.left) / b.width() * size.width
                                    val top = (active.top - b.top) / b.height() * size.height
                                    val right = (active.right - b.left) / b.width() * size.width
                                    val bottom = (active.bottom - b.top) / b.height() * size.height
                                    drawRect(Color(0x5533A1FF), Offset(left, top),
                                        androidx.compose.ui.geometry.Size(right - left, bottom - top))
                                    drawRect(Color(0xFF1687E8), Offset(left, top),
                                        androidx.compose.ui.geometry.Size(right - left, bottom - top),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { pageBounds?.let { selection = RectF(it) } },
                            modifier = Modifier.weight(1f), enabled = !busy) { Text("Select full page") }
                        OutlinedButton(onClick = { selection = null }, modifier = Modifier.weight(1f), enabled = !busy) {
                            Text("Clear selection")
                        }
                    }
                    selection?.let { Text("Selection ready: ${it.width().toInt()} × ${it.height().toInt()} pt",
                        color = MaterialTheme.colorScheme.primary) }
                }
            }
            OutlinedTextField(value = text, onValueChange = { text = it },
                label = { Text("Replacement text (optional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.edit_pdf_text_size, fontSize.toInt()))
            Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 8f..36f)
            OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Text(if (picture == null) "Choose replacement image (optional)" else "Change replacement image")
            }
            if (picture != null) {
                Text("Replacement image selected.", color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { picture = null }) { Text("Remove selected image") }
            }
            Text("The selected area is permanently cleared in the edited PDF. The original is kept until you choose to overwrite it.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                val selectedPdf = pdf
                val selectedArea = selection
                if (selectedPdf != null && selectedArea != null) scope.launch {
                    busy = true
                    val output = PdfContentReplacement.replace(context, selectedPdf,
                        (page.toIntOrNull() ?: 1) - 1, selectedArea, text, fontSize, picture)
                    busy = false
                    if (output == null) Toast.makeText(context, R.string.edit_pdf_edit_failed, Toast.LENGTH_LONG).show()
                    else {
                        resultFile = output
                        Toast.makeText(context, "Replacement applied. Choose how to save it.", Toast.LENGTH_SHORT).show()
                    }
                }
            }, enabled = pdf != null && selection != null && (text.isNotBlank() || picture != null) && !busy,
                modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator() else Text("Replace selected content")
            }
            if (resultFile != null) {
                HorizontalDivider()
                Text("Save options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Button(onClick = { confirmOverwrite = true }, enabled = !busy && pdf != null,
                    modifier = Modifier.fillMaxWidth()) { Text("Save to same PDF (overwrite original)") }
                OutlinedButton(onClick = { savePicker.launch(name + "_edited.pdf") },
                    enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Save as new PDF") }
                OutlinedButton(onClick = {
                    resultFile?.let { PdfActions.share(context, it, name + "_edited.pdf") }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Share edited PDF") }
            }
        }
    }
}
