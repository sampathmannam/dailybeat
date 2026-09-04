package com.dailybeat.app.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.dailybeat.app.BuildConfig

/**
 * The OpenFreeMap style matching the current theme.
 *
 * PatrolGrid follows the system theme, and its flagship mission is a 22:00-02:00 night
 * patrol, so a daylight map inside the dark UI is a night-vision problem in a vehicle.
 * A single dark style is not the answer either: it leaves a dark panel sitting in the
 * light UI. Pick per theme instead. Both URLs are source-pinned and verified in the
 * release build.
 */
@Composable
fun patrolMapStyleUrl(): String = if (isSystemInDarkTheme()) {
    BuildConfig.PATROLGRID_MAP_STYLE_URL_DARK
} else {
    BuildConfig.PATROLGRID_MAP_STYLE_URL
}
