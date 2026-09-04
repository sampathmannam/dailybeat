package com.dailybeat.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = SurfaceElevated,
    onPrimaryContainer = Navy,
    secondary = WarningAccessibleLight,
    onSecondary = Color.White,
    secondaryContainer = GoldSoft,
    onSecondaryContainer = Ink,
    background = Canvas,
    onBackground = Ink,
    surface = SurfaceCard,
    onSurface = Ink,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = Color(0xFF56667A),
    outline = Color(0xFF77889C),
    error = ErrorRed,
)

private val DarkColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Ink,
    primaryContainer = NavySoft,
    onPrimaryContainer = Color.White,
    secondary = GoldSoft,
    onSecondary = Ink,
    secondaryContainer = Navy,
    onSecondaryContainer = Color.White,
    background = NightCanvas,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = NightSurfaceElevated,
    onSurfaceVariant = NightMuted,
    outline = NightOutline,
    error = Color(0xFFFF6B6B),
)

@Composable
fun DailyBeatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // statusBarColor / navigationBarColor were deprecated in API 35 (Android 15).
            // PatrolGrid already runs edge-to-edge; rely on the WindowCompat controller
            // for light/dark icons and the system bar background drawn by the theme.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = DailyBeatTypography,
        shapes = DailyBeatShapes,
        content = content,
    )
}
