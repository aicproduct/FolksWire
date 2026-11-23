package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "reader_preferences")

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Newsprint"),
    DARK("Obsidian")
}

enum class ReaderFontScale(val label: String, val multiplier: Float) {
    COMPACT("Compact", 0.9f),
    STANDARD("Standard", 1.0f),
    LARGE("Large", 1.2f)
}

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: ReaderFontScale = ReaderFontScale.STANDARD
)

class ReaderPreferences(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val fontScaleKey = stringPreferencesKey("font_scale")

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val theme = prefs[themeKey]?.let { name ->
            ThemeMode.entries.firstOrNull { it.name == name }
        } ?: ThemeMode.SYSTEM
        val scale = prefs[fontScaleKey]?.let { name ->
            ReaderFontScale.entries.firstOrNull { it.name == name }
        } ?: ReaderFontScale.STANDARD
        UserPreferences(themeMode = theme, fontScale = scale)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[themeKey] = mode.name
        }
    }

    suspend fun setFontScale(scale: ReaderFontScale) {
        context.dataStore.edit { prefs ->
            prefs[fontScaleKey] = scale.name
        }
    }
}
