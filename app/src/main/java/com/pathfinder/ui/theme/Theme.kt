package com.pathfinder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Forest = Color(0xFF2E5E3E)
private val ForestLight = Color(0xFF9CCFA8)
private val Earth = Color(0xFF8D6E3F)
private val Alert = Color(0xFFC62828)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6CF),
    onPrimaryContainer = Color(0xFF0B2E16),
    secondary = Earth,
    onSecondary = Color.White,
    error = Alert,
)

private val DarkColors = darkColorScheme(
    primary = ForestLight,
    onPrimary = Color(0xFF0B2E16),
    primaryContainer = Color(0xFF1E4429),
    onPrimaryContainer = Color(0xFFC8E6CF),
    secondary = Color(0xFFD9BC8C),
    error = Color(0xFFEF9A9A),
)

@Composable
fun PathFinderTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
