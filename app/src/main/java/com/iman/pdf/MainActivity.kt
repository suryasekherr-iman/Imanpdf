package com.iman.pdf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.iman.pdf.ui.MainScreen
import com.iman.pdf.ui.theme.ImanTheme
import com.iman.pdf.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ImanTheme(mode = ThemeMode.SYSTEM) {
                MainScreen()
            }
        }
    }
}
