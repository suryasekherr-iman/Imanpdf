package com.iman.pdf.ui

import android.net.Uri
import androidx.compose.runtime.Composable

val implementedTools = listOf(
    "combine",
    "create_pdf",
    "password",
    "organize",
    "crop"
)

@Composable
fun ToolHost(
    tool: String,
    onClose: () -> Unit,
    onOpen: (Uri) -> Unit
) {
    when (tool) {
        "combine" -> CombineScreen(onBack = onClose, onOpen = onOpen)
        "create_pdf" -> ImagesToPdfScreen(onBack = onClose, onOpen = onOpen)
        "password" -> ProtectScreen(onBack = onClose, onOpen = { })
        "organize" -> OrganizeScreen(onBack = onClose, onOpen = onOpen)
        "crop" -> CropScreen(onBack = onClose, onOpen = onOpen)
    }
}
