package com.iman.pdf.ui

import android.content.Context
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iman.pdf.pdf.PdfActions
import com.iman.pdf.pdf.PdfCompress
import com.iman.pdf.pdf.UriActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private val levelNames = listOf(
    "Low compression (best quality)",
    "Medium compression",
    "High compression (smallest file)"
)
private val levelDpi = listOf(150, 110, 80)
private val levelQuality = listOf(80, 65, 50)

private fun compressNameOf(context: Context, uri: Uri): String {
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

private fun sizeText(bytes: Long): String {
    val kb = bytes / 1024.0
    if (kb < 1024.0) {
        return String.format(Locale.US, "%.0f KB", kb)
    }
    return String.format(Locale.US, "%.1f MB", kb / 1024.0)
}

@Composable
fun CompressScreen(
    onBack: () -> Unit,
    onOpen: (Uri) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceFile by remember { mutableStateOf<File?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var level by remember { mutableIntStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var progressDone by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<File?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            sourceFile?.delete()
            result?.delete()
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val copy = UriActions.copyToCache(context, uri)
                busy = false
                if (copy == null) {
                    Toast.makeText(context, "Could not open this PDF", Toast.LENGTH_LONG).show()
                } else {
                    sourceFile?.delete()
                    result?.delete()
                    result = null
                    sourceFile = copy
                    sourceName = compressNameOf(context, uri)
                }
            }
        }
    }

    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { destination ->
        val file = result
        if (destination != null && file != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    PdfActions.saveCopy(context, file, destination)
                }
                if (ok) {
                    Toast.makeText(context, "Compressed PDF saved", Toast.LENGTH_SHORT).show()
                    onOpen(destination)
                } else {
                    Toast.makeText(context, "Could not save the PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val source = sourceFile
    val compressed = result
    val smaller = source != null && compressed != null && compressed.length() < source.length()

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
                    text = "Compress PDF",
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
                    Text("Choose a PDF")
                }

                if (source != null) {
                    Text(
                        text = sourceName,
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Original size: " + sizeText(source.length()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Choose a level",
                        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    levelNames.forEachIndexed { index, name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !busy) {
                                    level = index
                                    result?.delete()
                                    result = null
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = level == index, onClick = null)
                            Text(
                                text = name,
                                modifier = Modifier.padding(start = 12.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    Text(
                        text = "Note: pages become images, so text in the new PDF cannot be " +
                            "selected or searched. Your original file stays unchanged.",
                        modifier = Modifier.padding(top = 12.dp),
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            if (progressTotal > 0) {
                                Text(
                                    text = "Page " + progressDone + " of " + progressTotal,
                                    modifier = Modifier.padding(top = 12.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                if (source != null && compressed != null && !busy) {
                    val saved = source.length() - compressed.length()
                    val percent = if (source.length() > 0L) {
                        (saved * 100L / source.length()).toInt()
                    } else {
                        0
                    }
                    Text(
                        text = sizeText(source.length()) + "  →  " + sizeText(compressed.length()),
                        modifier = Modifier.padding(top = 20.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (smaller) {
                            percent.toString() + "% smaller"
                        } else {
                            "This level could not make the file smaller. " +
                                "Try a higher compression level."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (source != null) {
                if (smaller) {
                    Button(
                        onClick = {
                            saver.launch(sourceName.removeSuffix(".pdf") + "_compressed.pdf")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp)
                    ) {
                        Text("Save compressed PDF")
                    }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                progressDone = 0
                                progressTotal = 0
                                val made = PdfCompress.apply(
                                    context,
                                    source,
                                    levelDpi[level],
                                    levelQuality[level]
                                ) { done, total ->
                                    progressDone = done
                                    progressTotal = total
                                }
                                busy = false
                                if (made == null) {
                                    Toast.makeText(
                                        context,
                                        "Could not compress this PDF",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    result?.delete()
                                    result = made
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp)
                    ) {
                        Text("Compress")
                    }
                }
            }
        }
    }
}
