package com.iman.pdf.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iman.pdf.annot.AnnotBake
import com.iman.pdf.annot.AnnotController
import com.iman.pdf.pdf.PdfActions
import com.iman.pdf.pdf.PdfEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Unsaved changes, saving and the "save before leaving" flow. */
internal class ViewerSaving(
    private val context: Context,
    private val scope: CoroutineScope,
    private val engine: PdfEngine,
    private val annot: AnnotController
) {
    /** Where "Save to this PDF" writes; null when the original must not be overwritten. */
    var originalUri: Uri? = null
    var fileName: String = "document.pdf"
    var launchCopy: (String) -> Unit = {}

    /** Increases whenever the working copy changes, so pages are rendered again. */
    var annotationRevision by mutableIntStateOf(0)
        private set
    var busy by mutableStateOf(false)
        private set

    // "unsaved" = the working copy differs from what the user has saved; pending marks count too.
    private var unsaved by mutableStateOf(false)
    var saveDialogOpen by mutableStateOf(false)
        private set
    var exitDialogOpen by mutableStateOf(false)
        private set
    private var pendingExit: (() -> Unit)? = null
    private var afterSave: (() -> Unit)? = null

    fun markChanged() {
        unsaved = true
        annotationRevision++
    }

    private fun finishSave() {
        unsaved = false
        val next = afterSave
        afterSave = null
        next?.invoke()
    }

    fun onCopyPicked(uri: Uri?) {
        if (uri == null) {
            afterSave = null
            return
        }
        scope.launch {
            busy = true
            val ok = withContext(Dispatchers.IO) {
                PdfActions.saveCopy(context, engine.file, uri)
            }
            busy = false
            Toast.makeText(
                context,
                if (ok) "Saved successfully." else "Could not save the PDF.",
                Toast.LENGTH_SHORT
            ).show()
            if (ok) {
                finishSave()
            } else {
                afterSave = null
            }
        }
    }

    /** Writes pending marks into the working copy. */
    private suspend fun commitMarks(): Boolean {
        val pending = annot.marks.toList()
        if (pending.isEmpty()) return true
        val baked = AnnotBake.bake(context, engine.file, pending) ?: return false
        val replaced = withContext(Dispatchers.IO) { engine.replaceWith(baked) }
        baked.delete()
        if (replaced) {
            annot.clearAll()
            markChanged()
        }
        return replaced
    }

    fun save(overwrite: Boolean) {
        saveDialogOpen = false
        if (busy) return
        scope.launch {
            busy = true
            if (!commitMarks()) {
                busy = false
                afterSave = null
                Toast.makeText(context, "Could not save your edits.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val target = originalUri
            if (overwrite && target != null) {
                val ok = withContext(Dispatchers.IO) {
                    PdfActions.saveCopyToUri(context, engine.file, target)
                }
                busy = false
                if (ok) {
                    Toast.makeText(context, "Saved to this PDF.", Toast.LENGTH_SHORT).show()
                    finishSave()
                } else {
                    Toast.makeText(
                        context,
                        "This PDF cannot be changed here. Save a new copy.",
                        Toast.LENGTH_LONG
                    ).show()
                    launchCopy(fileName)
                }
            } else {
                busy = false
                launchCopy(fileName)
            }
        }
    }

    fun requestSave() {
        afterSave = null
        saveDialogOpen = true
    }

    fun cancelSave() {
        saveDialogOpen = false
        afterSave = null
    }

    /** Runs [action] (which leaves the viewer), asking first if changes would be lost. */
    fun requestExit(action: () -> Unit) {
        if (unsaved || annot.marks.isNotEmpty()) {
            pendingExit = action
            exitDialogOpen = true
        } else {
            action()
        }
    }

    fun dismissExit() {
        exitDialogOpen = false
    }

    fun saveThenExit() {
        exitDialogOpen = false
        afterSave = pendingExit
        saveDialogOpen = true
    }

    fun discardAndExit() {
        exitDialogOpen = false
        annot.clearAll()
        unsaved = false
        pendingExit?.invoke()
    }

    /** Gives [use] a file that includes marks not saved yet (for Share and Print). */
    fun withCurrentPdf(use: (File) -> Unit) {
        val pending = annot.marks.toList()
        if (pending.isEmpty()) {
            use(engine.file)
            return
        }
        scope.launch {
            busy = true
            val baked = AnnotBake.bake(context, engine.file, pending)
            busy = false
            if (baked == null) {
                Toast.makeText(context, "Could not include your edits.", Toast.LENGTH_LONG).show()
            } else {
                use(baked)
            }
        }
    }

    fun share(title: String) = withCurrentPdf { PdfActions.share(context, it, title) }

    fun print(title: String) = withCurrentPdf { PdfActions.print(context, it, title) }
}

@Composable
internal fun rememberViewerSaving(
    engine: PdfEngine,
    annot: AnnotController,
    originalUri: Uri?,
    fileName: String
): ViewerSaving {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { ViewerSaving(context, scope, engine, annot) }
    state.originalUri = originalUri
    state.fileName = fileName
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> state.onCopyPicked(uri) }
    state.launchCopy = { launcher.launch(it) }
    return state
}

@Composable
internal fun ViewerSaveDialogs(state: ViewerSaving) {
    if (state.exitDialogOpen) {
        AlertDialog(
            onDismissRequest = { state.dismissExit() },
            title = { Text("Save changes?") },
            text = { Text("This PDF has changes that are not saved.") },
            confirmButton = {
                TextButton(onClick = { state.saveThenExit() }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { state.discardAndExit() }) { Text("Discard") }
            }
        )
    }
    if (state.saveDialogOpen) {
        val canOverwrite = state.originalUri != null
        AlertDialog(
            onDismissRequest = { state.cancelSave() },
            title = { Text("Save PDF") },
            text = {
                Text(
                    if (canOverwrite) {
                        "Save into this PDF, or keep it unchanged and save a new copy."
                    } else {
                        "This PDF was unlocked with a password. The new copy will not have a password."
                    }
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (canOverwrite) {
                        TextButton(onClick = { state.save(overwrite = true) }) {
                            Text("Save to this PDF")
                        }
                    }
                    TextButton(onClick = { state.save(overwrite = false) }) {
                        Text("Save as new copy")
                    }
                    TextButton(onClick = { state.cancelSave() }) { Text("Cancel") }
                }
            }
        )
    }
    if (state.busy) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Text("Saving PDF...", modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}
