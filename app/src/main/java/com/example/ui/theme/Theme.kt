package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val EditorialDarkColorScheme = darkColorScheme(
    primary = WireCrimsonDark,
    onPrimary = Color.White,
    primaryContainer = WireCrimsonContainerDark,
    onPrimaryContainer = Color(0xFFFFDADA),
    secondary = FolksCyanDark,
    onSecondary = Color(0xFF001E2B),
    secondaryContainer = FolksCyanContainerDark,
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = PaperSecondary,
    background = ObsidianBg,
    onBackground = PaperWhite,
    surface = ObsidianSurface,
    onSurface = PaperWhite,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = SlateMuted,
    outline = HairlineDark,
    outlineVariant = HairlineDark
)

private val EditorialLightColorScheme = lightColorScheme(
    primary = WireCrimson,
    onPrimary = Color.White,
    primaryContainer = WireCrimsonContainer,
    onPrimaryContainer = Color(0xFF5F0A0A),
    secondary = FolksCyan,
    onSecondary = Color.White,
    secondaryContainer = FolksCyanContainer,
    onSecondaryContainer = Color(0xFF063644),
    tertiary = InkSecondary,
    background = NewsprintBg,
    onBackground = InkPrimary,
    surface = NewsprintSurface,
    onSurface = InkPrimary,
    surfaceVariant = NewsprintSurfaceVariant,
    onSurfaceVariant = InkMuted,
    outline = HairlineLight,
    outlineVariant = HairlineLight
)

@Composable
fun FolksWireTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> EditorialDarkColorScheme
        else -> EditorialLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
