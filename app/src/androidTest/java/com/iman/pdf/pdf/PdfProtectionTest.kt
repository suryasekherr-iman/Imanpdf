package com.iman.pdf.pdf

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artifex.mupdf.fitz.Document
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfProtectionTest {

    @Test
    fun protectedPdfRequiresPasswordAndOpensWithCorrectPassword() {
        runBlocking<Unit> {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val source = createSamplePdf(context)
            val interopFixture = File(context.filesDir, "protection-regression-output.pdf")
            interopFixture.delete()
            var protected: File? = null
            try {
                val password = "Test,=\"pass\\word!42"
                protected = PdfTools.protect(context, Uri.fromFile(source), password)
                assertNotNull("Protection failed: ${PdfTools.lastProtectError}", protected)

                val output = requireNotNull(protected)
                assertTrue("Protected output must be non-empty", output.isFile && output.length() > 0L)
                assertTrue(
                    "Protected output must be encrypted",
                    PdfTools.verifyProtected(context, Uri.fromFile(output), password)
                )

                var wrongPasswordDocument: Document? = null
                try {
                    wrongPasswordDocument = Document.openDocument(output.absolutePath)
                    assertTrue("PDF must require a password", wrongPasswordDocument.needsPassword())
                    assertFalse(
                        "Incorrect password must be rejected",
                        wrongPasswordDocument.authenticatePassword("definitely-wrong")
                    )
                } finally {
                    wrongPasswordDocument?.destroy()
                }

                var correctPasswordDocument: Document? = null
                try {
                    correctPasswordDocument = Document.openDocument(output.absolutePath)
                    assertTrue(
                        "Correct password must be accepted",
                        correctPasswordDocument.authenticatePassword(password)
                    )
                    assertTrue("All pages must remain readable", correctPasswordDocument.countPages() == 1)
                    output.copyTo(interopFixture, overwrite = true)
                } finally {
                    correctPasswordDocument?.destroy()
                }
            } finally {
                protected?.delete()
                source.delete()
            }
        }
    }

    @Test
    fun invalidPasswordInputDoesNotCreateProtectedOutput() {
        runBlocking<Unit> {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val source = createSamplePdf(context)
            try {
                assertNull(PdfTools.protect(context, Uri.fromFile(source), ""))
            } finally {
                source.delete()
            }
        }
    }

    private fun createSamplePdf(context: Context): File {
        val file = File(context.cacheDir, "protection_test_${System.nanoTime()}.pdf")
        val document = PdfDocument()
        try {
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(300, 400, 1).create()
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f }
            page.canvas.drawText("PDF protection regression test", 24f, 48f, paint)
            document.finishPage(page)
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        return file
    }
}
