package com.iman.pdf.ui

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iman.pdf.pdf.PdfEngine
import com.iman.pdf.pdf.PdfSplit
import kotlinx.coroutines.launch

@Composable
fun SplitScreen(
    onBack: () -> Unit,
    onOpen: (Uri) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var engine by remember { mutableStateOf<PdfEngine?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(0) }
    var everyText by remember { mutableStateOf("1") }
    var rangeText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pendingFiles by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
    var pendingRanges by remember { mutableStateOf<List<IntRange>>(emptyList()) }

    DisposableEffect(Unit) {
        onDispose { engine?.close() }
    }

    val onPicked: (Uri?) -> Unit = { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val opened = PdfEngine.open(context, uri)
                busy = false
                if (opened == null) {
                    Toast.makeText(context, "Could not open this PDF", Toast.LENGTH_LONG).show()
                } else {
                    engine?.close()
                    engine = opened
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
                                if (column >= 0) name = cursor.getString(column)
                            }
                        }
                    } catch (_: Exception) {
                    }
                    sourceName = name ?: "Document.pdf"
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

    val folder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { tree ->
        val files = pendingFiles
        val ranges = pendingRanges
        if (tree != null && files.isNotEmpty()) {
            scope.launch {
                busy = true
                val saved = PdfSplit.writeToFolder(
                    context,
                    files,
                    ranges,
                    tree,
                    sourceName.removeSuffix(".pdf")
                )
                busy = false
                files.forEach { it.delete() }
                pendingFiles = emptyList()
                Toast.makeText(
                    context,
                    if (saved == files.size) {
                        saved.toString() + " PDF files saved"
                    } else {
                        saved.toString() + " of " + files.size + " files saved"
                    },
                    Toast.LENGTH_LONG
                ).show()
            }
        }
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
                    text = "Split PDF",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("application/pdf")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (current == null) "Choose a PDF" else "Change PDF")
                }

                if (current != null) {
                    Text(
                        text = sourceName + "  •  " + current.pageCount + " pages",
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { mode = 0 }
                            .padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = mode == 0, onClick = null)
                        Text("Every N pages", modifier = Modifier.padding(start = 12.dp))
                    }
                    if (mode == 0) {
                        OutlinedTextField(
                            value = everyText,
                            onValueChange = { everyText = it.filter(Char::isDigit) },
                            label = { Text("Pages in each file") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { mode = 1 }
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = mode == 1, onClick = null)
                        Text("Page ranges", modifier = Modifier.padding(start = 12.dp))
                    }
                    if (mode == 1) {
                        OutlinedTextField(
                            value = rangeText,
                            onValueChange = { rangeText = it },
                            label = { Text("Example: 1-3, 4-6, 8") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }

                    Text(
                        text = "Each part is saved as a separate PDF in the folder you choose. " +
                            "Your original file stays unchanged.",
                        modifier = Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (busy) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            if (current != null) {
                Button(
                    onClick = {
                        val ranges = if (mode == 0) {
                            val n = everyText.toIntOrNull() ?: 0
                            if (n < 1) null else PdfSplit.everyN(n, current.pageCount)
                        } else {
                            PdfSplit.parseRanges(rangeText, current.pageCount)
                        }
                        if (ranges == null) {
                            Toast.makeText(
                                context,
                                "Check the numbers. Pages must be between 1 and " + current.pageCount,
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            scope.launch {
                                busy = true
                                val files = PdfSplit.split(context, current.file, ranges)
                                busy = false
                                if (files.isEmpty()) {
                                    Toast.makeText(
                                        context,
                                        "Could not split this PDF",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    pendingFiles = files
                                    pendingRanges = ranges
                                    folder.launch(null)
                                }
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    Text("Split and choose folder")
                }
            }
        }
    }
}
