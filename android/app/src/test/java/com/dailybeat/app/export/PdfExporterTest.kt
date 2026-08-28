package com.dailybeat.app.export

import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
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
}
