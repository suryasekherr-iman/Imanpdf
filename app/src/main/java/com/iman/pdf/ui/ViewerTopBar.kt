package com.iman.pdf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ViewerTopBar(
    title: String,
    searchOpen: Boolean,
    query: String,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onClearQuery: () -> Unit,
    onBack: () -> Unit,
    onCloseSearch: () -> Unit,
    onOpenSearch: () -> Unit,
    onLiquid: () -> Unit,
    onReadAloud: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (searchOpen) {
            IconButton(onClick = onCloseSearch) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search in PDF") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear")
                }
            }
        } else {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onLiquid) {
                Icon(Icons.Outlined.WaterDrop, contentDescription = "Liquid mode")
            }
            IconButton(onClick = onReadAloud) {
                Icon(Icons.Outlined.Headphones, contentDescription = "Read aloud")
            }
            IconButton(onClick = onOpenSearch) {
                Icon(Icons.Outlined.Search, contentDescription = "Search")
            }
            ViewerMenu(
                onShare = onShare,
                onPrint = onPrint,
                onSave = onSave
            )
        }
    }
}
