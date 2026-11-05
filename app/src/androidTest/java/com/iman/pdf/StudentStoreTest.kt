package com.iman.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iman.pdf.data.StudentStore
import com.iman.pdf.data.StudyHighlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudentStoreTest {
    private val context by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext
    }
    private val store by lazy { StudentStore(context) }

    @Before
    fun clearStudentData() {
        context.getSharedPreferences("student_spaces", 0).edit().clear().commit()
    }

    @Test
    fun notesKeepTheirPdfPageAndDocumentLink() {
        store.addNote("Formula", "Area = length × width", 4, "content://docs/math")

        val note = store.notes().single()
        assertEquals("Formula", note.title)
        assertEquals(4, note.page)
        assertEquals("content://docs/math", note.documentId)
    }

    @Test
    fun highlightsAreReplacedPerDocumentWithoutRemovingOtherDocuments() {
        store.replaceHighlights(
            "doc-one",
            "One.pdf",
            listOf(StudyHighlight("old", "One.pdf", 1, "HIGHLIGHT", "Old text"))
        )
        store.replaceHighlights(
            "doc-two",
            "Two.pdf",
            listOf(StudyHighlight("old", "Two.pdf", 2, "HIGHLIGHT", "Other document"))
        )
        store.replaceHighlights(
            "doc-one",
            "One.pdf",
            listOf(StudyHighlight("new", "One.pdf", 3, "HIGHLIGHT", "Updated text"))
        )

        val highlights = store.highlights()
        assertEquals(2, highlights.size)
        assertTrue(highlights.any { it.documentTitle == "One.pdf" && it.text == "Updated text" })
        assertTrue(highlights.any { it.documentTitle == "Two.pdf" && it.text == "Other document" })
        assertFalse(highlights.any { it.text == "Old text" })
    }

    @Test
    fun revisionCompletionAndFlashcardDeletionPersist() {
        store.addRevisionItem("Chapter 2")
        store.addFlashcard("Define force", "Mass × acceleration")

        val revision = store.revisionItems().single()
        val flashcard = store.flashcards().single()
        store.setRevisionDone(revision.id, true)
        store.removeFlashcard(flashcard.id)

        assertTrue(store.revisionItems().single().done)
        assertTrue(store.flashcards().isEmpty())
    }

    @Test
    fun bookmarksAreUniquePerDocumentAndPage() {
        store.addBookmark("doc-one", "One.pdf", 5)
        store.addBookmark("doc-one", "One.pdf", 5)
        store.addBookmark("doc-two", "Two.pdf", 5)

        val bookmarks = store.bookmarks()
        assertEquals(2, bookmarks.size)
        assertTrue(bookmarks.any { it.id == "doc-one|5" })
        assertTrue(bookmarks.any { it.id == "doc-two|5" })
    }
}
