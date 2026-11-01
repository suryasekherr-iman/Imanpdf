package com.iman.pdf.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab as TabItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.iman.pdf.R
import com.iman.pdf.data.DeviceFiles
import com.iman.pdf.data.DevicePdf
import com.iman.pdf.data.FileStore
import com.iman.pdf.data.PdfEntry
import com.iman.pdf.data.UserPrefs
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpen: (Uri) -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { UserPrefs(context) }
    val store = remember { FileStore(context) }
    val name by prefs.userName.collectAsState(initial = "Indrajit")
    val photo by prefs.photoPath.collectAsState(initial = "")
    val entries by store.entries.collectAsState(initial = emptyList())
    val tabs = listOf("Recent", "Starred", "On device")
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 3 })
    val selectedTab = pagerState.currentPage
    var searching by rememberSaveable { mutableStateOf(false) }
    var listView by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var clearOpen by remember { mutableStateOf(false) }
    var selectMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }

    var hasAccess by remember { mutableStateOf(DeviceFiles.hasAllFilesAccess()) }
    var scanning by remember { mutableStateOf(false) }
    var deviceFiles by remember { mutableStateOf<List<DevicePdf>>(emptyList()) }

    fun exitSelect() {
        selectMode = false
        selected.clear()
    }

    fun toggle(uri: String) {
        if (selected.contains(uri)) selected.remove(uri) else selected.add(uri)
        selectMode = selected.isNotEmpty()
    }

    BackHandler(enabled = selectMode) { exitSelect() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = DeviceFiles.hasAllFilesAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(selectedTab, hasAccess) {
        if (selectMode) exitSelect()
        if (selectedTab == 2 && hasAccess) {
            scanning = true
            deviceFiles = DeviceFiles.scan(context)
            scanning = false
        }
    }

    fun requestAccess() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:" + context.packageName)
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: Exception) {
            }
        }
    }

    val starredList = entries.filter { it.starred }
    val currentList: List<PdfEntry> = if (selectedTab == 0) entries else starredList

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            if (selectMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { exitSelect() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                    Text(
                        text = selected.size.toString() + " selected",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(
                        onClick = {
                            selected.clear()
                            selected.addAll(currentList.map { it.uri })
                        }
                    ) {
                        Icon(Icons.Outlined.SelectAll, contentDescription = "Select all")
                    }
                    IconButton(
                        onClick = {
                            val allStarred = selected.all { uri ->
                                entries.firstOrNull { it.uri == uri }?.starred == true
                            }
                            val list = selected.toList()
                            scope.launch {
                                list.forEach { store.setStarred(it, !allStarred) }
                            }
                            exitSelect()
                        }
                    ) {
                        Icon(Icons.Outlined.StarBorder, contentDescription = "Star")
                    }
                    IconButton(
                        onClick = {
                            val list = selected.toList()
                            scope.launch { list.forEach { store.remove(it) } }
                            exitSelect()
                        }
                    ) {
                        Icon(
                            Icons.Outlined.RemoveCircleOutline,
                            contentDescription = "Remove from recent"
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.iman_logo),
                            contentDescription = "Iman",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(scaleX = 1.65f, scaleY = 1.65f),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { searching = true }) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { }) {
                        Icon(Icons.Outlined.Notifications, contentDescription = "Notifications")
                    }
                    Avatar(
                        path = photo,
                        size = 36.dp,
                        modifier = Modifier.clickable { onOpenSettings() }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Welcome back, $name",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    modifier = Modifier.weight(1f)
                ) {
                    tabs.forEachIndexed { index, title ->
                        TabItem(
                            selected = selectedTab == index,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            text = { Text(title) }
                        )
                    }
                }
                if (selectedTab != 2) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Select") },
                                onClick = {
                                    menuOpen = false
                                    selectMode = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Select all") },
                                onClick = {
                                    menuOpen = false
                                    selected.clear()
                                    selected.addAll(currentList.map { it.uri })
                                    selectMode = selected.isNotEmpty()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (listView) "Grid view" else "List view") },
                                onClick = {
                                    menuOpen = false
                                    listView = !listView
                                }
                            )
                            if (selectedTab == 0) {
                                DropdownMenuItem(
                                    text = { Text("Clear recent") },
                                    onClick = {
                                        menuOpen = false
                                        clearOpen = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 12.dp
            ) { page ->
                if (page == 2) {
                    DeviceTab(
                        hasAccess = hasAccess,
                        scanning = scanning,
                        files = deviceFiles,
                        onGrant = { requestAccess() },
                        onOpen = onOpen
                    )
                } else {
                    val list = if (page == 0) entries else starredList
                    if (list.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (page == 0) "No files yet. Tap + to open a PDF."
                                else "No starred files yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(if (listView) 1 else 2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 96.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(if (listView) 4.dp else 16.dp)
                        ) {
                            items(list, key = { it.uri }) { entry ->
                                RecentCard(
                                    entry = entry,
                                    onOpen = { onOpen(Uri.parse(entry.uri)) },
                                    onToggleStar = {
                                        scope.launch { store.setStarred(entry.uri, !entry.starred) }
                                    },
                                    onRemove = {
                                        scope.launch { store.remove(entry.uri) }
                                    },
                                    listMode = listView,
                                    selectMode = selectMode,
                                    selected = selected.contains(entry.uri),
                                    onSelectToggle = { toggle(entry.uri) }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (searching) {
            HomeSearch(
                entries = entries,
                onOpen = onOpen,
                onClose = { searching = false }
            )
        }
    }

    if (clearOpen) {
        AlertDialog(
            onDismissRequest = { clearOpen = false },
            title = { Text("Clear recent?") },
            text = { Text("Recent files will be removed from this list. Starred files stay. Your PDF files are not deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearOpen = false
                        val list = entries.filter { !it.starred }
                        scope.launch { list.forEach { store.remove(it.uri) } }
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { clearOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
