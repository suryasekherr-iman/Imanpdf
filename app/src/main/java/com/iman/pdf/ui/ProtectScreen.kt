package com.iman.pdf.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iman.pdf.pdf.PdfActions
import com.iman.pdf.pdf.PdfProtectionService
import com.iman.pdf.pdf.PdfTools
import com.iman.pdf.pdf.UriActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun pickedNameOf(context: Context, uri: Uri): String {
    var name: String? = null
    try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0) name = cursor.getString(column)
            }
        }
    } catch (_: Exception) {
    }
    return name ?: "Document.pdf"
}

@Composable
fun ProtectScreen(
    onBack: () -> Unit,
    onOpen: (Uri) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Uri?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("set") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var saveMode by remember { mutableStateOf("new") }
    var pending by remember { mutableStateOf<File?>(null) }
    var pendingIsProtected by remember { mutableStateOf(true) }

    val onPicked: (Uri?) -> Unit = { uri ->
        if (uri != null) {
            source = uri
            sourceName = pickedNameOf(context, uri)
            password = ""
            confirm = ""
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
        onPicked
    )

    LaunchedEffect(Unit) {
        ToolSeed.take()?.let { onPicked(it) }
    }

    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { destination ->
        val file = pending
        if (destination == null || file == null) {
            file?.delete()
            pending = null
            busy = false
        } else {
            scope.launch {
                try {
                    val written = withContext(Dispatchers.IO) {
                        PdfActions.saveCopy(context, file, destination)
                    }
                    val verified = if (written) {
                        if (pendingIsProtected) {
                            PdfTools.verifyProtected(context, destination, password)
                        } else {
                            PdfTools.verifyUnprotected(context, destination)
                        }
                    } else false

                    if (verified) {
                        val message = if (pendingIsProtected) {
                            "Password set. Remember your password."
                        } else {
                            "Password removed. The new PDF is unprotected."
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        onOpen(destination)
                    } else {
                        Toast.makeText(
                            context,
                            "The saved PDF failed verification. The original was not changed.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } finally {
                    file.delete()
                    pending = null
                    busy = false
                }
            }
        }
    }

    val mismatch = mode == "set" && confirm.isNotEmpty() && confirm != password
    val passwordReady = if (mode == "set") {
        password.length >= 4 && password == confirm
    } else {
        password.isNotEmpty()
    }
    val ready = source != null && passwordReady && !busy

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "PDF password",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (mode == "set") {
                        Button(
                            onClick = { mode = "set" },
                            modifier = Modifier.weight(1f)
                        ) { Text("Set password") }
                        OutlinedButton(
                            onClick = {
                                mode = "remove"
                                password = ""
                                confirm = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Remove password") }
                    } else {
                        OutlinedButton(
                            onClick = {
                                mode = "set"
                                password = ""
                                confirm = ""
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Set password") }
                        Button(
                            onClick = { mode = "remove" },
                            modifier = Modifier.weight(1f)
                        ) { Text("Remove password") }
                    }
                }

                OutlinedButton(
                    onClick = { picker.launch(arrayOf("application/pdf")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    Text("Choose a PDF")
                }
                if (source != null) {
                    Text(
                        text = sourceName,
                        modifier = Modifier.padding(top = 12.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Text(
                    text = "Save result",
                    modifier = Modifier.padding(top = 18.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = saveMode == "same", onClick = { saveMode = "same" })
                    Text("Replace same PDF")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = saveMode == "new", onClick = { saveMode = "new" })
                    Text("Save as new PDF")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = {
                        Text(if (mode == "set") "New password (at least 4 characters)" else "Current PDF password")
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                if (mode == "set") {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text("Confirm new password") },
                        singleLine = true,
                        isError = mismatch,
                        supportingText = {
                            if (mismatch) Text("Passwords do not match")
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "If you forget this password, the PDF cannot be opened again.",
                        modifier = Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Enter the current opening password. The source PDF will remain unchanged when you save as a new file.",
                        modifier = Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (busy) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            Button(
                onClick = {
                    val uri = source
                    if (uri != null) {
                        scope.launch {
                            busy = true
                            val isSettingPassword = mode == "set"
                            val result = if (isSettingPassword) {
                                PdfTools.protect(context, uri, password)
                            } else {
                                PdfTools.removePassword(context, uri, password)
                            }

                            if (result == null) {
                                busy = false
                                val error = PdfProtectionService.lastError ?: "PDF validation or save failed"
                                Toast.makeText(
                                    context,
                                    "Could not ${if (isSettingPassword) "set" else "remove"} password: $error",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else if (saveMode == "same") {
                                val backup = withContext(Dispatchers.IO) {
                                    UriActions.copyToCache(context, uri)
                                }
                                val written = backup != null && withContext(Dispatchers.IO) {
                                    PdfActions.saveCopyToUri(context, result, uri)
                                }
                                val verified = if (written) {
                                    if (isSettingPassword) {
                                        PdfTools.verifyProtected(context, uri, password)
                                    } else {
                                        PdfTools.verifyUnprotected(context, uri)
                                    }
                                } else false

                                if (!verified && backup != null) {
                                    withContext(Dispatchers.IO) {
                                        PdfActions.saveCopyToUri(context, backup, uri)
                                    }
                                }
                                backup?.delete()
                                result.delete()
                                busy = false

                                if (verified) {
                                    val message = if (isSettingPassword) {
                                        "Password set on the same PDF."
                                    } else {
                                        "Password removed from the same PDF."
                                    }
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    onOpen(uri)
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Could not safely replace the original PDF. The backup was restored when possible.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                pending = result
                                pendingIsProtected = isSettingPassword
                                val base = sourceName.removeSuffix(".pdf")
                                val suffix = if (isSettingPassword) "_protected.pdf" else "_unprotected.pdf"
                                saver.launch(base + suffix)
                            }
                        }
                    }
                },
                enabled = ready,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                Text(if (mode == "set") "Protect PDF" else "Remove password")
            }
        }
    }
}
