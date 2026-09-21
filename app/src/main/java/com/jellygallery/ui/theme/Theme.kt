package com.jellygallery.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentAmber,
    onPrimary = Black,
    background = Black,
    onBackground = White,
    surface = DarkSurface,
    onSurface = White,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = GrayText,
    error = RedDelete,
    onError = White
)

private val LightColorScheme = lightColorScheme(
    primary = AccentAmber,
    onPrimary = White,
    background = Color(0xFFF9F9F9),
    onBackground = Black,
    surface = White,
    onSurface = Black,
    surfaceVariant = Color(0xFFEFEFEF),
    onSurfaceVariant = Color(0xFF555555),
    error = RedDelete,
    onError = White
)

@Composable
fun JellyGalleryTheme(
    darkTheme: Boolean = true, // Jelly Star のバッテリー消費を抑えるためデフォルトはダーク
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
