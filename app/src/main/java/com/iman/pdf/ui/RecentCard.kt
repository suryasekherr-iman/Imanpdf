package com.iman.pdf.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iman.pdf.data.FileStore
import com.iman.pdf.data.PdfEntry
import com.iman.pdf.pdf.FileOps
import com.iman.pdf.pdf.PdfActions
import com.iman.pdf.pdf.UriActions
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentCard(
    entry: PdfEntry,
    onOpen: () -> Unit,
    onToggleStar: () -> Unit,
    onRemove: () -> Unit,
    listMode: Boolean = false,
    selectMode: Boolean = false,
    selected: Boolean = false,
    onSelectToggle: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sheetOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val store = remember { FileStore(context) }
    val fileName = if (entry.name.endsWith(".pdf", ignoreCase = true)) {
        entry.name
    } else {
        entry.name + ".pdf"
    }

    val thumb = remember(entry.thumb) {
        entry.thumb?.let { path ->
            try {
                BitmapFactory.decodeFile(path)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { destination ->
        if (destination != null) {
            scope.launch {
                val ok = UriActions.saveUriCopy(context, Uri.parse(entry.uri), destination)
                Toast.makeText(
                    context,
                    if (ok) "Copy saved" else "Could not save the copy",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val tap: () -> Unit = {
        if (selectMode) onSelectToggle() else onOpen()
    }

    if (listMode) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = tap, onLongClick = onSelectToggle)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Thumb(
                thumb = thumb,
                name = entry.name,
                starred = entry.starred,
                selectMode = selectMode,
                selected = selected,
                modifier = Modifier
                    .width(60.dp)
                    .height(80.dp),
                pad = 6.dp
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    text = entry.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "PDF  •  " + formatDate(entry.lastOpened),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { sheetOpen = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "More")
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = tap, onLongClick = onSelectToggle)
        ) {
            Thumb(
                thumb = thumb,
                name = entry.name,
                starred = entry.starred,
                selectMode = selectMode,
                selected = selected,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.75f),
                pad = 16.dp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = entry.name,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "PDF",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { sheetOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                }
            }
        }
    }

    if (sheetOpen) {
        FileSheet(
            entry = entry,
            thumb = thumb,
            onDismiss = { sheetOpen = false },
            onToggleStar = onToggleStar,
            onShare = {
                sheetOpen = false
                scope.launch {
                    val file = UriActions.copyToCache(context, Uri.parse(entry.uri))
                    if (file != null) {
                        PdfActions.share(context, file, entry.name)
                    } else {
                        Toast.makeText(context, "Could not open this PDF", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            },
            onPrint = {
                sheetOpen = false
                scope.launch {
                    val file = UriActions.copyToCache(context, Uri.parse(entry.uri))
                    if (file != null) {
                        PdfActions.print(context, file, entry.name)
                    } else {
                        Toast.makeText(context, "Could not open this PDF", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            },
            onSave = {
                sheetOpen = false
                saveLauncher.launch(fileName)
            },
            onRename = {
                sheetOpen = false
                newName = entry.name.removeSuffix(".pdf")
                renameOpen = true
            },
            onDelete = {
                sheetOpen = false
                deleteOpen = true
            },
            onRemove = {
                sheetOpen = false
                onRemove()
            }
        )
    }

    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clean = newName.filterNot { it in "\\/:*?\"<>|" }.trim()
                        renameOpen = false
                        if (clean.isNotEmpty()) {
                            val finalName = if (clean.endsWith(".pdf", ignoreCase = true)) {
                                clean
                            } else {
                                clean + ".pdf"
                            }
                            scope.launch {
                                val newUri = FileOps.rename(context, Uri.parse(entry.uri), finalName)
                                if (newUri == null) {
                                    Toast.makeText(
                                        context,
                                        "Could not rename this file",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    val target = newUri.toString()
                                    store.recordOpened(target, finalName, entry.thumb)
                                    if (entry.starred) {
                                        store.setStarred(target, true)
                                    }
                                    if (target != entry.uri) {
                                        store.remove(entry.uri)
                                    }
                                    Toast.makeText(context, "Renamed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text("Delete file?") },
            text = { Text(entry.name + " will be deleted from your phone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteOpen = false
                        scope.launch {
                            val ok = FileOps.delete(context, Uri.parse(entry.uri))
                            if (ok) {
                                Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                                store.remove(entry.uri)
                            } else {
                                Toast.makeText(
                                    context,
                                    "Could not delete this file",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun Thumb(
    thumb: ImageBitmap?,
    name: String,
    starred: Boolean,
    selectMode: Boolean,
    selected: Boolean,
    modifier: Modifier,
    pad: Dp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = name,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad),
                contentScale = ContentScale.Fit
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.PictureAsPdf,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (starred) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "Starred",
                tint = Color(0xFFFFC107),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(18.dp)
            )
        }
        if (selectMode) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
            )
        }
    }
}
