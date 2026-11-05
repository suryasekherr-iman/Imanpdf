package com.iman.pdf.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iman.pdf.R
import com.iman.pdf.pdf.PdfActions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Image
import com.artifex.mupdf.fitz.PDFAnnotation
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.PDFPage
import com.artifex.mupdf.fitz.Rect
import com.iman.pdf.pdf.UriActions

private suspend fun editPdf(
    context: android.content.Context,
    uri: Uri,
    pageNumber: Int,
    text: String,
    size: Float,
    imageUri: Uri?
): File? = withContext(Dispatchers.IO) {
    var source: File? = null
    var document: PDFDocument? = null
    try {
        source = UriActions.copyToCache(context, uri) ?: return@withContext null
        document = Document.openDocument(source.absolutePath) as? PDFDocument
            ?: return@withContext null
        if (document.countPages() == 0) return@withContext null
        val page = document.loadPage((pageNumber - 1).coerceIn(0, document.countPages() - 1)) as? PDFPage
            ?: return@withContext null
        try {
            val bounds = page.bounds
            val width = (bounds.x1 - bounds.x0).coerceAtLeast(1f)
            val height = (bounds.y1 - bounds.y0).coerceAtLeast(1f)
            if (text.isNotBlank()) {
                val annotation = page.createAnnotation(PDFAnnotation.TYPE_FREE_TEXT)
                val fontSize = size.coerceIn(8f, 36f)
                val textWidth = (text.lines().maxOfOrNull { it.length } ?: 1) * fontSize * 0.65f
                val textHeight = text.lines().size * fontSize * 1.35f
                annotation.setRect(Rect(bounds.x0 + 40f, bounds.y0 + 40f,
                    (bounds.x0 + 40f + textWidth).coerceAtMost(bounds.x1),
                    (bounds.y0 + 40f + textHeight).coerceAtMost(bounds.y1)))
                annotation.setContents(text)
                annotation.setColor(floatArrayOf(0f, 0f, 0f))
                annotation.setDefaultAppearance("Helv", fontSize, floatArrayOf(0f, 0f, 0f))
                annotation.update()
                annotation.destroy()
            }
            if (imageUri != null) {
                val bytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                    ?: return@withContext null
                val image = Image(bytes)
                try {
                    val annotation = page.createAnnotation(PDFAnnotation.TYPE_STAMP)
                    val imageWidth = width * 0.4f
                    val imageHeight = imageWidth * image.height / image.width.coerceAtLeast(1)
                    annotation.setRect(Rect(bounds.x0 + 40f, bounds.y1 - imageHeight - 40f,
                        bounds.x0 + 40f + imageWidth, bounds.y1 - 40f))
                    annotation.setAppearance(image)
                    annotation.update()
                    annotation.destroy()
                } finally { image.destroy() }
            }
            page.update()
        } finally { page.destroy() }
        val output = File(context.cacheDir, "edited_${System.nanoTime()}.pdf")
        document.save(output.absolutePath, "garbage=compact,appearance=yes")
        output.takeIf { it.isFile && it.length() > 0 }
    } catch (_: Exception) {
        null
    } finally {
        try { document?.destroy() } catch (_: Exception) {}
        source?.delete()
    }
}

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
    var size by remember { mutableFloatStateOf(18f) }
    var busy by remember { mutableStateOf(false) }
    var resultFile by remember { mutableStateOf<File?>(null) }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pdf = uri
        if (uri != null) {
            val displayName = runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (column >= 0) cursor.getString(column) else null
                    } else null
                }
            }.getOrNull()
            name = (displayName ?: "Document.pdf").removeSuffix(".pdf")
                .removeSuffix(".PDF").ifBlank { "Document" }
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { picture = it }
    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { target ->
        val file = resultFile
        if (target != null && file != null) scope.launch {
            val saved = withContext(Dispatchers.IO) { PdfActions.saveCopy(context, file, target) }
            if (saved) {
                Toast.makeText(context, R.string.edit_pdf_saved, Toast.LENGTH_SHORT).show()
                onOpen(target)
            } else {
                Toast.makeText(context, R.string.edit_pdf_save_failed, Toast.LENGTH_LONG).show()
            }
        }
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
            Text(
                stringResource(R.string.edit_pdf_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (pdf == null) R.string.edit_pdf_choose else R.string.edit_pdf_change))
            }
            if (pdf != null) Text(stringResource(R.string.edit_pdf_selected))
            OutlinedTextField(
                value = page,
                onValueChange = { page = it.filter(Char::isDigit).ifBlank { "1" } },
                label = { Text(stringResource(R.string.edit_pdf_page_number)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.edit_pdf_text_to_add)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Text(stringResource(R.string.edit_pdf_text_size, size.toInt()))
            Slider(value = size, onValueChange = { size = it }, valueRange = 8f..36f)
            OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (picture == null) R.string.edit_pdf_choose_image else R.string.edit_pdf_change_image))
            }
            Button(
                onClick = {
                    val selected = pdf
                    if (selected != null) scope.launch {
                        busy = true
                        val edited = editPdf(context, selected, page.toIntOrNull() ?: 1, text, size, picture)
                        busy = false
                        if (edited == null) {
                            Toast.makeText(context, R.string.edit_pdf_edit_failed, Toast.LENGTH_LONG).show()
                        } else {
                            resultFile = edited
                            savePicker.launch(name + "_edited.pdf")
                        }
                    }
                },
                enabled = pdf != null && (text.isNotBlank() || picture != null) && !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) CircularProgressIndicator() else Text(stringResource(R.string.edit_pdf_apply_save))
            }
        }
    }
}
