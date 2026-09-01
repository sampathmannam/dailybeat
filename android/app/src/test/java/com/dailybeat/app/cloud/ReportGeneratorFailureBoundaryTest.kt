package com.dailybeat.app.cloud

import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.audit.OperationalFailureLog
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DailyBeatApp::class)
class ReportGeneratorFailureBoundaryTest {

    private lateinit var context: DailyBeatApp

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        OperationalFailureLog.clear(context)
    }

    @After
    fun tearDown() {
        OperationalFailureLog.clear(context)
    }

    @Test
    fun integrityFailureRecordsOnlyFixedTextWithoutModelDerivedCitation() {
        recordDailyReportFailure(
            context,
            ReportIntegrityException(
                "Cloud report failed source-integrity validation: Unknown citation [V999] from model output.",
            ),
        )

        val line = OperationalFailureLog.readRecent(context).single()

        assertTrue(line.contains("daily-report-integrity"))
        assertTrue(line.endsWith("Daily report failed source-integrity validation."))
        assertFalse(line.contains("[V999]"))
        assertFalse(line.contains("model output"))
    }
}
