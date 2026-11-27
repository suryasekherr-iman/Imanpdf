package com.iman.pdf.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Offset
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.iman.pdf.R
import com.iman.pdf.data.DeviceFiles
import com.iman.pdf.data.DevicePdf
import com.iman.pdf.data.FileStore
import com.iman.pdf.data.PdfEntry
import com.iman.pdf.data.UserPrefs
import kotlinx.coroutines.launch

private data class HomeQuickAction(val id: String, val titleRes: Int, val icon: ImageVector, val tint: Color)

private val homeQuickActions = listOf(
    HomeQuickAction("create_pdf", R.string.tool_create_pdf, Icons.Outlined.NoteAdd, Color(0xFFFF6B6B)),
    HomeQuickAction("edit_pdf", R.string.tool_edit_pdf, Icons.Outlined.Edit, Color(0xFF5AA9FF)),
    HomeQuickAction("flashcards", R.string.tool_flashcards, Icons.Outlined.Style, Color(0xFFB388FF)),
    HomeQuickAction("fill_sign", R.string.tool_fill_sign, Icons.Outlined.Gesture, Color(0xFFFFA24C)),
    HomeQuickAction("export", R.string.tool_export_pdf, Icons.Outlined.Article, Color(0xFF4CD1A0)),
    HomeQuickAction("organize", R.string.tool_organize, Icons.Outlined.ViewModule, Color(0xFF5AA9FF)),
    HomeQuickAction("combine", R.string.tool_combine, Icons.Outlined.MergeType, Color(0xFFFF6B6B)),
    HomeQuickAction("compress", R.string.tool_compress, Icons.Outlined.Compress, Color(0xFF4CD1A0)),
    HomeQuickAction("read_aloud", R.string.tool_read_aloud, Icons.Outlined.VolumeUp, Color(0xFF5AA9FF)),
    HomeQuickAction("password", R.string.tool_password, Icons.Outlined.Lock, Color(0xFFFFA24C)),
    HomeQuickAction("view_all_tools", R.string.tools_view_all, Icons.Outlined.GridView, Color(0xFFB388FF))
)

@Composable
fun HomeScreen(
    onOpen: (Uri) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onQuickAction: (String) -> Unit = {},
    onFileAction: (String, Uri) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { UserPrefs(context) }
    val store = remember { FileStore(context) }
    val name by prefs.userName.collectAsState(initial = "Indrajit")
    val photo by prefs.photoPath.collectAsState(initial = "")
    val themeName by prefs.themeMode.collectAsState(initial = "SYSTEM")
    val isDarkTheme = when (themeName) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }
    val hideRecent by prefs.hideRecentDocuments.collectAsState(initial = false)
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

    var hasAccess by remember { mutableStateOf(true) }
    var scanning by remember { mutableStateOf(false) }
    var deviceFiles by remember { mutableStateOf<List<DevicePdf>>(emptyList()) }
    var scanError by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableStateOf(0) }
    var homeHeaderHeightPx by remember { mutableStateOf(0f) }
    var homeHeaderOffsetPx by remember { mutableStateOf(0f) }
    val homeScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val previousOffset = homeHeaderOffsetPx
                val nextOffset = (previousOffset + available.y)
                    .coerceIn(-homeHeaderHeightPx, 0f)
                homeHeaderOffsetPx = nextOffset
                return Offset(x = 0f, y = nextOffset - previousOffset)
            }
        }
    }

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
                refreshToken++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(selectedTab, hasAccess, refreshToken) {
        if (selectMode) exitSelect()
        if (selectedTab == 2 && hasAccess) {
            scanning = true
            scanError = false
            try {
                deviceFiles = DeviceFiles.scan(context)
            } catch (e: Exception) {
                com.iman.pdf.logSwallowed("HomeScreen:155", e)
                deviceFiles = emptyList()
                scanError = true
            } finally {
                scanning = false
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                DeviceFiles.addFolder(context, uri)
                refreshToken++
            } catch (e: SecurityException) {
                com.iman.pdf.logSwallowed("HomeScreen:172", e)
                scanError = true
            }
        }
    }

    fun requestAccess() { folderPicker.launch(null) }

    val recentEntries = if (hideRecent) emptyList() else entries
    val starredList = entries.filter { it.starred }
    val currentList: List<PdfEntry> = if (selectedTab == 0) recentEntries else starredList

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .nestedScroll(homeScrollConnection)
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
                    // Brand stays at the far left; profile, search and theme controls align right.
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.iman_logo),
                            contentDescription = "ImAn brand logo",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(scaleX = 1.65f, scaleY = 1.65f),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Avatar(
                        path = photo,
                        size = 36.dp,
                        modifier = Modifier.clickable { onOpenSettings() }
                    )
                    IconButton(onClick = { searching = true }) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                scope.launch {
                                    prefs.setThemeMode(if (isDarkTheme) "LIGHT" else "DARK")
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = if (isDarkTheme) "Switch to light mode" else "Switch to dark mode",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val offsetY = homeHeaderOffsetPx.roundToInt()
                        val visibleHeight = (placeable.height + offsetY).coerceIn(0, placeable.height)
                        layout(placeable.width, visibleHeight) {
                            placeable.placeRelative(0, offsetY)
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            homeHeaderHeightPx = size.height.toFloat()
                            homeHeaderOffsetPx = homeHeaderOffsetPx.coerceIn(-homeHeaderHeightPx, 0f)
                        }
                ) {
                    if (!selectMode) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Welcome back, $name",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.create_quick_actions),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowItems(homeQuickActions, key = { it.id }) { action ->
                                Column(
                                    modifier = Modifier
                                        .width(112.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .clickable { onQuickAction(action.id) }
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(action.tint.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(action.icon, contentDescription = null, tint = action.tint)
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = stringResource(action.titleRes),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
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
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 12.dp
            ) { page ->
                if (page == 2) {
                    DeviceTab(
                        scanning = scanning,
                        files = deviceFiles,
                        scanError = scanError,
                        onGrant = { requestAccess() },
                        onOpen = onOpen
                    )
                } else {
                    val list = if (page == 0) recentEntries else starredList
                    if (list.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (page == 0 && hideRecent) stringResource(R.string.home_recent_hidden)
                                else if (page == 0) "No files yet. Tap + to open a PDF."
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
                                    onSelectToggle = { toggle(entry.uri) },
                                    onFileAction = onFileAction
                                )
                            }
                        }
                    }
                }
            }
        }

        if (searching) {
            HomeSearch(
                entries = recentEntries,
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
