package com.dailybeat.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = SurfaceElevated,
    onPrimaryContainer = Navy,
    secondary = Gold,
    onSecondary = Ink,
    secondaryContainer = GoldSoft,
    onSecondaryContainer = Ink,
    background = Canvas,
    onBackground = Ink,
    surface = SurfaceCard,
    onSurface = Ink,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = InkMuted,
    outline = OutlineSoft,
    error = ErrorRed,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB8D4FF),
    onPrimary = NightCanvas,
    primaryContainer = Color(0xFF183F6E),
    onPrimaryContainer = NightInk,
    secondary = Gold,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF514600),
    onSecondaryContainer = Color(0xFFFFF4B8),
    background = NightCanvas,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = NightElevated,
    onSurfaceVariant = NightMuted,
    outline = Color(0xFF6F8194),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun DailyBeatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = DailyBeatTypography,
        shapes = DailyBeatShapes,
        content = content,
    )
}
