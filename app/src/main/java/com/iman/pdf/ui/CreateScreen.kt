package com.iman.pdf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class QuickAction(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val tint: Color
)

private val quickActions = listOf(
    QuickAction("create_pdf", "Create PDF", Icons.Outlined.NoteAdd, Color(0xFFFF6B6B)),
    QuickAction("scan", "New Scan", Icons.Outlined.CameraAlt, Color(0xFF4CD1A0)),
    QuickAction("combine", "Combine files", Icons.Outlined.MergeType, Color(0xFF5AA9FF)),
    QuickAction("organize", "Organize pages", Icons.Outlined.ViewModule, Color(0xFFFFA24C)),
    QuickAction("crop", "Crop pages", Icons.Outlined.Crop, Color(0xFFB388FF)),
    QuickAction("compress", "Compress PDF", Icons.Outlined.Compress, Color(0xFF4DD0E1)),
    QuickAction("password", "Set password", Icons.Outlined.Lock, Color(0xFFFFD54F))
)

@Composable
fun CreateScreen(onAction: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Quick actions",
            modifier = Modifier.padding(top = 24.dp, bottom = 16.dp),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(quickActions, key = { it.id }) { action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onAction(action.id) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(action.tint.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                            tint = action.tint
                        )
                    }
                    Spacer(modifier = Modifier.height(0.dp))
                    Text(
                        text = action.title,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
