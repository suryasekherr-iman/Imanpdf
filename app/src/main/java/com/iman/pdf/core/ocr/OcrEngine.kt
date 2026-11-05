package com.iman.pdf.core.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrTextLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/**
 * On-device OCR for rendered PDF pages.
 *
 * The default Latin recognizer and the Devanagari recognizer run locally on the device.
 * The structured result preserves normalized line bounds for selectable page overlays
 * and search-hit highlighting.
 */
object OcrEngine {
    private val latinRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    private val devanagariRecognizer by lazy {
        TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }

    fun extractTextFromBitmap(
        bitmap: Bitmap,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        extractLinesFromBitmap(
            bitmap = bitmap,
            onSuccess = { lines -> onSuccess(lines.joinToString("\n") { it.text }.trim()) },
            onFailure = onFailure
        )
    }

    fun extractLinesFromBitmap(
        bitmap: Bitmap,
        onSuccess: (List<OcrTextLine>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        try {
            require(!bitmap.isRecycled) { "The page bitmap has already been recycled." }
            val image = InputImage.fromBitmap(bitmap, 0)
            latinRecognizer.process(image)
                .addOnSuccessListener { latinResult ->
                    val latinLines = toLines(latinResult, bitmap.width, bitmap.height)
                    try {
                        devanagariRecognizer.process(image)
                            .addOnSuccessListener { devanagariResult ->
                                onSuccess(
                                    mergeLines(
                                        latinLines,
                                        toLines(devanagariResult, bitmap.width, bitmap.height)
                                    )
                                )
                            }
                            .addOnFailureListener {
                                // Keep usable Latin output if the secondary recognizer fails.
                                onSuccess(latinLines)
                            }
                    } catch (_: Exception) {
                        onSuccess(latinLines)
                    }
                }
                .addOnFailureListener { latinError ->
                    try {
                        devanagariRecognizer.process(image)
                            .addOnSuccessListener { devanagariResult ->
                                onSuccess(toLines(devanagariResult, bitmap.width, bitmap.height))
                            }
                            .addOnFailureListener { onFailure(latinError) }
                    } catch (_: Exception) {
                        onFailure(latinError)
                    }
                }
        } catch (error: Exception) {
            onFailure(error)
        }
    }

    suspend fun extractLinesFromBitmap(bitmap: Bitmap): List<OcrTextLine> =
        suspendCancellableCoroutine { continuation ->
            extractLinesFromBitmap(
                bitmap = bitmap,
                onSuccess = { lines ->
                    if (continuation.isActive) continuation.resume(lines)
                },
                onFailure = { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            )
        }

    private fun toLines(result: Text, width: Int, height: Int): List<OcrTextLine> {
        val output = ArrayList<OcrTextLine>()
        result.textBlocks.forEach { block ->
            block.lines.forEach { line ->
                val bounds: Rect? = line.boundingBox
                val value = line.text.trim()
                if (bounds != null && value.isNotEmpty()) {
                    output.add(
                        OcrTextLine(
                            text = value,
                            left = (bounds.left.toFloat() / width).coerceIn(0f, 1f),
                            top = (bounds.top.toFloat() / height).coerceIn(0f, 1f),
                            right = (bounds.right.toFloat() / width).coerceIn(0f, 1f),
                            bottom = (bounds.bottom.toFloat() / height).coerceIn(0f, 1f)
                        )
                    )
                }
            }
        }
        return output
    }

    private fun mergeLines(
        first: List<OcrTextLine>,
        second: List<OcrTextLine>
    ): List<OcrTextLine> {
        val merged = first.toMutableList()
        second.forEach { candidate ->
            val duplicate = merged.indexOfFirst { existing -> overlap(existing, candidate) > 0.55f }
            if (duplicate < 0) {
                merged.add(candidate)
            } else {
                val existing = merged[duplicate]
                val combined = listOf(existing.text, candidate.text)
                    .distinctBy { it.lowercase() }
                    .joinToString(" ")
                merged[duplicate] = existing.copy(text = combined)
            }
        }
        return merged.sortedWith(compareBy<OcrTextLine> { it.top }.thenBy { it.left })
    }

    private fun overlap(first: OcrTextLine, second: OcrTextLine): Float {
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
