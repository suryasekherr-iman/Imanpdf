package com.iman.pdf.ui

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
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Draw
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

@Composable
fun ViewerBottomBar(
    onReadAloud: () -> Unit,
    onTool: (AnnotTool) -> Unit,
    onLiquid: () -> Unit,
    onSearch: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onSave: () -> Unit
) {
    var moreOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        BarItem(Icons.Outlined.Headphones, "Read aloud", Modifier.weight(1f)) {
            onReadAloud()
        }
        BarItem(Icons.Outlined.Create, "Fill & Sign", Modifier.weight(1f)) {
            onTool(AnnotTool.SIGN)
        }
        BarItem(Icons.Outlined.TextFields, "Text", Modifier.weight(1f)) {
            onTool(AnnotTool.TEXT)
        }
        BarItem(Icons.Outlined.Highlight, "Highlight", Modifier.weight(1f)) {
            onTool(AnnotTool.HIGHLIGHT)
        }
        BarItem(Icons.Outlined.Draw, "Draw", Modifier.weight(1f)) {
            onTool(AnnotTool.DRAW)
        }
        BarItem(Icons.Outlined.GridView, "More tools", Modifier.weight(1f)) {
            moreOpen = true
        }
    }

    if (moreOpen) {
        MoreToolsSheet(
            items = listOf(
                SheetItem(Icons.Outlined.WaterDrop, "Liquid mode") { onLiquid() },
                SheetItem(Icons.Outlined.Search, "Search") { onSearch() },
                SheetItem(Icons.Outlined.StickyNote2, "Note") { onTool(AnnotTool.NOTE) },
                SheetItem(Icons.Outlined.Check, "Tick") { onTool(AnnotTool.CHECK) },
                SheetItem(Icons.Outlined.Cancel, "Cross") { onTool(AnnotTool.CROSS) },
                SheetItem(Icons.Outlined.CalendarToday, "Date") { onTool(AnnotTool.DATE) },
                SheetItem(Icons.Outlined.Share, "Share") { onShare() },
                SheetItem(Icons.Outlined.Print, "Print") { onPrint() },
                SheetItem(Icons.Outlined.SaveAlt, "Save a copy") { onSave() }
            ),
            onDismiss = { moreOpen = false }
        )
    }
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
    onDismiss: () -> Unit
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
        }
    }
}
