package com.iman.pdf.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iman.pdf.data.DeviceFiles
import com.iman.pdf.data.DevicePdf
import com.iman.pdf.data.PdfEntry

@Composable
fun HomeSearch(
    entries: List<PdfEntry>,
    onOpen: (Uri) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var device by remember { mutableStateOf<List<DevicePdf>>(emptyList()) }
    val focus = remember { FocusRequester() }

    BackHandler { onClose() }

    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (e: Exception) {
        }
        if (DeviceFiles.hasAllFilesAccess()) {
            device = DeviceFiles.scan(context)
        }
    }

    val q = query.trim()
    val recentHits = if (q.isEmpty()) {
        emptyList()
    } else {
        entries.filter { it.name.contains(q, ignoreCase = true) }
    }
    val seen = recentHits.map { it.uri }.toSet()
    val deviceHits = if (q.isEmpty()) {
        emptyList()
    } else {
        device.filter {
            it.name.contains(q, ignoreCase = true) && it.uri.toString() !in seen
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus),
                singleLine = true,
                placeholder = { Text("Search PDFs") },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                }
            )
        }

        if (q.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Type to search your PDFs",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (recentHits.isEmpty() && deviceHits.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No PDFs found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(recentHits, key = { "r" + it.uri }) { entry ->
                    ResultRow(entry.name, "Recent") {
                        onOpen(Uri.parse(entry.uri))
                        onClose()
                    }
                }
                items(deviceHits, key = { "d" + it.uri }) { file ->
                    ResultRow(file.name, "On device") {
                        onOpen(file.uri)
                        onClose()
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    name: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.PictureAsPdf,
            contentDescription = null,
            tint = Color(0xFFE53935),
            modifier = Modifier.size(36.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                text = name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
