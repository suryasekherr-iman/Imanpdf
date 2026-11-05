package com.iman.pdf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewSettingsSheet(
    keepScreenOn: Boolean,
    nightMode: Boolean,
    sepiaMode: Boolean,
    onKeepScreenOn: (Boolean) -> Unit,
    onNightMode: (Boolean) -> Unit,
    onSepiaMode: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text("View settings", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            SettingRow(
                title = "Keep screen on",
                subtitle = "Screen will not sleep while reading",
                checked = keepScreenOn,
                onChange = onKeepScreenOn
            )
            SettingRow(
                title = "Night mode",
                subtitle = "Invert page colors for dark reading",
                checked = nightMode,
                onChange = onNightMode
            )
            SettingRow(
                title = "Sepia mode",
                subtitle = "Warm paper-like page colors",
                checked = sepiaMode,
                onChange = onSepiaMode
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
