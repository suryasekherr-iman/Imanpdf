package com.iman.pdf.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.Tab as TabItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iman.pdf.data.FileStore
import com.iman.pdf.data.PdfEntry
import com.iman.pdf.data.UserPrefs
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onOpen: (Uri) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { UserPrefs(context) }
    val store = remember { FileStore(context) }
    val name by prefs.userName.collectAsState(initial = "Indrajit")
    val entries by store.entries.collectAsState(initial = emptyList())
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Recent", "Starred", "On device")

    val shown: List<PdfEntry> = when (selectedTab) {
        0 -> entries
        1 -> entries.filter { it.starred }
        else -> emptyList()
    }

    val emptyText = when (selectedTab) {
        0 -> "No files yet. Tap + to open a PDF."
        1 -> "No starred files yet."
        else -> "On device files will appear here."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.MenuBook,
                contentDescription = "Iman",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { }) {
                Icon(Icons.Outlined.Search, contentDescription = "Search")
            }
            IconButton(onClick = { }) {
                Icon(Icons.Outlined.Notifications, contentDescription = "Notifications")
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Person, contentDescription = "Profile")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Welcome back, $name",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent
        ) {
            tabs.forEachIndexed { index, title ->
                TabItem(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (shown.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(shown, key = { it.uri }) { entry ->
                    FileCard(
                        entry = entry,
                        onOpen = { onOpen(Uri.parse(entry.uri)) },
                        onToggleStar = {
                            scope.launch { store.setStarred(entry.uri, !entry.starred) }
                        },
                        onRemove = {
                            scope.launch { store.remove(entry.uri) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FileCard(
    entry: PdfEntry,
    onOpen: () -> Unit,
    onToggleStar: () -> Unit,
    onRemove: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val thumb = remember(entry.thumb) {
        entry.thumb?.let { path ->
            try {
                BitmapFactory.decodeFile(path)?.asImageBitmap()
            } catch (e: Exception) {
                null
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
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(if (entry.starred) "Remove star" else "Add star")
                        },
                        onClick = {
                            menuOpen = false
                            onToggleStar()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from list") },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        }
                    )
                }
            }
        }
    }
}
