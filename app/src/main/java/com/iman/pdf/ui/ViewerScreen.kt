package com.imran.pdf.ui.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.imran.pdf.core.pdf.MuPdfEngine

@Composable
fun PdfViewerScreen(pdfFilePath: String) {
    val muPdfEngine = remember { MuPdfEngine() }
    var pdfBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Zoom state
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // PDF Load karein
    LaunchedEffect(pdfFilePath) {
        isLoading = true
        val success = muPdfEngine.openDocument(pdfFilePath)
        if (success) {
            // Page 0 (Pehla page) render karein 1.5x scale par
            pdfBitmap = muPdfEngine.renderPageToBitmap(0, scale = 1.5f)
        } else {
            errorMessage = "PDF open nahi ho payi. File corrupt ho sakti hai."
        }
        isLoading = false
    }

    // Cleanup jab screen close ho
    DisposableEffect(Unit) {
        onDispose {
            muPdfEngine.closeDocument()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = Color.White)
        } else if (errorMessage != null) {
            Text(text = errorMessage!!, color = Color.Red)
        } else {
            pdfBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "PDF Page",
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                // Pinch to Zoom aur Pan logic
                                scale *= zoom
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        }
                )
            }
        }
    }
}
