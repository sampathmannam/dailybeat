package com.dailybeat.app.capture

import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.audit.CaptureAuditLog
import com.dailybeat.app.data.model.StructuredEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DailyBeatApp::class)
class VoiceCaptureDataReplacementTest {
    @Test fun `voice recognition completing after erase neither uploads nor recreates records`() = boundedTest {
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var cloudCalls = 0
        val voice = VoiceCaptureOrchestrator(app,
            transcribe = { started.complete(Unit); release.await(); "Private spoken note" },
            canEnrich = { true },
            extract = { cloudCalls++; Result.success(StructuredEvent(rawText = it)) },
        )
        val result = async { voice.captureAndSave() }
        started.await()
        replaceData(app)
        release.complete(Unit)
        assertTrue(result.await().isFailure)
        assertEquals(0, cloudCalls)
        assertTrue(app.db.events().all().isEmpty())
        assertTrue(CaptureAuditLog.readRecent(app).none { it.contains("| voice |") })
    }

    @Test fun `late cloud voice reply cannot overwrite restored data or recreate audit`() = boundedTest {
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val voice = VoiceCaptureOrchestrator(app,
            transcribe = { "Old private spoken note" },
            canEnrich = { true },
            extract = { started.complete(Unit); release.await(); Result.success(StructuredEvent(rawText = it)) },
        )
        val result = async { voice.captureAndSave() }
        started.await()
        replaceData(app, "Restored note")
        release.complete(Unit)
        assertTrue(result.await().isFailure)
        assertEquals(listOf("Restored note"), app.db.events().all().map { it.rawText })
        assertTrue(CaptureAuditLog.readRecent(app).none { it.contains("| voice |") })
    }

    @Test fun `ordinary local voice note still saves once with its audit`() = boundedTest {
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        replaceData(app)
        val voice = VoiceCaptureOrchestrator(app, transcribe = { "Current voice note" }, canEnrich = { false })
        assertEquals("Current voice note", voice.captureAndSave().getOrThrow())
        assertEquals(listOf("Current voice note"), app.db.events().all().map { it.rawText })
        assertEquals(1, CaptureAuditLog.readRecent(app).count { it.contains("| voice |") })
    }

    private fun boundedTest(block: suspend CoroutineScope.() -> Unit) = runBlocking(Dispatchers.IO) {
        withTimeout(15_000L, block)
    }

    private suspend fun replaceData(app: DailyBeatApp, replacement: String? = null) {
        CaptureStorageGate.mutex.withLock {
            CaptureStorageGate.dataGeneration.incrementAndGet()
            app.db.clearAllTables()
            assertTrue(CaptureAuditLog.clear(app))
            if (replacement != null) app.eventRepository.addManualEvent(replacement)
        }
    }
}
