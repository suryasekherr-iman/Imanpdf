package com.iman.pdf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iman.pdf.pdf.PdfFormField
import com.iman.pdf.pdf.PdfFormFieldUpdate

@Composable
fun PdfFormFillDialog(
    fields: List<PdfFormField>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (List<PdfFormFieldUpdate>) -> Unit
) {
    var values by remember(fields) {
        mutableStateOf(fields.associate { (it.pageIndex to it.fieldIndex) to it.value })
    }
    val editableFields = fields.filter {
        !it.readOnly && it.type in setOf("Text", "Dropdown", "List", "Checkbox")
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Fill PDF form") },
        text = {
            if (fields.isEmpty()) {
                Text("No interactive form fields were found in this PDF.")
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${fields.size} field(s) found",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    fields.forEach { field ->
                        val key = field.pageIndex to field.fieldIndex
                        val current = values[key] ?: field.value
                        val enabled = !saving && !field.readOnly &&
                            field.type in setOf("Text", "Dropdown", "List", "Checkbox")
                        if (field.type == "Checkbox") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = current.equals("true", true) ||
                                        (current.isNotBlank() && !current.equals("Off", true)),
                                    enabled = enabled,
                                    onCheckedChange = {
                                        values = values + (key to it.toString())
                                    }
                                )
                                Column {
                                    Text(field.label)
                                    Text(
                                        "Page ${field.pageIndex + 1}" +
                                            if (field.required) " • Required" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = current,
                                onValueChange = { values = values + (key to it) },
                                enabled = enabled,
                                readOnly = !enabled,
                                label = {
                                    Text(field.label + if (field.required) " *" else "")
                                },
                                supportingText = {
                                    val options = field.options.takeIf { it.isNotEmpty() }
                                    Text(
                                        "Page ${field.pageIndex + 1} • ${field.type}" +
                                            (options?.let { " • Options: ${it.joinToString(", ")}" } ?: "") +
                                            if (field.readOnly) " • Read only" else ""
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                    if (editableFields.isEmpty()) {
                        Text(
                            "These fields cannot be edited in Iman PDF.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(editableFields.map { field ->
                        PdfFormFieldUpdate(
                            pageIndex = field.pageIndex,
                            fieldIndex = field.fieldIndex,
                            value = values[field.pageIndex to field.fieldIndex] ?: field.value
                        )
                    })
                },
                enabled = !saving && editableFields.isNotEmpty()
            ) { Text(if (saving) "Saving…" else "Save form") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}
