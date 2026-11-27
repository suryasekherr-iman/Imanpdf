package com.iman.pdf.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.iman.pdf.core.ocr.OcrEngine
import com.iman.pdf.pdf.OcrLine
import com.iman.pdf.pdf.OcrPage
import com.iman.pdf.pdf.OcrStore
import com.iman.pdf.pdf.PdfEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Text recognition for scanned pages. */
internal class ViewerOcr(
    private val context: Context,
    private val scope: CoroutineScope,
    private val engine: PdfEngine,
    private val documentId: String,
    private val listState: LazyListState
) {
    var revision by mutableIntStateOf(0)
        private set
    var processing by mutableStateOf(false)
        private set
    var extractedText by mutableStateOf<String?>(null)
        private set

    suspend fun loadPageText(page: Int) {
        extractedText = withContext(Dispatchers.IO) {
            OcrStore.loadPage(context, documentId, page)?.text
        }
    }

    private suspend fun recognizeAndStorePage(page: Int): String? {
        val bitmap = withContext(Dispatchers.IO) {
            engine.renderPageToBitmap(page, 2f)
        } ?: throw IllegalStateException("MuPDF could not render this page for OCR.")

        try {
            val recognizedLines = OcrEngine.extractLinesFromBitmap(bitmap)
            if (recognizedLines.isEmpty()) return null

            val ocrPage = OcrPage(
                page = page,
                lines = recognizedLines.map { line ->
                    OcrLine(
                        text = line.text,
                        left = line.left,
                        top = line.top,
                        right = line.right,
                        bottom = line.bottom
                    )
                }
            )
            val saved = withContext(Dispatchers.IO) {
                OcrStore.savePage(context, documentId, ocrPage)
            }
            if (!saved) throw IllegalStateException("OCR text could not be saved.")
            if (page == listState.firstVisibleItemIndex) {
                extractedText = ocrPage.text
            }
            return ocrPage.text
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    fun run(pages: IntRange) {
        if (processing || pages.isEmpty()) return
        scope.launch {
            processing = true
            var completed = 0
            var unreadable = 0
            var failed = 0
            try {
                for (page in pages) {
                    try {
                        if (recognizeAndStorePage(page).isNullOrBlank()) {
                            unreadable++
                        } else {
                            completed++
                        }
                    } catch (e: Exception) {
                        com.iman.pdf.logSwallowed("ViewerOcr:run", e)
                        failed++
                    }
                }
            } finally {
                revision++
                processing = false
            }

            val message = when {
                completed == 0 && failed > 0 ->
                    "OCR failed. Please try again with a clearer page."
                completed == 0 ->
                    "OCR could not detect readable text. Try a clearer scan."
                unreadable + failed > 0 ->
                    "OCR completed for $completed page(s); ${unreadable + failed} page(s) had low-quality text or failed."
                else -> "OCR Complete! Text is now searchable."
            }
            Toast.makeText(
                context,
                message,
                if (pages.count() > 1) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun runCurrentPage() {
        if (engine.pageCount <= 0) return
        val page = listState.firstVisibleItemIndex.coerceIn(0, engine.pageCount - 1)
        run(page..page)
    }

    fun runDocument() = run(0 until engine.pageCount)
}

@Composable
internal fun ViewerOcrOverlay(ocr: ViewerOcr) {
    if (!ocr.processing) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Text(
                text = "Recognizing PDF text...",
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
