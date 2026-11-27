package com.iman.pdf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.StrikethroughS
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iman.pdf.annot.AnnotController
import com.iman.pdf.annot.AnnotTool
import com.iman.pdf.annot.SignatureStore
import com.iman.pdf.annot.TextMark

private val palette = listOf(
    0xFF000000.toInt(),
    0xFFE53935.toInt(),
    0xFF1E88E5.toInt(),
    0xFF43A047.toInt(),
    0xFFFFEB3B.toInt(),
    0xFFFB8C00.toInt(),
    0xFF8E24AA.toInt()
)

@Composable
fun AnnotToolbar(
    annot: AnnotController,
    onSaveAnnotations: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var confirmClose by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (annot.signature == null) {
            annot.signature = SignatureStore.load(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolButton(Icons.Outlined.Highlight, annot.tool == AnnotTool.HIGHLIGHT) {
                    annot.select(toggle(annot.tool, AnnotTool.HIGHLIGHT))
                }
                ToolButton(Icons.Outlined.FormatUnderlined, annot.tool == AnnotTool.UNDERLINE) {
                    annot.select(toggle(annot.tool, AnnotTool.UNDERLINE))
                }
                ToolButton(Icons.Outlined.StrikethroughS, annot.tool == AnnotTool.STRIKE) {
                    annot.select(toggle(annot.tool, AnnotTool.STRIKE))
                }
                ToolButton(Icons.Outlined.Draw, annot.tool == AnnotTool.DRAW) {
                    annot.select(toggle(annot.tool, AnnotTool.DRAW))
                }
                ToolButton(Icons.Outlined.TextFields, annot.tool == AnnotTool.TEXT) {
                    annot.select(toggle(annot.tool, AnnotTool.TEXT))
                }
                ToolButton(Icons.Outlined.StickyNote2, annot.tool == AnnotTool.NOTE) {
                    annot.select(toggle(annot.tool, AnnotTool.NOTE))
                }
                ToolButton(Icons.Outlined.Gesture, annot.tool == AnnotTool.SIGN) {
                    annot.select(toggle(annot.tool, AnnotTool.SIGN))
                }
                ToolButton(Icons.Outlined.Check, annot.tool == AnnotTool.CHECK) {
                    annot.select(toggle(annot.tool, AnnotTool.CHECK))
                }
                ToolButton(Icons.Outlined.Cancel, annot.tool == AnnotTool.CROSS) {
                    annot.select(toggle(annot.tool, AnnotTool.CROSS))
                }
                ToolButton(Icons.Outlined.CalendarToday, annot.tool == AnnotTool.DATE) {
                    annot.select(toggle(annot.tool, AnnotTool.DATE))
                }
                ToolButton(Icons.Outlined.Backspace, annot.tool == AnnotTool.ERASE) {
                    annot.select(toggle(annot.tool, AnnotTool.ERASE))
                }
            }
            IconButton(
                onClick = { annot.redo() },
                enabled = annot.canRedo,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }
            IconButton(
                onClick = { annot.undo() },
                enabled = annot.canUndo,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            IconButton(
                onClick = { onSaveAnnotations() },
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.Outlined.Save, contentDescription = "Save PDF")
            }
            IconButton(
                onClick = {
                    if (annot.marks.isNotEmpty()) {
                        confirmClose = true
                    } else {
                        onClose()
                    }
                }
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }

        if (annot.tool != AnnotTool.NONE) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                palette.forEach { argb ->
                    val chosen = annot.color == argb
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(
                                width = if (chosen) 3.dp else 1.dp,
                                color = if (chosen) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                shape = CircleShape
                            )
                            .clickable { annot.color = argb }
                    )
                }
            }
        }

        if (annot.tool == AnnotTool.SIGN) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Tap on the page to place your signature",
                    style = MaterialTheme.typography.labelMedium
                )
                TextButton(onClick = { annot.needSignature = true }) {
                    Text("Change")
                }
            }
        }

        if (annot.tool == AnnotTool.ERASE) {
            Text(
                "Tap an unsaved mark to erase it",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        if (annot.tool == AnnotTool.CHECK || annot.tool == AnnotTool.CROSS) {
            Text(
                "Tap on the page to place the mark",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        if (annot.tool == AnnotTool.DRAW) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Thickness", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = annot.strokeWidth,
                    onValueChange = { annot.strokeWidth = it },
                    valueRange = 0.002f..0.014f,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }

        if (annot.tool == AnnotTool.TEXT ||
            annot.tool == AnnotTool.NOTE ||
            annot.tool == AnnotTool.DATE
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Text size", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = annot.textSize,
                    onValueChange = { annot.textSize = it },
                    valueRange = 0.02f..0.07f,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }

    if (annot.needSignature) {
        SignaturePadDialog(
            onSave = { data ->
                SignatureStore.save(context, data)
                annot.signature = data
                annot.needSignature = false
            },
            onDismiss = {
                annot.needSignature = false
                if (annot.signature == null) {
                    annot.select(AnnotTool.NONE)
                }
            }
        )
    }

    val place = annot.pendingText
    if (place != null) {
        AlertDialog(
            onDismissRequest = {
                typed = ""
                annot.pendingText = null
            },
            title = {
                Text(if (annot.tool == AnnotTool.NOTE) "Add note" else "Add text")
            },
            text = {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    placeholder = { Text("Type here") },
                    minLines = 2
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val text = typed.trim()
                        if (text.isNotEmpty()) {
                            annot.add(
                                TextMark(
                                    page = place.page,
                                    x = place.x,
                                    y = place.y,
                                    text = text,
                                    color = annot.color,
                                    size = annot.textSize,
                                    boxed = annot.tool == AnnotTool.NOTE
                                )
                            )
                        }
                        typed = ""
                        annot.pendingText = null
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        typed = ""
                        annot.pendingText = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Unsaved marks") },
            text = { Text("Save your marks on this PDF, or discard them?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClose = false
                        onSaveAnnotations()
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmClose = false
                        annot.clearAll()
                        onClose()
                    }
                ) {
                    Text("Discard")
                }
            }
        )
    }
}

private fun toggle(current: AnnotTool, wanted: AnnotTool): AnnotTool {
    return if (current == wanted) AnnotTool.NONE else wanted
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                }
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
