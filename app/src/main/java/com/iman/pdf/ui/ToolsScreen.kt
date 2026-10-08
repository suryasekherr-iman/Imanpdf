package com.iman.pdf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.Tab as TabItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class ToolItem(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val icon: ImageVector
)

private val allTools = listOf(
    ToolItem(
        "fill_sign", "Fill & Sign",
        "Complete a form and add your signature",
        "Edit", Icons.Outlined.Gesture
    ),
    ToolItem(
        "comment", "Add comment",
        "Highlight text, draw, and add sticky notes or text comments",
        "Read", Icons.Outlined.Comment
    ),
    ToolItem(
        "liquid", "Liquid mode",
        "Read PDFs more easily with content reflowing to fit your screen",
        "Read", Icons.Outlined.Article
    ),
    ToolItem(
        "read_aloud", "Read aloud",
        "Listen to your PDF on the go",
        "Read", Icons.Outlined.VolumeUp
    ),
    ToolItem(
        "edit_pdf", "Edit PDF",
        "Edit text and images in your PDF",
        "Edit", Icons.Outlined.Edit
    ),
    ToolItem(
        "organize", "Organize pages",
        "Reorder, rotate, delete or add pages",
        "Edit", Icons.Outlined.ViewModule
    ),
    ToolItem(
        "crop", "Crop pages",
        "Trim the margins of your pages",
        "Edit", Icons.Outlined.Crop
    ),
    ToolItem(
        "compress", "Compress PDF",
        "Reduce the size of your PDF file",
        "Edit", Icons.Outlined.Compress
    ),
    ToolItem(
        "password", "Set password",
        "Protect your PDF with a password",
        "Edit", Icons.Outlined.Lock
    ),
    ToolItem(
        "combine", "Combine files",
        "Merge multiple files into one PDF",
        "Create", Icons.Outlined.MergeType
    ),
    ToolItem(
        "create_pdf", "Create PDF",
        "Make a PDF from images or documents",
        "Create", Icons.Outlined.NoteAdd
    ),
    ToolItem(
        "scan", "Scan",
        "Scan documents with your camera",
        "Create", Icons.Outlined.CameraAlt
    )
)

@Composable
fun ToolsScreen(onToolClick: (ToolItem) -> Unit) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val filters = listOf("All", "Read", "Create", "Edit")
    val shown = if (filter == 0) {
        allTools
    } else {
        allTools.filter { it.category == filters[filter] }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Tools",
            modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        ScrollableTabRow(
            selectedTabIndex = filter,
            edgePadding = 0.dp,
            containerColor = Color.Transparent
        ) {
            filters.forEachIndexed { index, label ->
                TabItem(
                    selected = filter == index,
                    onClick = { filter = index },
                    text = { Text(label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(shown, key = { it.id }) { tool ->
                ToolCard(tool = tool, onClick = { onToolClick(tool) })
            }
        }
    }
}

@Composable
private fun ToolCard(tool: ToolItem, onClick: () -> Unit) {
    val tint = when (tool.category) {
        "Read" -> Color(0xFF5AA9FF)
        "Create" -> Color(0xFF4CD1A0)
        else -> Color(0xFFFFA24C)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                tint = tint
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = tool.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = tool.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
