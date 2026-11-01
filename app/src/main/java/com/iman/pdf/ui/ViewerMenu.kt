package com.iman.pdf.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext

@Composable
fun ViewerMenu(
    onReadAloud: () -> Unit,
    onPrint: () -> Unit,
    onSave: () -> Unit,
    onTool: (String) -> Unit,
    onBookmarks: () -> Unit = {},
    onAddBookmark: () -> Unit = {}
) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }

    fun soon() {
        Toast.makeText(context, "This will be added soon", Toast.LENGTH_SHORT).show()
    }

    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "More")
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false }
        ) {
            MenuRow(Icons.Outlined.GridView, "View settings") {
                open = false
                soon()
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
                soon()
            }
            MenuRow(Icons.Outlined.NoteAdd, "Add bookmark") {
                open = false
                onAddBookmark()
            }
            HorizontalDivider()
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
                soon()
            }
            MenuRow(Icons.Outlined.SaveAlt, "Export") {
                open = false
                soon()
            }
            HorizontalDivider()
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
            HorizontalDivider()
            MenuRow(Icons.Outlined.Save, "Save a copy") {
                open = false
                onSave()
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
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick
    )
}
