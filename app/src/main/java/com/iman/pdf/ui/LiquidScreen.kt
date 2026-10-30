package com.iman.pdf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iman.pdf.pdf.LiquidLoader
import com.iman.pdf.pdf.PdfEngine

@Composable
fun LiquidScreen(
    engine: PdfEngine,
    startPage: Int,
    onClose: (Int) -> Unit
) {
    val context = LocalContext.current
    val first = startPage.coerceIn(0, engine.pageCount - 1)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = first)
    var fontSize by remember { mutableFloatStateOf(18f) }
    val loader = remember { LiquidLoader(context, engine.file) }
    val pages = remember { mutableStateMapOf<Int, List<String>>() }
    val currentPage by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    DisposableEffect(loader) {
        onDispose { loader.close() }
    }

    LaunchedEffect(loader) {
        val total = engine.pageCount
        for (step in 0 until total) {
            val index = (first + step) % total
            if (pages[index] == null) {
                pages[index] = loader.page(index)
            }
        }
    }

    BackHandler(onBack = { onClose(currentPage) })

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onClose(currentPage) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Liquid mode",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { fontSize = (fontSize - 2f).coerceAtLeast(12f) }) {
                    Text("A−")
                }
                TextButton(onClick = { fontSize = (fontSize + 2f).coerceAtMost(36f) }) {
                    Text("A+")
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                items(count = engine.pageCount, key = { it }) { index ->
                    LaunchedEffect(index) {
                        if (pages[index] == null) {
                            pages[index] = loader.page(index)
                        }
                    }
                    LiquidPage(
                        index = index,
                        paragraphs = pages[index],
                        fontSize = fontSize
                    )
                }
            }
        }
    }
}

@Composable
private fun LiquidPage(
    index: Int,
    paragraphs: List<String>?,
    fontSize: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = "Page " + (index + 1),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        if (paragraphs == null) {
            Text(
                text = "Loading...",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (paragraphs.isEmpty()) {
            Text(
                text = "No text on this page (it may be an image).",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            paragraphs.forEach { paragraph ->
                Text(
                    text = paragraph,
                    modifier = Modifier.padding(top = 10.dp),
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.5f).sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}
