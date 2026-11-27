package com.iman.pdf.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iman.pdf.pdf.PdfEngine
import com.iman.pdf.pdf.PdfText
import com.iman.pdf.pdf.SearchHit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** In-document search state of the viewer. */
internal class ViewerSearch(
    private val context: Context,
    private val scope: CoroutineScope,
    private val engine: PdfEngine,
    private val documentId: String,
    private val listState: LazyListState
) {
    val focusRequester = FocusRequester()
    var open by mutableStateOf(false)
    var query by mutableStateOf("")
    var hits by mutableStateOf<List<SearchHit>>(emptyList())
    var searching by mutableStateOf(false)
    var searched by mutableStateOf(false)
    var activeHit by mutableIntStateOf(-1)

    fun close() {
        open = false
        hits = emptyList()
        searching = false
        searched = false
        activeHit = -1
    }

    fun clear() {
        query = ""
        hits = emptyList()
        searched = false
        activeHit = -1
    }

    fun goToHit(index: Int) {
        if (hits.isEmpty()) {
            return
        }
        val target = ((index % hits.size) + hits.size) % hits.size
        activeHit = target
        scope.launch { listState.scrollToItem(hits[target].page) }
    }

    fun run(keyboard: SoftwareKeyboardController?) {
        val text = query.trim()
        if (text.isEmpty()) {
            return
        }
        scope.launch {
            searching = true
            searched = false
            activeHit = -1
            keyboard?.hide()
            hits = PdfText.search(context, engine.file, text, documentId)
            searching = false
            searched = true
        }
    }
}

/** Previous/next row shown while stepping through search results. */
@Composable
internal fun ViewerSearchNav(search: ViewerSearch) {
    val visible = search.open && !search.searching && !search.searched &&
        search.activeHit >= 0 && search.hits.isNotEmpty()
    if (!visible) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { search.searched = true }) {
            Text("List")
        }
        Text(
            text = (search.activeHit + 1).toString() + " / " + search.hits.size,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(onClick = { search.goToHit(search.activeHit - 1) }) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous result")
        }
        IconButton(onClick = { search.goToHit(search.activeHit + 1) }) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next result")
        }
    }
}
