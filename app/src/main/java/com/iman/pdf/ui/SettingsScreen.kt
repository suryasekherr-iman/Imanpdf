package com.iman.pdf.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.iman.pdf.R
import com.iman.pdf.data.UserPrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private fun applyLocale(code: String) {
    val list = if (code == "system") {
        LocaleListCompat.getEmptyLocaleList()
    } else {
        LocaleListCompat.forLanguageTags(code)
    }
    AppCompatDelegate.setApplicationLocales(list)
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { UserPrefs(context) }
    val theme by prefs.themeMode.collectAsState(initial = "SYSTEM")
    val language by prefs.language.collectAsState(initial = "system")
    val photo by prefs.photoPath.collectAsState(initial = "")
    val hideRecent by prefs.hideRecentDocuments.collectAsState(initial = false)
    var nameField by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val path = ProfilePhoto.save(context, uri)
                if (path != null) {
                    prefs.setPhotoPath(path)
                } else {
                    Toast.makeText(context, context.getString(R.string.settings_photo_error), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        nameField = prefs.userName.first()
    }

    val themeOptions = listOf(
        "SYSTEM" to context.getString(R.string.settings_system_default),
        "LIGHT" to context.getString(R.string.settings_light),
        "DARK" to context.getString(R.string.settings_dark)
    )
    val languageOptions = listOf(
        "system" to "System default",
        "en" to context.getString(R.string.settings_english),
        "bn" to context.getString(R.string.settings_bengali),
        "hi" to context.getString(R.string.settings_hindi)
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                }
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            SectionTitle(stringResource(R.string.settings_profile))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(
                    path = photo,
                    size = 80.dp,
                    modifier = Modifier.clickable { picker.launch("image/*") }
                )
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    TextButton(onClick = { picker.launch("image/*") }) {
                        Text(if (photo.isEmpty()) stringResource(R.string.settings_add_photo) else stringResource(R.string.settings_change_photo))
                    }
                    if (photo.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    prefs.setPhotoPath("")
                                    ProfilePhoto.clear(context)
                                }
                            }
                        ) {
                            Text(stringResource(R.string.settings_remove_photo))
                        }
                    }
                }
            }
            OutlinedTextField(
                value = nameField,
                onValueChange = { nameField = it },
                label = { Text(stringResource(R.string.settings_your_name)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val value = nameField.trim()
                    if (value.isNotEmpty()) {
                        scope.launch { prefs.setUserName(value) }
                        Toast.makeText(context, context.getString(R.string.settings_name_saved), Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(stringResource(R.string.settings_save_name))
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle(stringResource(R.string.settings_theme))
            themeOptions.forEach { option ->
                OptionRow(
                    label = option.second,
                    selected = theme == option.first,
                    onClick = {
                        scope.launch { prefs.setThemeMode(option.first) }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle(stringResource(R.string.settings_language))
            languageOptions.forEach { option ->
                OptionRow(
                    label = option.second,
                    selected = language == option.first,
                    onClick = {
                        scope.launch {
                            prefs.setLanguage(option.first)
                            applyLocale(option.first)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle(stringResource(R.string.settings_privacy))
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    scope.launch { prefs.setHideRecentDocuments(!hideRecent) }
                }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = hideRecent,
                    onCheckedChange = { value -> scope.launch { prefs.setHideRecentDocuments(value) } }
                )
                Text(
                    text = stringResource(R.string.settings_hide_recent_documents),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Text(
                text = stringResource(R.string.settings_hide_recent_description),
                modifier = Modifier.padding(horizontal = 56.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(16.dp))
            AppLockSection()

            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle(stringResource(R.string.settings_about))
            Text(
                text = stringResource(R.string.settings_version),
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
