package com.iman.pdf.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iman.pdf.pdf.PdfActions
import com.iman.pdf.pdf.PdfEngine
import com.iman.pdf.pdf.PdfOrganize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class PageItem(val id: Int, val original: Int, val rotation: Int)

private fun organizeNameOf(context: Context, uri: Uri): String {
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
    return name ?: "Document.pdf"
}

@Composable
fun OrganizeScreen(
    onBack: () -> Unit,
    onOpen: (Uri) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var engine by remember { mutableStateOf<PdfEngine?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var pages by remember { mutableStateOf<List<PageItem>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var second by remember { mutableStateOf<PdfEngine?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<File?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            engine?.close()
            second?.close()
        }
    }

    val onPicked: (Uri?) -> Unit = { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val opened = PdfEngine.open(context, uri)
                busy = false
                if (opened == null) {
                    Toast.makeText(
                        context,
                        "Could not open this PDF",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    engine?.close()
                    engine = opened
                    sourceName = organizeNameOf(context, uri)
                    pages = (0 until opened.pageCount).map { PageItem(it, it, 0) }
                    selected = emptySet()
                }
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
        onPicked
    )

    LaunchedEffect(Unit) {
        ToolSeed.take()?.let { onPicked(it) }
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

    fun rotateSelected() {
        pages = pages.map {
            if (selected.contains(it.id)) {
                it.copy(rotation = (it.rotation + 90) % 360)
            } else {
                it
            }
        }
    }

    fun deleteSelected() {
        pages = pages.filter { !selected.contains(it.id) }
        selected = emptySet()
    }

    fun insertAfterSelection(items: List<PageItem>) {
        val lastSelected = pages.indexOfLast { selected.contains(it.id) }
        val at = if (lastSelected >= 0) lastSelected + 1 else pages.size
        val list = pages.toMutableList()
        list.addAll(at, items)
        pages = list
    }

    fun nextId(): Int = (pages.maxOfOrNull { it.id } ?: -1) + 1

    fun addBlank() {
        insertAfterSelection(listOf(PageItem(nextId(), -1, 0)))
    }

    val insertPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            if (pages.any { it.original <= -2 }) {
                Toast.makeText(
                    context,
                    "Remove the earlier inserted pages first",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                scope.launch {
                    busy = true
                    val opened = PdfEngine.open(context, uri)
                    busy = false
                    if (opened == null) {
                        Toast.makeText(context, "Could not open this PDF", Toast.LENGTH_LONG).show()
                    } else {
                        second?.close()
                        second = opened
                        val start = nextId()
                        insertAfterSelection(
                            (0 until opened.pageCount).map { PageItem(start + it, -(it + 2), 0) }
                        )
                    }
                }
            }
        }
    }

    fun duplicateSelected() {
        var next = (pages.maxOfOrNull { it.id } ?: -1) + 1
        val list = ArrayList<PageItem>()
        pages.forEach { item ->
            list.add(item)
            if (selected.contains(item.id)) {
                list.add(item.copy(id = next++))
            }
        }
        pages = list
    }

    fun extractSelected() {
        val chosen = pages.filter { selected.contains(it.id) }
        val file = engine?.file
        if (chosen.isEmpty() || file == null) {
            return
        }
        scope.launch {
            busy = true
            val result = PdfOrganize.apply(
                context,
                file,
                chosen.map { it.original },
                chosen.map { it.rotation },
                second?.file
            )
            busy = false
            if (result == null) {
                Toast.makeText(context, "Could not save these changes", Toast.LENGTH_LONG).show()
            } else {
                pending = result
                saver.launch(sourceName.removeSuffix(".pdf") + "_extracted.pdf")
            }
        }
    }

    fun moveSelected(delta: Int) {
        if (selected.size != 1) {
            return
        }
        val id = selected.first()
        val from = pages.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to < 0 || to >= pages.size) {
            return
        }
        val list = pages.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        pages = list
    }

    val current = engine

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
                    text = "Organize pages",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (current == null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (busy) {
                        CircularProgressIndicator()
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Choose a PDF to rotate, delete or rearrange its pages.",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = { picker.launch(arrayOf("application/pdf")) },
                                modifier = Modifier.padding(top = 16.dp)
                            ) {
                                Text("Choose a PDF")
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = pages.size.toString() + " pages  •  " +
                        selected.size + " selected. Tap pages to select them.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(modifier = Modifier.weight(1f)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(pages, key = { it.id }) { page ->
                            PageCell(
                                engine = if (page.original <= -2) second else current,
                                original = page.original,
                                rotation = page.rotation,
                                position = pages.indexOf(page) + 1,
                                selected = selected.contains(page.id),
                                onClick = {
                                    selected = if (selected.contains(page.id)) {
                                        selected - page.id
                                    } else {
                                        selected + page.id
                                    }
                                }
                            )
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { moveSelected(-1) },
                        enabled = selected.size == 1
                    ) {
                        Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Move earlier")
                    }
                    IconButton(
                        onClick = { moveSelected(1) },
                        enabled = selected.size == 1
                    ) {
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Move later")
                    }
                    IconButton(
                        onClick = { rotateSelected() },
                        enabled = selected.isNotEmpty()
                    ) {
                        Icon(Icons.Outlined.RotateRight, contentDescription = "Rotate")
                    }
                    IconButton(
                        onClick = { deleteSelected() },
                        enabled = selected.isNotEmpty()
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                    }
                    IconButton(onClick = { addBlank() }) {
                        Icon(Icons.Outlined.NoteAdd, contentDescription = "Add blank page")
                    }
                    IconButton(onClick = { insertPicker.launch(arrayOf("application/pdf")) }) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = "Insert pages from PDF")
                    }
                    IconButton(
                        onClick = { duplicateSelected() },
                        enabled = selected.isNotEmpty()
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Duplicate")
                    }
                    IconButton(
                        onClick = { extractSelected() },
                        enabled = selected.isNotEmpty()
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = "Extract pages")
                    }
                }

                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            val result = PdfOrganize.apply(
                                context,
                                current.file,
                                pages.map { it.original },
                                pages.map { it.rotation },
                                second?.file
                            )
                            busy = false
                            if (result == null) {
                                Toast.makeText(
                                    context,
                                    "Could not save these changes",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                pending = result
                                saver.launch(sourceName.removeSuffix(".pdf") + "_edited.pdf")
                            }
                        }
                    },
                    enabled = pages.isNotEmpty() && !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    Text("Save as new PDF")
                }
            }
        }
    }
}

@Composable
private fun PageCell(
    engine: PdfEngine?,
    original: Int,
    rotation: Int,
    position: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    var bitmap by remember(original) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(original, engine) {
        val index = if (original <= -2) -original - 2 else original
        bitmap = if (original == -1 || engine == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                try { engine.renderPage(index, 260) } catch (_: Exception) { null }
            }
        }
    }

    val sideways = rotation == 90 || rotation == 270
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(3.dp, borderColor, RoundedCornerShape(8.dp))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            val current = bitmap
            if (current != null) {
                Image(
                    bitmap = current.asImageBitmap(),
                    contentDescription = "Page " + position,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .graphicsLayer(
                            rotationZ = rotation.toFloat(),
                            scaleX = if (sideways) 0.72f else 1f,
                            scaleY = if (sideways) 0.72f else 1f
                        ),
                    contentScale = ContentScale.Fit
                )
            }
        }
        Text(
            text = position.toString(),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
