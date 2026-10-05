package com.miniphoto.viewer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF173D34),
    onPrimary = Color.White,
    secondary = Color(0xFFD85C41),
    tertiary = Color(0xFFF4C95D),
    background = Color(0xFFFFF9F5),
    surface = Color(0xFFFFF9F5),
    surfaceVariant = Color(0xFFF1E8DF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BD5C3),
    secondary = Color(0xFFFFB4A2),
    tertiary = Color(0xFFF4C95D),
    background = Color(0xFF101512),
    surface = Color(0xFF101512),
)

@Composable
fun MiniPhotoTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
