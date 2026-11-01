package com.iman.pdf.ui

import android.net.Uri
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iman.pdf.pdf.PdfActions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private suspend fun editPdf(
    context: android.content.Context,
    uri: Uri,
    pageNumber: Int,
    text: String,
    size: Float,
    imageUri: Uri?
): File? = withContext(Dispatchers.IO) {
    var source: File? = null
    try {
        PDFBoxResourceLoader.init(context.applicationContext)
        source = File(context.cacheDir, "editor_source_${System.nanoTime()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            source.outputStream().use { output -> input.copyTo(output) }
        } ?: return@withContext null
        PDDocument.load(source).use { document ->
            if (document.numberOfPages == 0) return@withContext null
            val page = document.getPage((pageNumber - 1).coerceIn(0, document.numberOfPages - 1))
            if (text.isNotBlank()) {
                PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, size)
                    stream.newLineAtOffset(40f, page.mediaBox.height - 70f)
                    stream.showText(text)
                    stream.endText()
                }
            }
            if (imageUri != null) {
                val bytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                    ?: return@withContext null
                val image = PDImageXObject.createFromByteArray(document, bytes, "inserted-image")
                val width = (page.mediaBox.width * 0.4f).coerceAtMost(image.width.toFloat())
                val scale = width / image.width
                PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                    stream.drawImage(image, 40f, 40f, width, image.height * scale)
                }
            }
            val output = File(context.cacheDir, "edited_${System.nanoTime()}.pdf")
            document.save(output)
            output
        }
    } catch (_: Exception) {
        null
    } finally {
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

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pdf = it }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { picture = it }
    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { target ->
        val file = resultFile
        if (target != null && file != null) scope.launch {
            val saved = withContext(Dispatchers.IO) { PdfActions.saveCopy(context, file, target) }
            if (saved) {
                Toast.makeText(context, "Edited PDF saved", Toast.LENGTH_SHORT).show()
                onOpen(target)
            } else Toast.makeText(context, "Could not save PDF", Toast.LENGTH_LONG).show()
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Edit PDF", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text("Add text or an image to a PDF page. This adds new content; it does not erase or replace existing printed text.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (pdf == null) "Choose PDF" else "Change PDF")
            }
            if (pdf != null) Text("PDF selected")
            OutlinedTextField(value = page, onValueChange = { page = it.filter(Char::isDigit).ifBlank { "1" } },
                label = { Text("Page number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Text to add") },
                minLines = 2, modifier = Modifier.fillMaxWidth())
            Text("Text size: ${size.toInt()} pt")
            Slider(value = size, onValueChange = { size = it }, valueRange = 8f..36f)
            OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Text(if (picture == null) "Choose image to insert" else "Change inserted image")
            }
            Button(
                onClick = {
                    val selected = pdf
                    if (selected != null) scope.launch {
                        busy = true
                        val edited = editPdf(context, selected, page.toIntOrNull() ?: 1, text, size, picture)
                        busy = false
                        if (edited == null) Toast.makeText(context, "Could not edit this PDF. Check the file and text.", Toast.LENGTH_LONG).show()
                        else {
                            resultFile = edited
                            savePicker.launch(name + "_edited.pdf")
                        }
                    }
                },
                enabled = pdf != null && (text.isNotBlank() || picture != null) && !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) CircularProgressIndicator() else Text("Apply changes and save")
            }
        }
    }
}
