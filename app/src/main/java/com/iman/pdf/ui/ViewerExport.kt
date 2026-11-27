package com.iman.pdf.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.iman.pdf.annot.AnnotBake
import com.iman.pdf.annot.AnnotController
import com.iman.pdf.pdf.PdfEngine
import com.iman.pdf.pdf.PdfExport
import kotlinx.coroutines.launch

/** Export dialog (text file or page images). Must stay composed so picker results arrive. */
@Composable
internal fun ViewerExport(
    open: Boolean,
    engine: PdfEngine,
    documentId: String,
    baseName: String,
    annot: AnnotController,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportText by remember { mutableStateOf("") }
    val textLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = PdfExport.writeText(context, uri, exportText)
                Toast.makeText(
                    context,
                    if (ok) "Text file saved" else "Could not save the text file",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                Toast.makeText(context, "Exporting pages...", Toast.LENGTH_SHORT).show()
                val saved = try {
                    val pending = annot.marks.toList()
                    if (pending.isEmpty()) {
                        PdfExport.writeImages(context, engine, uri, baseName)
                    } else {
                        val baked = checkNotNull(AnnotBake.bake(context, engine.file, pending))
                        val marked = checkNotNull(PdfEngine.open(context, Uri.fromFile(baked)))
                        try {
                            PdfExport.writeImages(context, marked, uri, baseName)
                        } finally {
                            marked.close()
                        }
                    }
                } catch (e: Exception) {
                    com.iman.pdf.logSwallowed("ViewerExport:images", e)
                    0
                }
                Toast.makeText(
                    context,
                    if (saved == engine.pageCount) {
                        "$saved images saved"
                    } else {
                        "$saved of ${engine.pageCount} images saved"
                    },
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    if (open) {
        ExportDialog(
            onText = {
                onDismiss()
                scope.launch {
                    val text = PdfExport.allText(context, engine.file, documentId)
                    if (text.isBlank()) {
                        Toast.makeText(
                            context,
                            "No text found in this PDF (it may be a scan)",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        exportText = text
                        textLauncher.launch("$baseName.txt")
                    }
                }
            },
            onImages = {
                onDismiss()
                folderLauncher.launch(null)
            },
            onDismiss = onDismiss
        )
    }
}
