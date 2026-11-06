package com.iman.pdf.ui

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import com.iman.pdf.annot.AnnotController
import com.iman.pdf.pdf.PdfEngine
import com.iman.pdf.pdf.SearchHit
import com.iman.pdf.pdf.OcrStore
import com.iman.pdf.pdf.OcrLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    activeSearchHit: SearchHit? = null,
    documentId: String = engine.file.absolutePath,
    ocrRevision: Int = 0,
    onHighlight: ((Int, RectF) -> Unit)? = null,
    onInk: ((Int, List<List<PointF>>, Int, Float) -> Unit)? = null
) {
    val context = LocalContext.current
    var ocrLines by remember(index, documentId) { mutableStateOf<List<OcrLine>>(emptyList()) }
    LaunchedEffect(index, documentId, ocrRevision) {
        ocrLines = withContext(Dispatchers.IO) {
            OcrStore.loadPage(context, documentId, index)?.lines.orEmpty()
        }
    }
    var bitmap by remember(index) { mutableStateOf<Bitmap?>(null) }
    var ratio by remember(index) { mutableFloatStateOf(1.414f) }

    LaunchedEffect(index, renderWidth) {
        val rendered = withContext(Dispatchers.IO) {
            engine.pageAspectRatio(index) to engine.renderPage(index, renderWidth)
        }
        ratio = rendered.first
        bitmap = rendered.second
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
        if (ocrLines.isNotEmpty()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val pageWidth = maxWidth
                val pageHeight = maxHeight
                SelectionContainer {
                    Box(modifier = Modifier.fillMaxSize()) {
                        ocrLines.forEach { line ->
                            val lineWidth = (line.right - line.left).coerceAtLeast(0.001f)
                            val lineHeight = (line.bottom - line.top).coerceAtLeast(0.001f)
                            BasicText(
                                text = line.text,
                                modifier = Modifier
                                    .offset(
                                        x = pageWidth * line.left.coerceIn(0f, 1f),
                                        y = pageHeight * line.top.coerceIn(0f, 1f)
                                    )
                                    .width(pageWidth * lineWidth.coerceAtMost(1f))
                                    .height(pageHeight * lineHeight.coerceAtMost(1f)),
                                style = TextStyle(
                                    color = Color.Transparent,
                                    fontSize = (pageHeight * lineHeight * 0.75f).value.coerceAtLeast(1f).sp
                                ),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
                }
            }
        }
        if (annot != null) {
            AnnotationLayer(
                page = index,
                annot = annot,
                modifier = Modifier.fillMaxSize(),
                onHighlight = onHighlight,
                onInk = onInk
            )
        }
    }
}
