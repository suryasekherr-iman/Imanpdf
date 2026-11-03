package com.iman.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Opens a generated 3-page PDF in the viewer and taps every viewer menu item.
 * A crash anywhere fails the test; each test also checks the screen that should appear.
 */
@RunWith(AndroidJUnit4::class)
class ViewerSmokeTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun openSamplePdf() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = makeSamplePdf(context)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setClassName(context.packageName, MainActivity::class.java.name)
            .setDataAndType(uri, "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        scenario = ActivityScenario.launch(intent)
        rule.waitUntil(30_000) {
            rule.onAllNodesWithContentDescription("Page 1").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun closeApp() {
        scenario?.close()
    }

    private fun makeSamplePdf(context: Context): File {
        val dir = File(context.cacheDir, "shared")
        dir.mkdirs()
        val file = File(dir, "smoke.pdf")
        val document = PdfDocument()
        val paint = Paint()
        paint.textSize = 24f
        for (number in 1..3) {
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, number).create())
            page.canvas.drawText("Smoke test page $number", 60f, 100f, paint)
            document.finishPage(page)
        }
        file.outputStream().use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun waitForText(text: String, substring: Boolean = false, millis: Long = 15_000) {
        rule.waitUntil(millis) {
            rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitUntilGone(text: String, millis: Long = 20_000) {
        rule.waitUntil(millis) {
            rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun openMenu() {
        rule.onNodeWithContentDescription("More").performClick()
        waitForText("Print")
    }

    private fun menuItem(label: String) {
        openMenu()
        rule.onNodeWithText(label).performClick()
    }

    @Test
    fun viewSettingsSheetOpens() {
        menuItem("View settings")
        waitForText("Keep screen on")
        waitForText("Night mode")
    }

    @Test
    fun commentListOpens() {
        menuItem("Comment List")
        waitForText("No comments in this PDF.")
    }

    @Test
    fun exportDialogOpensAndCancels() {
        menuItem("Export")
        waitForText("Text file (.txt)")
        waitForText("Images (JPG, one per page)")
        rule.onNodeWithText("Cancel").performClick()
        waitUntilGone("Text file (.txt)")
    }

    @Test
    fun bookmarksSheetOpensEmpty() {
        menuItem("Bookmarks & TOC")
        waitForText("No bookmarks yet", substring = true)
    }

    @Test
    fun addBookmarkThenShowsInList() {
        menuItem("Add bookmark")
        menuItem("Bookmarks & TOC")
        waitUntilGone("No bookmarks yet")
        waitForText("Page 1")
    }

    @Test
    fun compressOpensWithCurrentPdf() {
        menuItem("Compress PDF")
        // "Compress" button only exists when a PDF is already selected
        waitForText("Compress", millis = 20_000)
    }

    @Test
    fun passwordOpensWithCurrentPdf() {
        menuItem("Set password")
        waitForText("Password (at least", substring = true, millis = 20_000)
    }

    @Test
    fun pagesOpensWithCurrentPdf() {
        menuItem("Pages")
        // Pages = organize tool; its empty-state hint must disappear once the PDF is seeded
        waitUntilGone("Choose a PDF to rotate, delete or rearrange its pages.")
    }

    @Test
    fun editPdfOpens() {
        menuItem("Edit PDF")
        // Edit PDF screen shows its own "Choose PDF" button
        waitForText("Choose PDF")
    }
}
