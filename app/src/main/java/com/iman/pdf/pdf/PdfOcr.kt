package com.iman.pdf.pdf

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.content.Context
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object PdfOcr {
    suspend fun recognize(context: Context, file: File, pageIndex: Int): OcrPage? =
        withContext(Dispatchers.IO) {
            var descriptor: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            var bitmap: Bitmap? = null
            val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val devanagari = TextRecognition.getClient(
                DevanagariTextRecognizerOptions.Builder().build()
            )
            try {
                descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(descriptor)
                if (pageIndex !in 0 until renderer.pageCount) return@withContext null
                val page = renderer.openPage(pageIndex)
                try {
                    val width = (page.width * 2).coerceIn(900, 1800)
                    val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap!!.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                } finally {
                    page.close()
                }

                val input = InputImage.fromBitmap(bitmap!!, 0)
                val latinResult = process(latin, input)
                val devanagariResult = process(devanagari, input)
                val lines = ArrayList<OcrLine>()
                lines.addAll(toLines(latinResult, width, height))
                toLines(devanagariResult, width, height).forEach { candidate ->
                    val duplicate = lines.indexOfFirst { existing ->
                        overlap(existing, candidate) > 0.55f
                    }
                    if (duplicate < 0) {
                        lines.add(candidate)
                    } else if (candidate.text.length > lines[duplicate].text.length) {
                        lines[duplicate] = candidate
                    }
                }
                OcrPage(pageIndex, lines.sortedWith(compareBy<OcrLine> { it.top }.thenBy { it.left }))
                    .takeIf { it.lines.isNotEmpty() }
            } catch (_: Exception) {
                null
            } finally {
                bitmap?.recycle()
                latin.close()
                devanagari.close()
                try { renderer?.close() } catch (_: Exception) {}
                try { descriptor?.close() } catch (_: Exception) {}
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
                val bounds: Rect = line.boundingBox ?: return@forEach
                val text = line.text.trim()
                if (text.isNotEmpty()) {
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
