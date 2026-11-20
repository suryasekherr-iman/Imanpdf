package com.iman.pdf.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerMenu(
    onReadAloud: () -> Unit,
    onPrint: () -> Unit,
    onSave: () -> Unit,
    onTool: (String) -> Unit,
    onBookmarks: () -> Unit = {},
    onAddBookmark: () -> Unit = {},
    onViewSettings: () -> Unit = {},
    onComments: () -> Unit = {},
    onExport: () -> Unit = {},
    onOcrPage: () -> Unit = {},
    onOcrDocument: () -> Unit = {}
) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    fun soon() {
        Toast.makeText(context, "This will be added soon", Toast.LENGTH_SHORT).show()
    }

    Column {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = "More",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    if (open) {
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = "More options",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                MenuRow(Icons.Outlined.GridView, "View settings") {
                    open = false
                    onViewSettings()
                }
                MenuRow(Icons.Outlined.ViewModule, "Pages") {
                    open = false
                    onTool("organize")
                }
                MenuRow(Icons.Outlined.Article, "Bookmarks & TOC") {
                    open = false
                    onBookmarks()
                }
                MenuRow(Icons.Outlined.Comment, "Comment List") {
                    open = false
                    onComments()
                }
                MenuRow(Icons.Outlined.NoteAdd, "Add bookmark") {
                    open = false
                    onAddBookmark()
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                MenuRow(Icons.Outlined.Print, "Print") {
                    open = false
                    onPrint()
                }
                MenuRow(Icons.Outlined.Headphones, "Read aloud") {
                    open = false
                    onReadAloud()
                }
                MenuRow(Icons.Outlined.Edit, "Edit PDF") {
                    open = false
                    onTool("edit_pdf")
                }
                MenuRow(Icons.Outlined.SaveAlt, "Export") {
                    open = false
                    onExport()
                }
                MenuRow(Icons.Outlined.Article, "Recognize text on this page (OCR)") {
                    open = false
                    onOcrPage()
                }
                MenuRow(Icons.Outlined.Article, "Recognize text in entire PDF (OCR)") {
                    open = false
                    onOcrDocument()
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                MenuRow(Icons.Outlined.Compress, "Compress PDF") {
                    open = false
                    onTool("compress")
                }
                MenuRow(Icons.Outlined.MergeType, "Combine files") {
                    open = false
                    onTool("combine")
                }
                MenuRow(Icons.Outlined.Lock, "Set password") {
                    open = false
                    onTool("password")
                }
                MenuRow(Icons.Outlined.PictureAsPdf, "Create PDF") {
                    open = false
                    onTool("create_pdf")
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                MenuRow(Icons.Outlined.Save, "Save a copy") {
                    open = false
                    onSave()
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        onClick = onClick
    )
}
