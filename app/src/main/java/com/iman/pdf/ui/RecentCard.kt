package com.iman.pdf.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iman.pdf.data.PdfEntry
import com.iman.pdf.pdf.PdfActions
import com.iman.pdf.pdf.UriActions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecentCard(
    entry: PdfEntry,
    onOpen: () -> Unit,
    onToggleStar: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sheetOpen by remember { mutableStateOf(false) }
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb,
                    contentDescription = entry.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (entry.starred) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Starred",
                    tint = Color(0xFFFFC107),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
        }

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
            onRemove = {
                sheetOpen = false
                onRemove()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileSheet(
    entry: PdfEntry,
    thumb: ImageBitmap?,
    onDismiss: () -> Unit,
    onToggleStar: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit
) {
    val opened = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        .format(Date(entry.lastOpened))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (thumb != null) {
                        Image(
                            bitmap = thumb,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.PictureAsPdf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp)
                ) {
                    Text(
                        text = entry.name,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "PDF  •  Opened " + opened,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onToggleStar) {
                    Icon(
                        imageVector = if (entry.starred) {
                            Icons.Filled.Star
                        } else {
                            Icons.Outlined.StarBorder
                        },
                        contentDescription = "Star",
                        tint = if (entry.starred) {
                            Color(0xFFFFC107)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SheetRow(Icons.Outlined.Share, "Share", onShare)
            SheetRow(Icons.Outlined.Print, "Print", onPrint)
            SheetRow(Icons.Outlined.SaveAlt, "Save a copy", onSave)
            SheetRow(Icons.Outlined.RemoveCircleOutline, "Remove from recent", onRemove)
        }
    }
}

@Composable
private fun SheetRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 20.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
