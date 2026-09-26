package com.dailybeat.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailybeat.app.ui.theme.DailyBeatTheme
import com.dailybeat.app.ui.today.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PatternSuggestionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun suggestionsAreReadableInBothThemesAtLargeText() {
        requireDisposableTestApp()
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        val dark = mutableStateOf(false)
        val analysis = TodayPatternAnalysis(observedDays = 4, todayStops = 3, averageStopsPerDay = 2.5,
            recurringPlace = "Library", recurringPlaceVisits = 3, stopsToName = 2,
            suggestions = listOf(PatternSuggestion.NAME_STOPS, PatternSuggestion.NOTE_RECURRING_PLACE))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                DailyBeatTheme(darkTheme = dark.value) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            TodayPatternDashboard(analysis)
                        }
                    }
                }
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            compose.onNodeWithText("Suggestions for you").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(app.getString(R.string.today_pattern_suggest_note, "Library"))
                .performScrollTo().assertIsDisplayed()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            try {
                val directory = File(app.getExternalFilesDir(null), "pattern-suggestions").apply { mkdirs() }
                File(directory, "suggestions-${if (night) "dark" else "light"}.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally { bitmap.recycle() }
        }
    }
}
