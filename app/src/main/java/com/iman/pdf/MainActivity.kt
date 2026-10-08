package com.iman.pdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.iman.pdf.data.UserPrefs
import com.iman.pdf.ui.MainScreen
import com.iman.pdf.ui.theme.ImanTheme
import com.iman.pdf.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = UserPrefs(applicationContext)
        val incoming: Uri? =
            if (intent?.action == Intent.ACTION_VIEW) intent.data else null

        setContent {
            val themeName by prefs.themeMode.collectAsState(initial = "SYSTEM")
            val mode = try {
                ThemeMode.valueOf(themeName)
            } catch (e: Exception) {
                ThemeMode.SYSTEM
            }
            ImanTheme(mode = mode) {
                MainScreen(incomingUri = incoming)
            }
        }
    }
}
