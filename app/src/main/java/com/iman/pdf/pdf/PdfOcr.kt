package com.iman.pdf.pdf

import android.graphics.Bitmap
import android.graphics.Rect
import com.iman.pdf.core.pdf.MuPdfEngine
import android.content.Context
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object PdfOcr {
    // Created once and reused for every page (creating them per page was slow and heavy).
    private val latin by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val devanagari by lazy {
        TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }

    suspend fun recognize(context: Context, file: java.io.File, pageIndex: Int): OcrPage? =
        withContext(Dispatchers.IO) {
            val engine = MuPdfEngine()
            var bitmap: Bitmap? = null
            try {
                if (!engine.openDocument(file.absolutePath)) return@withContext null
                bitmap = engine.renderPageToBitmap(pageIndex, 2f) ?: return@withContext null
                val imageWidth = bitmap.width
                val imageHeight = bitmap.height
                val input = InputImage.fromBitmap(bitmap, 0)
                val latinResult = process(latin, input)
                val devanagariResult = process(devanagari, input)
                val lines = ArrayList<OcrLine>()
                lines.addAll(toLines(latinResult, imageWidth, imageHeight))
                toLines(devanagariResult, imageWidth, imageHeight).forEach { candidate ->
                    val duplicate = lines.indexOfFirst { existing -> overlap(existing, candidate) > 0.55f }
                    if (duplicate < 0) lines.add(candidate)
                    else {
                        val existing = lines[duplicate]
                        val merged = listOf(existing.text, candidate.text)
                            .distinctBy { it.lowercase() }.joinToString(" ")
                        lines[duplicate] = existing.copy(text = merged)
                    }
                }
                OcrPage(pageIndex, lines.sortedWith(compareBy<OcrLine> { it.top }.thenBy { it.left }))
                    .takeIf { it.lines.isNotEmpty() }
            } catch (_: Exception) {
                null
            } finally {
                bitmap?.recycle()
                engine.closeDocument()
            }
        }

    private suspend fun process(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        image: InputImage
    ): Text = suspendCancellableCoroutine { continuation ->
        recognizer.process(image)
            .addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(result)
            }
            .addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
    }

    private fun toLines(result: Text, width: Int, height: Int): List<OcrLine> {
        val output = ArrayList<OcrLine>()
        result.textBlocks.forEach { block ->
            block.lines.forEach { line ->
                val bounds: Rect? = line.boundingBox
                val text = line.text.trim()
                if (bounds != null && text.isNotEmpty()) {
                    output.add(
                        OcrLine(
                            text,
                            (bounds.left.toFloat() / width).coerceIn(0f, 1f),
                            (bounds.top.toFloat() / height).coerceIn(0f, 1f),
                            (bounds.right.toFloat() / width).coerceIn(0f, 1f),
                            (bounds.bottom.toFloat() / height).coerceIn(0f, 1f)
                        )
                    )
                }
            }
        }
        return output
    }

    private fun overlap(first: OcrLine, second: OcrLine): Float {
        val left = maxOf(first.left, second.left)
        val top = maxOf(first.top, second.top)
        val right = minOf(first.right, second.right)
        val bottom = minOf(first.bottom, second.bottom)
        val intersection = (right - left).coerceAtLeast(0f) *
            (bottom - top).coerceAtLeast(0f)
        val firstArea = (first.right - first.left) * (first.bottom - first.top)
        val secondArea = (second.right - second.left) * (second.bottom - second.top)
        val union = firstArea + secondArea - intersection
        return if (union <= 0f) 0f else intersection / union
    }
}
