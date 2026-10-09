package com.iman.pdf.ui

import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.iman.pdf.pdf.PdfEngine

@Composable
fun PdfPageItem(
    engine: PdfEngine,
    index: Int,
    renderWidth: Int
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
                contentScale = ContentScale.FillBounds
            )
        }
    }
}
