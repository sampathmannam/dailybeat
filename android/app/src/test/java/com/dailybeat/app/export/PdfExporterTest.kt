package com.dailybeat.app.export

import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PdfExporterTest {

    private val exporter = PdfExporter(ApplicationProvider.getApplicationContext())

    @Test
    fun wrapLine_splitsLongLines() {
        val paint = Paint().apply { textSize = 12f }
        val lines = exporter.wrapLine(
            "This is a long line of text that should be wrapped into multiple lines for PDF export",
            paint,
            80f,
        )
        assertTrue(lines.size > 1)
    }

    @Test
    fun wrapLine_splitsOneUnbrokenTokenToPageWidth() {
        val paint = Paint().apply { textSize = 12f }

        val lines = exporter.wrapLine("x".repeat(1_000), paint, 80f)

        assertTrue(lines.size > 1)
        assertFalse(lines.any { paint.measureText(it) > 80f })
    }

    @Test
    fun wrapLine_keepsUnicodeCodePointsIntactWhenSplitting() {
        val paint = Paint().apply { textSize = 12f }
        val token = "🙂".repeat(200)

        val lines = exporter.wrapLine(token, paint, 80f)

        assertEquals(token, lines.joinToString(separator = ""))
        assertFalse(lines.any { line ->
            line.isNotEmpty() &&
                (Character.isLowSurrogate(line.first()) || Character.isHighSurrogate(line.last()))
        })
    }
}
