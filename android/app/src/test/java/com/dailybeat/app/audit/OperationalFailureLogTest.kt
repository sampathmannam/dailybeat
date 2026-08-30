package com.dailybeat.app.audit

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OperationalFailureLogTest {

    private lateinit var context: Context

    @get:Rule
    val temporaryFolder = TemporaryFolder()

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
    fun recordRedactsSecretsAndSensitivePayloads() {
        OperationalFailureLog.record(
            context,
            category = "cloud",
            retryable = false,
            message = "Bearer sk-live-secret prompt=private diary lat=12.9716 lon=77.5946",
        )

        val line = OperationalFailureLog.readRecent(context).single()

        assertFalse(line.contains("sk-live-secret"))
        assertFalse(line.contains("private diary"))
        assertFalse(line.contains("12.9716"))
        assertFalse(line.contains("77.5946"))
        assertTrue(line.contains("cloud"))
    }

    @Test
    fun recordRedactsEveryRequiredSensitivePatternAndFlattensNewlines() {
        OperationalFailureLog.record(
            context,
            category = "backup",
            retryable = true,
            message = "api key: top-secret\r\nprompt=private words diary=personal entry " +
                "latitude=-33.8688 longitude=151.2093 token=sk-another-secret",
        )

        val line = OperationalFailureLog.readRecent(context).single()

        listOf(
            "top-secret",
            "private words",
            "personal entry",
            "-33.8688",
            "151.2093",
            "sk-another-secret",
            "\r",
            "\n",
        ).forEach { sensitive -> assertFalse(line.contains(sensitive)) }
        assertTrue(line.contains("backup"))
        assertTrue(line.contains("true"))
    }

    @Test
    fun recordRedactsApiKeyLabelWithoutPunctuation() {
        OperationalFailureLog.record(context, "cloud", false, "api key no-separator-secret")

        val line = OperationalFailureLog.readRecent(context).single()

        assertFalse(line.contains("no-separator-secret"))
    }

    @Test
    fun recordTruncatesSanitizedMessageToOneHundredSixtyCharacters() {
        OperationalFailureLog.record(context, "map", false, "x".repeat(200))

        val message = OperationalFailureLog.readRecent(context).single().substringAfterLast(" | ")

        assertEquals(160, message.length)
    }

    @Test
    fun recordKeepsOnlyNewestEightyLines() {
        repeat(100) { OperationalFailureLog.record(context, "map", true, "failure-$it") }

        val lines = OperationalFailureLog.readRecent(context, 100)

        assertEquals(80, lines.size)
        assertTrue(lines.first().endsWith("failure-20"))
        assertTrue(lines.last().endsWith("failure-99"))
    }

    @Test
    fun readRecentHonorsRequestedLimitAndClearRemovesRecords() {
        repeat(5) { OperationalFailureLog.record(context, "capture-gps", false, "failure-$it") }

        assertEquals(2, OperationalFailureLog.readRecent(context, 2).size)

        OperationalFailureLog.clear(context)

        assertTrue(OperationalFailureLog.readRecent(context).isEmpty())
    }

    @Test
    fun ioFailuresNeverEscapeDiagnosticsApi() {
        val filesDirBlocker = temporaryFolder.newFile("not-a-directory")
        val brokenContext = object : ContextWrapper(context) {
            override fun getFilesDir(): File = filesDirBlocker
        }

        OperationalFailureLog.record(brokenContext, "cloud", false, "failure")

        assertTrue(OperationalFailureLog.readRecent(brokenContext).isEmpty())
        OperationalFailureLog.clear(brokenContext)
    }
}
