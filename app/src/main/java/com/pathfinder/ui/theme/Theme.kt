package com.pathfinder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
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

/** Alto contraste: preto e branco puros, cores fortes (para ler no sol). */
private val HighContrastLight = lightColorScheme(
    primary = Color(0xFF00320F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF00320F),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF4A2C00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE08A),
    onSecondaryContainer = Color.Black,
    tertiaryContainer = Color(0xFFFFE08A),
    onTertiaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE6E6E6),
    onSurfaceVariant = Color.Black,
    outline = Color.Black,
    error = Color(0xFFB00000),
)

private val HighContrastDark = darkColorScheme(
    primary = Color(0xFF7CFF9E),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF7CFF9E),
    onPrimaryContainer = Color.Black,
    secondaryContainer = Color(0xFFFFE08A),
    onSecondaryContainer = Color.Black,
    tertiaryContainer = Color(0xFFFFE08A),
    onTertiaryContainer = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color.White,
    outline = Color.White,
    error = Color(0xFFFF6E6E),
)

/** Letras 15% maiores no alto contraste. */
private fun Typography.scaled(factor: Float): Typography {
    fun TextStyle.s() = copy(fontSize = fontSize * factor, lineHeight = lineHeight * factor)
    return Typography(
        displayLarge = displayLarge.s(), displayMedium = displayMedium.s(), displaySmall = displaySmall.s(),
        headlineLarge = headlineLarge.s(), headlineMedium = headlineMedium.s(), headlineSmall = headlineSmall.s(),
        titleLarge = titleLarge.s(), titleMedium = titleMedium.s(), titleSmall = titleSmall.s(),
        bodyLarge = bodyLarge.s(), bodyMedium = bodyMedium.s(), bodySmall = bodySmall.s(),
        labelLarge = labelLarge.s(), labelMedium = labelMedium.s(), labelSmall = labelSmall.s(),
    )
}

@Composable
fun PathFinderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        highContrast && darkTheme -> HighContrastDark
        highContrast -> HighContrastLight
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = if (highContrast) Typography().scaled(1.15f) else Typography(),
        content = content,
    )
}
