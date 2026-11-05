package com.iman.pdf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.iman.pdf.data.AppLock

@Composable
fun AppLockSection() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(AppLock.isEnabled(context)) }
    var bio by remember { mutableStateOf(AppLock.biometricEnabled(context)) }
    var setupOpen by remember { mutableStateOf(false) }
    val canBio = remember { AppLock.canUseBiometric(context) }

    Text(
        text = "App lock",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
    LockToggleRow(
        title = "Lock with PIN",
        subtitle = "Ask for a 4-digit PIN when the app opens",
        checked = enabled
    ) { on ->
        if (on) {
            setupOpen = true
        } else {
            AppLock.disable(context)
            (context as? android.app.Activity)?.let { AppLock.applySecure(it) }
            enabled = false
            bio = false
        }
    }
    if (enabled) {
        TextButton(
            onClick = { setupOpen = true },
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text("Change PIN")
        }
        if (canBio) {
            LockToggleRow(
                title = "Use fingerprint / face",
                subtitle = "Unlock faster; PIN still works",
                checked = bio
            ) { on ->
                AppLock.setBiometric(context, on)
                bio = on
            }
        }
    }

    if (setupOpen) {
        PinSetupDialog(
            onSave = { pin ->
                AppLock.setPin(context, pin)
                (context as? android.app.Activity)?.let { AppLock.applySecure(it) }
                enabled = true
                setupOpen = false
            },
            onDismiss = { setupOpen = false }
        )
    }
}

@Composable
private fun LockToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
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

@Composable
fun PinSetupDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val mismatch = second.length == 4 && second != first

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set 4-digit PIN") },
        text = {
            Column {
                OutlinedTextField(
                    value = first,
                    onValueChange = { first = it.filter(Char::isDigit).take(4) },
                    label = { Text("New PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = second,
                    onValueChange = { second = it.filter(Char::isDigit).take(4) },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    isError = mismatch,
                    supportingText = {
                        if (mismatch) {
                            Text("PINs do not match")
                        }
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(first) },
                enabled = first.length == 4 && first == second
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
