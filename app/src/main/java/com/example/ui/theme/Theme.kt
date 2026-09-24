package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DualCamDarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF97F0FF),
    secondary = Color(0xFFB0BEC5),
    onSecondary = Color(0xFF1E2836),
    secondaryContainer = Color(0xFF263238),
    onSecondaryContainer = Color(0xFFCFD8DC),
    tertiary = RecordRed,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF930026),
    onTertiaryContainer = Color(0xFFFFD9DD),
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B)
)

@Composable
fun DualCamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Camera apps look best in high-contrast professional dark mode
    MaterialTheme(
        colorScheme = DualCamDarkColorScheme,
        typography = Typography,
        content = content
    )
}
