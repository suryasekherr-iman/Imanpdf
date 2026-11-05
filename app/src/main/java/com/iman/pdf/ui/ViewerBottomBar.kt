package com.iman.pdf.ui

import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.StrikethroughS
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iman.pdf.annot.AnnotTool

private class SheetItem(
    val icon: ImageVector,
    val label: String,
    val action: () -> Unit
)

private class ViewerToolItem(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val action: () -> Unit
)

private const val DEFAULT_BAR = "read_aloud,ocr_page,sign,text,highlight,draw"
private const val MAX_BAR = 6

@Composable
fun ViewerBottomBar(
    onReadAloud: () -> Unit,
    onTool: (AnnotTool) -> Unit,
    onLiquid: () -> Unit,
    onSearch: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onSave: () -> Unit,
    onOcrPage: () -> Unit = {},
    onOcrDocument: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("viewer_prefs", Context.MODE_PRIVATE) }
    var moreOpen by remember { mutableStateOf(false) }
    var customizeOpen by remember { mutableStateOf(false) }
    var barIds by remember {
        mutableStateOf(
            (prefs.getString("bar_tools", null) ?: DEFAULT_BAR)
                .split(",")
                .filter { it.isNotBlank() }
        )
    }

    val all = listOf(
        ViewerToolItem("read_aloud", Icons.Outlined.Headphones, "Read aloud") { onReadAloud() },
        ViewerToolItem("ocr_page", Icons.Outlined.Article, "Scan/OCR") { onOcrPage() },
        ViewerToolItem("ocr_document", Icons.Outlined.Article, "OCR document") { onOcrDocument() },
        ViewerToolItem("sign", Icons.Outlined.Create, "Fill & Sign") { onTool(AnnotTool.SIGN) },
        ViewerToolItem("text", Icons.Outlined.TextFields, "Text") { onTool(AnnotTool.TEXT) },
        ViewerToolItem("highlight", Icons.Outlined.Highlight, "Highlight") { onTool(AnnotTool.HIGHLIGHT) },
        ViewerToolItem("underline", Icons.Outlined.FormatUnderlined, "Underline") { onTool(AnnotTool.UNDERLINE) },
        ViewerToolItem("strike", Icons.Outlined.StrikethroughS, "Strikethrough") { onTool(AnnotTool.STRIKE) },
        ViewerToolItem("erase", Icons.Outlined.Backspace, "Eraser") { onTool(AnnotTool.ERASE) },
        ViewerToolItem("draw", Icons.Outlined.Draw, "Draw") { onTool(AnnotTool.DRAW) },
        ViewerToolItem("note", Icons.Outlined.StickyNote2, "Note") { onTool(AnnotTool.NOTE) },
        ViewerToolItem("check", Icons.Outlined.Check, "Tick") { onTool(AnnotTool.CHECK) },
        ViewerToolItem("cross", Icons.Outlined.Cancel, "Cross") { onTool(AnnotTool.CROSS) },
        ViewerToolItem("date", Icons.Outlined.CalendarToday, "Date") { onTool(AnnotTool.DATE) },
        ViewerToolItem("liquid", Icons.Outlined.WaterDrop, "Liquid mode") { onLiquid() },
        ViewerToolItem("search", Icons.Outlined.Search, "Search") { onSearch() },
        ViewerToolItem("share", Icons.Outlined.Share, "Share") { onShare() },
        ViewerToolItem("print", Icons.Outlined.Print, "Print") { onPrint() },
        ViewerToolItem("save", Icons.Outlined.SaveAlt, "Save a copy") { onSave() }
    )
    val onBar = barIds.mapNotNull { id -> all.firstOrNull { it.id == id } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        onBar.forEach { item ->
            BarItem(item.icon, item.label, Modifier.weight(1f)) { item.action() }
        }
        BarItem(Icons.Outlined.GridView, "More tools", Modifier.weight(1f)) {
            moreOpen = true
        }
    }

    if (moreOpen) {
        MoreToolsSheet(
            items = all
                .filter { item -> barIds.none { it == item.id } }
                .map { SheetItem(it.icon, it.label, it.action) },
            onDismiss = { moreOpen = false },
            onCustomize = {
                moreOpen = false
                customizeOpen = true
            }
        )
    }

    if (customizeOpen) {
        CustomizeToolbarDialog(
            tools = all.map { it.id to it.label },
            selected = barIds,
            onDismiss = { customizeOpen = false },
            onSave = { ids ->
                barIds = ids
                prefs.edit().putString("bar_tools", ids.joinToString(",")).apply()
                customizeOpen = false
            }
        )
    }
}

@Composable
private fun CustomizeToolbarDialog(
    tools: List<Pair<String, String>>,
    selected: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var chosen by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize toolbar") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Pick up to $MAX_BAR tools for the bottom bar (" + chosen.size + "/" + MAX_BAR + ")",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                tools.forEach { (id, label) ->
                    val on = chosen.contains(id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                chosen = if (on) {
                                    chosen - id
                                } else if (chosen.size < MAX_BAR) {
                                    chosen + id
                                } else {
                                    chosen
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = on,
                            onCheckedChange = null,
                            modifier = Modifier.padding(end = 12.dp, top = 8.dp, bottom = 8.dp)
                        )
                        Text(label, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val order = tools.map { it.first }
                    onSave(order.filter { chosen.contains(it) })
                },
                enabled = chosen.isNotEmpty()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = { onSave(DEFAULT_BAR.split(",")) }) { Text("Reset") }
        }
    )
}

@Composable
private fun BarItem(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreToolsSheet(
    items: List<SheetItem>,
    onDismiss: () -> Unit,
    onCustomize: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            Text(
                text = "More tools",
                modifier = Modifier.padding(bottom = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            items.chunked(4).forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    rowItems.forEach { item ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    onDismiss()
                                    item.action()
                                }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = item.label,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    repeat(4 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCustomize() }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Customize toolbar",
                    modifier = Modifier.padding(start = 16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
