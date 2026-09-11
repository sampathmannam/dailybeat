package com.dailybeat.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Whether the colour scheme currently in force is the dark one. `isSystemInDarkTheme()` is the
 * wrong question for this app: the officer can pin Light or Dark in Settings regardless of the
 * phone, and anything that picks a colour outside `MaterialTheme.colorScheme` has to follow that
 * choice, not the system's.
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

private val LightColorScheme = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = SurfaceElevated,
    onPrimaryContainer = Navy,
    secondary = Gold,
    onSecondary = Ink,
    secondaryContainer = GoldSoft,
    onSecondaryContainer = Ink,
    // Attention states — "Needs review", "Waiting for GPS". Left at Material's default this role is
    // a mauve-pink that has nothing to do with the navy-and-gold identity.
    tertiary = WarningAmber,
    onTertiary = Color.White,
    tertiaryContainer = WarningAmberSoft,
    onTertiaryContainer = WarningAmberInk,
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
    tertiary = NightAmber,
    onTertiary = Color(0xFF3B2300),
    tertiaryContainer = NightAmberSoft,
    onTertiaryContainer = NightAmberInk,
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
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = DailyBeatTypography,
            shapes = DailyBeatShapes,
            content = content,
        )
    }
}
