package com.iman.pdf.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.iman.pdf.data.FileStore
import com.iman.pdf.pdf.PdfEngine
import com.iman.pdf.pdf.PdfTools
import com.iman.pdf.pdf.Thumbnails
import kotlinx.coroutines.launch
import java.io.File

private val viewerTools = listOf("fill_sign", "comment", "liquid", "read_aloud")

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
    var tool by rememberSaveable { mutableStateOf("") }
    var engine by remember { mutableStateOf<PdfEngine?>(null) }
    var title by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var passwordUri by remember { mutableStateOf<Uri?>(null) }
    var passwordWrong by remember { mutableStateOf(false) }
    var pendingMode by remember { mutableStateOf("") }
    var viewerMode by remember { mutableStateOf("") }
    val tabs = Tab.entries

    fun openUri(uri: Uri, unlocked: File? = null) {
        scope.launch {
            loading = true
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            val source = if (unlocked != null) Uri.fromFile(unlocked) else uri
            val opened = PdfEngine.open(context, source)
            if (opened == null) {
                loading = false
                if (unlocked == null && PdfTools.needsPassword(context, uri)) {
                    passwordWrong = false
                    passwordUri = uri
                } else {
                    Toast.makeText(
                        context,
                        "Could not open this PDF. It may be damaged.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                val displayName = fileNameOf(context, uri)
                val thumb = Thumbnails.create(context, opened, uri.toString())
                store.recordOpened(uri.toString(), displayName, thumb)
                loading = false
                engine?.close()
                viewerMode = pendingMode
                pendingMode = ""
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
        } else {
            pendingMode = ""
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
            startMode = viewerMode,
            onBack = {
                current.close()
                engine = null
            }
        )
    } else if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
    } else if (implementedTools.contains(tool)) {
        ToolHost(
            tool = tool,
            onClose = { tool = "" },
            onOpen = {
                tool = ""
                openUri(it)
            }
        )
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
                } else if (selected == 1) {
                    CreateScreen(
                        onAction = { id ->
                            if (implementedTools.contains(id)) {
                                tool = id
                            } else {
                                Toast.makeText(
                                    context,
                                    "This will be added soon",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                } else if (selected == 2) {
                    FilesScreen(
                        onOpen = { openUri(it) },
                        onBrowse = { picker.launch(arrayOf("application/pdf")) }
                    )
                } else {
                    ToolsScreen(
                        onToolClick = { item ->
                            if (viewerTools.contains(item.id)) {
                                pendingMode = item.id
                                picker.launch(arrayOf("application/pdf"))
                            } else if (implementedTools.contains(item.id)) {
                                tool = item.id
                            } else {
                                Toast.makeText(
                                    context,
                                    item.title + " will be added soon",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
                if (loading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    val askFor = passwordUri
    if (askFor != null) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { passwordUri = null },
            title = { Text("Password required") },
            text = {
                Column {
                    Text("Enter the password to open this PDF.")
                    OutlinedTextField(
                        value = typed,
                        onValueChange = {
                            typed = it
                            passwordWrong = false
                        },
                        singleLine = true,
                        isError = passwordWrong,
                        supportingText = {
                            if (passwordWrong) {
                                Text("Wrong password")
                            }
                        },
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val file = PdfTools.unlock(context, askFor, typed)
                            if (file == null) {
                                passwordWrong = true
                            } else {
                                passwordUri = null
                                openUri(askFor, file)
                            }
                        }
                    }
                ) {
                    Text("Open")
                }
            },
            dismissButton = {
                TextButton(onClick = { passwordUri = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
