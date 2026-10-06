package com.iman.pdf.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "iman_prefs")

class UserPrefs(private val context: Context) {

    private val nameKey = stringPreferencesKey("user_name")
    private val themeKey = stringPreferencesKey("theme_mode")
    private val languageKey = stringPreferencesKey("language")

    val userName: Flow<String> =
        context.dataStore.data.map { it[nameKey] ?: "Indrajit" }

    val themeMode: Flow<String> =
        context.dataStore.data.map { it[themeKey] ?: "SYSTEM" }

    val language: Flow<String> =
        context.dataStore.data.map { it[languageKey] ?: "system" }

    suspend fun setUserName(value: String) {
        context.dataStore.edit { it[nameKey] = value }
    }

    suspend fun setThemeMode(value: String) {
        context.dataStore.edit { it[themeKey] = value }
    }

    suspend fun setLanguage(value: String) {
        context.dataStore.edit { it[languageKey] = value }
    }
}
