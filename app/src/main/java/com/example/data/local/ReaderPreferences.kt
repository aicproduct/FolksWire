package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    val fontScale: ReaderFontScale = ReaderFontScale.STANDARD,
    val adsEnabled: Boolean = true,
    val adFeedUrl: String = DEFAULT_AD_FEED_URL
) {
    companion object {
        const val DEFAULT_AD_FEED_URL = "https://hashtagfolks.com/ads.json"
    }
}

class ReaderPreferences(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val fontScaleKey = stringPreferencesKey("font_scale")
    private val adsEnabledKey = booleanPreferencesKey("ads_enabled")
    private val adFeedUrlKey = stringPreferencesKey("ad_feed_url")

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val theme = prefs[themeKey]?.let { name ->
            ThemeMode.entries.firstOrNull { it.name == name }
        } ?: ThemeMode.SYSTEM
        val scale = prefs[fontScaleKey]?.let { name ->
            ReaderFontScale.entries.firstOrNull { it.name == name }
        } ?: ReaderFontScale.STANDARD
        val adsEnabled = prefs[adsEnabledKey] ?: true
        val adFeedUrl = prefs[adFeedUrlKey]?.takeIf { it.isNotBlank() }
            ?: UserPreferences.DEFAULT_AD_FEED_URL
        UserPreferences(
            themeMode = theme,
            fontScale = scale,
            adsEnabled = adsEnabled,
            adFeedUrl = adFeedUrl
        )
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

    suspend fun setAdsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[adsEnabledKey] = enabled
        }
    }

    suspend fun setAdFeedUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[adFeedUrlKey] = url.trim().ifBlank { UserPreferences.DEFAULT_AD_FEED_URL }
        }
    }
}
