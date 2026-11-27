package com.iman.pdf.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iman.pdf.annot.AnnotController
import com.iman.pdf.pdf.Bookmarks
import com.iman.pdf.pdf.CommentItem
import com.iman.pdf.pdf.Comments
import com.iman.pdf.pdf.PdfEngine
import com.iman.pdf.pdf.PdfFormField
import com.iman.pdf.pdf.PdfFormFieldUpdate
import com.iman.pdf.pdf.TocEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** View settings of the open document. */
internal class ViewerDisplay {
    var keepScreenOn by mutableStateOf(false)
    var nightMode by mutableStateOf(false)
    var sepiaMode by mutableStateOf(false)
    var pageSnap by mutableStateOf(false)
}

/** Sheets and dialogs opened from the viewer menus. */
internal class ViewerPanels(
    private val context: Context,
    private val scope: CoroutineScope,
    private val engine: PdfEngine,
    private val documentId: String,
    private val annot: AnnotController
) {
    var exportOpen by mutableStateOf(false)
    var viewSettingsOpen by mutableStateOf(false)
    var bookmarksOpen by mutableStateOf(false)
    private var bookmarkList by mutableStateOf(
        Bookmarks.load(context, documentId, engine.pageCount)
    )
    private var tocList by mutableStateOf<List<TocEntry>>(emptyList())
    private var tocLoading by mutableStateOf(false)
    private var commentsOpen by mutableStateOf(false)
    private var commentsLoading by mutableStateOf(false)
    private var commentItems by mutableStateOf<List<CommentItem>>(emptyList())
    private var formDialogOpen by mutableStateOf(false)
    private var formLoading by mutableStateOf(false)
    private var formSaving by mutableStateOf(false)
    private var formFields by mutableStateOf<List<PdfFormField>>(emptyList())

    fun openComments() {
        commentsOpen = true
        commentsLoading = true
        scope.launch {
            val saved = Comments.load(context, engine.file)
            commentItems = Comments.fromMarks(annot.marks.toList()) + saved
            commentsLoading = false
        }
    }

    fun openBookmarks() {
        bookmarksOpen = true
        scope.launch {
            if (tocList.isEmpty()) {
                tocLoading = true
                tocList = Bookmarks.toc(context, engine.file)
                tocLoading = false
            }
        }
    }

    fun addBookmark(page: Int) {
        val added = Bookmarks.add(context, documentId, engine.pageCount, page)
        bookmarkList = Bookmarks.load(context, documentId, engine.pageCount)
        Toast.makeText(
            context,
            if (added) {
                "Bookmark added: page " + (page + 1)
            } else {
                "Page " + (page + 1) + " is already bookmarked"
            },
            Toast.LENGTH_SHORT
        ).show()
    }

    fun openFormFill() {
        if (formLoading || formSaving) return
        formLoading = true
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                (0 until engine.pageCount).flatMap { page -> engine.formFields(page) }
            }
            formFields = loaded
            formLoading = false
            formDialogOpen = true
            if (loaded.isEmpty()) {
                Toast.makeText(context, "No interactive PDF form fields found.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveForm(updates: List<PdfFormFieldUpdate>, onSaved: () -> Unit) {
        if (updates.isEmpty() || formSaving) return
        scope.launch {
            formSaving = true
            val saved = withContext(Dispatchers.IO) {
                try {
                    engine.updateFormFields(updates) &&
                        engine.saveDocument(engine.file.absolutePath)
                } catch (e: Exception) {
                    com.iman.pdf.logSwallowed("ViewerPanels:saveForm", e)
                    false
                }
            }
            formSaving = false
            if (saved) {
                onSaved()
                formDialogOpen = false
                Toast.makeText(context, "Form updated. Tap Save to keep it.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Could not save form values. Check that the PDF allows editing.", Toast.LENGTH_LONG).show()
            }
        }
    }

    @Composable
    fun Sheets(display: ViewerDisplay, listState: LazyListState, onFormSaved: () -> Unit) {
        if (bookmarksOpen) {
            BookmarksSheet(
                bookmarks = bookmarkList,
                toc = tocList,
                tocLoading = tocLoading,
                notes = bookmarkList.associateWith { page ->
                    Bookmarks.note(context, documentId, engine.pageCount, page)
                },
                onSaveNote = { page, note ->
                    Bookmarks.saveNote(context, documentId, engine.pageCount, page, note)
                },
                onJump = { page ->
                    bookmarksOpen = false
                    scope.launch { listState.scrollToItem(page) }
                },
                onRemove = { page ->
                    Bookmarks.remove(context, documentId, engine.pageCount, page)
                    bookmarkList = Bookmarks.load(context, documentId, engine.pageCount)
                },
                onDismiss = { bookmarksOpen = false }
            )
        }

        if (formDialogOpen) {
            PdfFormFillDialog(
                fields = formFields,
                saving = formSaving,
                onDismiss = { formDialogOpen = false },
                onSave = { updates -> saveForm(updates, onFormSaved) }
            )
        }

        if (commentsOpen) {
            CommentsSheet(
                items = commentItems,
                loading = commentsLoading,
                onJump = { page ->
                    commentsOpen = false
                    scope.launch { listState.scrollToItem(page) }
                },
                onDismiss = { commentsOpen = false }
            )
        }

        if (viewSettingsOpen) {
            ViewSettingsSheet(
                keepScreenOn = display.keepScreenOn,
                nightMode = display.nightMode,
                sepiaMode = display.sepiaMode,
                onKeepScreenOn = { display.keepScreenOn = it },
                onNightMode = {
                    display.nightMode = it
                    if (it) display.sepiaMode = false
                },
                onSepiaMode = {
                    display.sepiaMode = it
                    if (it) display.nightMode = false
                },
                onDismiss = { viewSettingsOpen = false }
            )
        }
    }
}
