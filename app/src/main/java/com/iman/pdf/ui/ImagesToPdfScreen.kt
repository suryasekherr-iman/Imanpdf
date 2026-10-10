package com.iman.pdf.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iman.pdf.pdf.PdfActions
import com.iman.pdf.pdf.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class PickedImage(val uri: Uri, val name: String)

private fun imageNameOf(context: Context, uri: Uri): String {
    var name: String? = null
    try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0) {
                    name = cursor.getString(column)
                }
            }
        }
    } catch (_: Exception) {
    }
    return name ?: "Image"
}

@Composable
fun ImagesToPdfScreen(
    onBack: () -> Unit,
    onOpen: (Uri) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<PickedImage>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<File?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            items = items + uris.map { PickedImage(it, imageNameOf(context, it)) }
        }
    }

    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { destination ->
        val file = pending
        if (destination != null && file != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    PdfActions.saveCopy(context, file, destination)
                }
                if (ok) {
                    Toast.makeText(context, "PDF saved", Toast.LENGTH_SHORT).show()
                    onOpen(destination)
                } else {
                    Toast.makeText(context, "Could not save the PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun move(from: Int, to: Int) {
        if (to < 0 || to >= items.size) {
            return
        }
        val list = items.toMutableList()
        val moved = list.removeAt(from)
        list.add(to, moved)
        items = list
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Create PDF",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = { picker.launch(arrayOf("image/*")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Add images")
            }

            Box(modifier = Modifier.weight(1f)) {
                if (items.isEmpty()) {
                    Text(
                        text = "Add one or more images. Each image becomes one page of the PDF.",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(items) { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = null,
                                    tint = Color(0xFF4CD1A0)
                                )
                                Text(
                                    text = (index + 1).toString() + ". " + item.name,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                IconButton(onClick = { move(index, index - 1) }) {
                                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                                }
                                IconButton(onClick = { move(index, index + 1) }) {
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "Move down"
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        items = items.filterIndexed { i, _ -> i != index }
                                    }
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove")
                                }
                            }
                        }
                    }
                }
                if (busy) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        val created = PdfTools.imagesToPdf(context, items.map { it.uri })
                        busy = false
                        if (created == null) {
                            Toast.makeText(
                                context,
                                "Could not create the PDF",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            pending = created
                            saver.launch("Images.pdf")
                        }
                    }
                },
                enabled = items.isNotEmpty() && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                Text("Create PDF from " + items.size + " images")
            }
        }
    }
}
