package com.iman.pdf.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.iman.pdf.annot.AnnotController
import com.iman.pdf.pdf.PdfEngine
import com.iman.pdf.pdf.SearchHit

private val NIGHT_MATRIX = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )
)

private val SEPIA_MATRIX = ColorMatrix(
    floatArrayOf(
        0.98f, 0f, 0f, 0f, 0f,
        0f, 0.93f, 0f, 0f, 0f,
        0f, 0f, 0.78f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
)

@Composable
fun PdfPageItem(
    engine: PdfEngine,
    index: Int,
    renderWidth: Int,
    annot: AnnotController? = null,
    nightMode: Boolean = false,
    sepiaMode: Boolean = false,
    searchHits: List<SearchHit> = emptyList(),
    activeSearchHit: SearchHit? = null
) {
    var bitmap by remember(index) { mutableStateOf<Bitmap?>(null) }
    var ratio by remember(index) { mutableFloatStateOf(1.414f) }

    LaunchedEffect(index, renderWidth) {
        ratio = engine.pageAspectRatio(index)
        bitmap = engine.renderPage(index, renderWidth)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f / ratio)
            .background(Color.White)
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "Page ${index + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                colorFilter = when {
                    nightMode -> ColorFilter.colorMatrix(NIGHT_MATRIX)
                    sepiaMode -> ColorFilter.colorMatrix(SEPIA_MATRIX)
                    else -> null
                }
            )
        }
        if (searchHits.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                searchHits.forEach { hit ->
                    val rect = hit.rect ?: return@forEach
                    val left = rect.left * size.width
                    val top = rect.top * size.height
                    val right = rect.right * size.width
                    val bottom = rect.bottom * size.height
                    drawRect(
                        color = if (hit == activeSearchHit) {
                            Color(0xFFFF9800).copy(alpha = 0.48f)
                        } else {
                            Color(0xFFFFEB3B).copy(alpha = 0.34f)
                        },
                        topLeft = Offset(left, top),
                        size = Size((right - left).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f))
                    )
                }
            }
        }
        if (annot != null) {
            AnnotationLayer(
                page = index,
                annot = annot,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
