package com.dailybeat.app.cloud

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.dailybeat.app.data.retention.HistoryRetentionWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutomaticReportConsentTest {
    @Test fun `disabled automatic consent skips cloud readiness and generation`() = runBlocking {
        val result = runAutomaticReportIfAllowed(
            automaticEnabled = { false },
            cloudReady = { error("The credential store must not be touched") },
            generate = { error("A disabled automatic report must not start") },
        )
        assertNull(result)
    }

    @Test fun `enabled automatic and cloud consent allows generation`() = runBlocking {
        var calls = 0
        val result = runAutomaticReportIfAllowed({ true }, { true }) {
            calls++
            Result.success("report")
        }
        assertEquals(1, calls)
        assertEquals("report", result?.getOrThrow())
    }

    @Test fun `worker cancellation is propagated instead of becoming a retry`() = runBlocking {
        try {
            runAutomaticReportIfAllowed({ true }, { true }) { throw CancellationException("cancelled") }
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) { }
    }

    @Test fun `automatic cancellation includes old class-tagged requests and leaves unrelated work alone`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = try {
            WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            WorkManager.initialize(context, Configuration.Builder().build())
            WorkManager.getInstance(context)
        }
        val automatic = OneTimeWorkRequestBuilder<ReportRetryWorker>().setInitialDelay(1, TimeUnit.DAYS).build()
        val unrelated = OneTimeWorkRequestBuilder<HistoryRetentionWorker>().setInitialDelay(1, TimeUnit.DAYS).build()
        assertTrue(automatic.tags.contains(ReportRetryWorker::class.java.name))
        manager.enqueue(listOf(automatic, unrelated)).result.get(10, TimeUnit.SECONDS)
        ReportRetryWorker.cancelAutomatic(context)
        // A later operation on WorkManager's serial task executor observes cancellation first.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        var state: WorkInfo.State?
        do {
            state = manager.getWorkInfoById(automatic.id).get(5, TimeUnit.SECONDS)?.state
            if (state == WorkInfo.State.CANCELLED) break
            Thread.sleep(10)
        } while (System.nanoTime() < deadline)
        assertEquals(WorkInfo.State.CANCELLED, state)
        assertEquals(WorkInfo.State.ENQUEUED, manager.getWorkInfoById(unrelated.id).get(5, TimeUnit.SECONDS)?.state)
        manager.cancelWorkById(unrelated.id).result.get(10, TimeUnit.SECONDS)
    }
}
