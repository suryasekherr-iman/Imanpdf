package com.iman.pdf.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.iman.pdf.data.FileStore
import com.iman.pdf.pdf.PdfEngine
import com.iman.pdf.pdf.Thumbnails
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    CREATE("Create", Icons.Outlined.Create),
    FILES("Files", Icons.Outlined.Folder),
    TOOLS("Tools", Icons.Outlined.GridView)
}

private fun fileNameOf(context: Context, uri: Uri): String {
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
fun MainScreen(incomingUri: Uri? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { FileStore(context) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var engine by remember { mutableStateOf<PdfEngine?>(null) }
    var title by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val tabs = Tab.entries

    fun openUri(uri: Uri) {
        scope.launch {
            loading = true
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            val opened = PdfEngine.open(context, uri)
            if (opened == null) {
                loading = false
                Toast.makeText(
                    context,
                    "Could not open this PDF. It may be password protected or damaged.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                val displayName = fileNameOf(context, uri)
                val thumb = Thumbnails.create(context, opened, uri.toString())
                store.recordOpened(uri.toString(), displayName, thumb)
                loading = false
                engine?.close()
                engine = opened
                title = displayName
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            openUri(uri)
        }
    }

    LaunchedEffect(incomingUri) {
        if (incomingUri != null) {
            openUri(incomingUri)
        }
    }

    val current = engine
    if (current != null) {
        ViewerScreen(
            engine = current,
            title = title,
            onBack = {
                current.close()
                engine = null
            }
        )
    } else if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                if (selected == 0) {
                    FloatingActionButton(
                        onClick = { picker.launch(arrayOf("application/pdf")) }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Open PDF")
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (selected == 0) {
                    HomeScreen(
                        onOpen = { openUri(it) },
                        onOpenSettings = { showSettings = true }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tabs[selected].label,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                if (loading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
