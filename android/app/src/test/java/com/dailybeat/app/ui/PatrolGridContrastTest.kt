package com.dailybeat.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.dailybeat.app.ui.theme.Canvas
import com.dailybeat.app.ui.theme.SuccessAccessibleDark
import com.dailybeat.app.ui.theme.SuccessAccessibleLight
import com.dailybeat.app.ui.theme.WarningAccessibleLight
import org.junit.Assert.assertTrue
import org.junit.Test

class PatrolGridContrastTest {

    @Test
    fun operationalStatusTextMeetsNormalTextContrastInBothSchemes() {
        assertContrastAtLeast(WarningAccessibleLight, Canvas, 4.5f)
        assertContrastAtLeast(SuccessAccessibleLight, Canvas, 4.5f)
        assertContrastAtLeast(Color(0xFFF5D78E), Color(0xFF0C2236), 4.5f)
        assertContrastAtLeast(SuccessAccessibleDark, Color(0xFF0C2236), 4.5f)
    }

    @Test
    fun lightThemeSecondaryTextAndBoundariesStayLegible() {
        assertContrastAtLeast(Color(0xFF56667A), Canvas, 4.5f)
        assertContrastAtLeast(Color(0xFF77889C), Color.White, 3.0f)
    }

    private fun assertContrastAtLeast(foreground: Color, background: Color, minimum: Float) {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        val ratio = (lighter + 0.05f) / (darker + 0.05f)
        assertTrue("Expected contrast >= $minimum but was $ratio", ratio >= minimum)
    }
}
