package com.dailybeat.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dailybeat.app.ui.DailyBeatNavigationBar
import com.dailybeat.app.ui.theme.DailyBeatTheme
import com.dailybeat.app.ui.today.BeatSummary
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class LargeTextLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Test fun metricsAndNavigationRemainReadableAtTwoHundredPercent() {
        requireDisposableTestApp()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                DailyBeatTheme {
                    Column(Modifier.width(320.dp)) {
                        BeatSummary("Today’s beat", "A busy day", "Distance", "~4.0 km", "Tracked time", "2 hr 15 min", "Auto stops", "12")
                        DailyBeatNavigationBar("today", {})
                    }
                }
            }
        }
        listOf("~4.0 km", "2 hr 15 min", "Auto stops", "Settings").forEach { text ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("No measured text for $text", layouts.isNotEmpty())
            val result = layouts.single()
            assertFalse("Text overflows at 200%: $text, size=${result.size}, constraints=${result.layoutInput.constraints}, " +
                "width=${result.didOverflowWidth}, height=${result.didOverflowHeight}, lines=${result.lineCount}", result.hasVisualOverflow)
        }
    }
}
