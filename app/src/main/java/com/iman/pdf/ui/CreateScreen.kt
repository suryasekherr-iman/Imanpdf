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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import com.iman.pdf.R
import androidx.compose.ui.unit.dp
import com.iman.pdf.R

private data class QuickAction(
    val id: String,
    val titleRes: Int,
    val icon: ImageVector,
    val tint: Color
)

private val quickActions = listOf(
    QuickAction("create_pdf", R.string.tool_create_pdf, Icons.Outlined.NoteAdd, Color(0xFFFF6B6B)),
    QuickAction("scan", R.string.tool_scan, Icons.Outlined.CameraAlt, Color(0xFF4CD1A0)),
    QuickAction("combine", R.string.tool_combine, Icons.Outlined.MergeType, Color(0xFF5AA9FF)),
    QuickAction("organize", R.string.tool_organize, Icons.Outlined.ViewModule, Color(0xFFFFA24C)),
    QuickAction("crop", R.string.tool_crop, Icons.Outlined.Crop, Color(0xFFB388FF)),
    QuickAction("compress", R.string.tool_compress, Icons.Outlined.Compress, Color(0xFF4DD0E1)),
    QuickAction("password", R.string.tool_password, Icons.Outlined.Lock, Color(0xFFFFD54F))
)

@Composable
fun CreateScreen(onAction: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.create_quick_actions),
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 156.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onAction(action.id) }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.Top
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
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(
                            when (action.id) {
                                "create_pdf" -> R.string.tool_create_pdf
                                "scan" -> R.string.tool_scan
                                "combine" -> R.string.tool_combine
                                "organize" -> R.string.tool_organize
                                "crop" -> R.string.tool_crop
                                "compress" -> R.string.tool_compress
                                "password" -> R.string.tool_password
                                else -> R.string.create_quick_actions
                            }
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            when (action.id) {
                                "create_pdf" -> R.string.tool_create_pdf_desc
                                "scan" -> R.string.tool_scan_desc
                                "combine" -> R.string.tool_combine_desc
                                "organize" -> R.string.tool_organize_desc
                                "crop" -> R.string.tool_crop_desc
                                "compress" -> R.string.tool_compress_desc
                                "password" -> R.string.tool_password_desc
                                else -> R.string.create_quick_actions
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
