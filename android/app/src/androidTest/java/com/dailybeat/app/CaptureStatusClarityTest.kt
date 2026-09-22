package com.dailybeat.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.dailybeat.app.capture.CaptureHealthLevel
import com.dailybeat.app.capture.CaptureHealthStatus
import com.dailybeat.app.ui.theme.DailyBeatTheme
import com.dailybeat.app.ui.today.CaptureOverview
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class CaptureStatusClarityTest {
    @get:Rule val compose = createComposeRule()

    private fun show(status: CaptureHealthStatus, darkTheme: Boolean) {
        requireDisposableTestApp()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                DailyBeatTheme(darkTheme = darkTheme) {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        CaptureOverview(status, gpsOn = true, cloudReady = false, onResumeCapture = {})
                    }
                }
            }
        }
    }

    private fun assertReadable(text: String, substring: Boolean = false) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, substring = substring, useUnmergedTree = true)
            .assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertFalse("Text overflows at 200%: $text, size=${layout.size}, constraints=${layout.layoutInput.constraints}, " +
                "width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}, lines=${layout.lineCount}", layout.hasVisualOverflow)
        }
    }

    private fun captureScreenshot(name: String) {
        if (InstrumentationRegistry.getArguments().getString("screenshots") != "true") return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Capture this Compose surface directly: a headless emulator can return null from
        // UiAutomation.takeScreenshot even when the app rendered and layout assertions passed.
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(instrumentation.targetContext.filesDir, "capture-status-evidence").apply { mkdirs() }
        try {
            File(directory, "$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
    }

    @Test fun approximateLocationExplainsUncertaintyAtLargeText() {
        show(CaptureHealthStatus(CaptureHealthLevel.APPROXIMATE, lastPointAgeMs = 30_000, accuracyM = 200f), false)
        assertReadable("Location is approximate")
        assertReadable("Nearby stops may be grouped.", substring = true)
        compose.onNodeWithText("Recording your route").assertDoesNotExist()
        captureScreenshot("approximate-light-200pct")
    }

    @Test fun missingLocationDoesNotClaimStillnessInDarkMode() {
        show(CaptureHealthStatus(CaptureHealthLevel.DEGRADED, lastPointAgeMs = 20 * 60_000), true)
        assertReadable("No recent location")
        assertReadable("your route may have a gap", substring = true)
        compose.onNodeWithText("No recent movement").assertDoesNotExist()
        captureScreenshot("missing-dark-200pct")
    }
}
