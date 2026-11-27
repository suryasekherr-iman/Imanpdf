package com.iman.pdf.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.iman.pdf.annot.AnnotTool

@Composable
internal fun ColumnScope.ViewerTopSection(
    visible: Boolean,
    title: String,
    currentPage: Int,
    search: ViewerSearch,
    saving: ViewerSaving,
    panels: ViewerPanels,
    ocr: ViewerOcr,
    reading: ViewerReading,
    onBack: () -> Unit,
    onOpenTool: (String) -> Unit,
    onLiquid: () -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut()
    ) {
        ViewerTopBar(
            title = title,
            searchOpen = search.open,
            query = search.query,
            focusRequester = search.focusRequester,
            onQueryChange = { search.query = it },
            onSearchSubmit = { search.run(keyboard) },
            onClearQuery = { search.clear() },
            onBack = { saving.requestExit(onBack) },
            onCloseSearch = { search.close() },
            onOpenSearch = { search.open = true },
            onTool = { id -> saving.requestExit { onOpenTool(id) } },
            onViewSettings = { panels.viewSettingsOpen = true },
            onExport = { panels.exportOpen = true },
            onOcrPage = { ocr.runCurrentPage() },
            onOcrDocument = { ocr.runDocument() },
            onComments = { panels.openComments() },
            onBookmarks = { panels.openBookmarks() },
            onAddBookmark = { panels.addBookmark(currentPage) },
            onLiquid = onLiquid,
            onReadAloud = {
                if (reading.panelOpen) {
                    reading.close()
                } else {
                    reading.panelOpen = true
                }
            },
            onShare = { saving.share(title) },
            onPrint = { saving.print(title) },
            onSave = { saving.requestSave() }
        )
    }
}

@Composable
internal fun ColumnScope.ViewerBottomSection(
    visible: Boolean,
    title: String,
    search: ViewerSearch,
    saving: ViewerSaving,
    panels: ViewerPanels,
    ocr: ViewerOcr,
    reading: ViewerReading,
    onAnnotTool: (AnnotTool) -> Unit,
    onLiquid: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        ViewerBottomBar(
            onReadAloud = { reading.panelOpen = true },
            onTool = onAnnotTool,
            onLiquid = onLiquid,
            onSearch = { search.open = true },
            onShare = { saving.share(title) },
            onPrint = { saving.print(title) },
            onSave = { saving.requestSave() },
            onOcrPage = { ocr.runCurrentPage() },
            onOcrDocument = { ocr.runDocument() },
            onFillForm = { panels.openFormFill() }
        )
    }
}
