package com.iman.pdf.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun ViewerMenu(
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onSave: () -> Unit
) {
    var open by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "More")
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false }
        ) {
            DropdownMenuItem(
                text = { Text("Share") },
                leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                onClick = {
                    open = false
                    onShare()
                }
            )
            DropdownMenuItem(
                text = { Text("Print") },
                leadingIcon = { Icon(Icons.Outlined.Print, contentDescription = null) },
                onClick = {
                    open = false
                    onPrint()
                }
            )
            DropdownMenuItem(
                text = { Text("Save a copy") },
                leadingIcon = { Icon(Icons.Outlined.SaveAlt, contentDescription = null) },
                onClick = {
                    open = false
                    onSave()
                }
            )
        }
    }
}
