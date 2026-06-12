package com.vitpool.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF0F6B5F),
    onPrimary = Color.White,
    secondary = Color(0xFF365B6D),
    tertiary = Color(0xFF7C5B24),
    background = Color(0xFFF8FAF9),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE1E7E4),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF72D7C8),
    secondary = Color(0xFFA8CFE0),
    tertiary = Color(0xFFEAC784),
    background = Color(0xFF111816),
    surface = Color(0xFF18211F),
    surfaceVariant = Color(0xFF394340),
)

@Composable
fun VitPoolTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
