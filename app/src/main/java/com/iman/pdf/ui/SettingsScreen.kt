package com.iman.pdf.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
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
    var nameField by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        nameField = prefs.userName.first()
    }

    val themeOptions = listOf(
        "SYSTEM" to "System default",
        "LIGHT" to "Light",
        "DARK" to "Dark"
    )
    val languageOptions = listOf(
        "system" to "System default",
        "en" to "English",
        "bn" to "বাংলা (Bengali)",
        "hi" to "हिन्दी (Hindi)"
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            SectionTitle("Profile")
            OutlinedTextField(
                value = nameField,
                onValueChange = { nameField = it },
                label = { Text("Your name") },
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
                        Toast.makeText(context, "Name saved", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text("Save name")
            }

            Spacer(modifier = Modifier.height(16.dp))
            SectionTitle("Theme")
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
            SectionTitle("Language")
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
            SectionTitle("About")
            Text(
                text = "Iman PDF Reader  •  Version 1.0",
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
