package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color.Black,
    primaryContainer = ElectricViolet,
    onPrimaryContainer = Color.White,
    secondary = NeonPink,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3F1435),
    onSecondaryContainer = Color(0xFFFFD8E4),
    tertiary = AmberGold,
    onTertiary = Color.Black,
    background = DarkStudioBg,
    onBackground = TextPrimary,
    surface = DarkStudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkStudioCard,
    onSurfaceVariant = TextSecondary,
    outline = GridLine
)

@Composable
fun VocalBeatAITheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
