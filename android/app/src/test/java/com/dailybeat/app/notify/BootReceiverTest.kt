package com.dailybeat.app.notify

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.PatrolRetentionStartupState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DailyBeatApp::class)
class BootReceiverTest {

    @Test
    fun configuredCheckingDefersCaptureAndStopsAnyExistingService() {
        val context = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        var applyCount = 0
        var stopCount = 0
        val receiver = configuredReceiver(PatrolRetentionStartupState.CHECKING).apply {
            applyConfiguredCapture = { applyCount += 1 }
            stopCapture = { stopCount += 1 }
        }

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertEquals(0, applyCount)
        assertEquals(1, stopCount)
    }

    @Test
    fun configuredBlockedDefersCaptureAndStopsAnyExistingService() {
        val context = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        var applyCount = 0
        var stopCount = 0
        val receiver = configuredReceiver(PatrolRetentionStartupState.BLOCKED).apply {
            applyConfiguredCapture = { applyCount += 1 }
            stopCapture = { stopCount += 1 }
        }

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertEquals(0, applyCount)
        assertEquals(1, stopCount)
    }

    @Test
    fun configuredReadyDelegatesResumeToCaptureController() {
        val context = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        var applyCount = 0
        var stopCount = 0
        val receiver = configuredReceiver(PatrolRetentionStartupState.READY).apply {
            applyConfiguredCapture = { applyCount += 1 }
            stopCapture = { stopCount += 1 }
        }

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertEquals(1, applyCount)
        assertEquals(0, stopCount)
    }

    @Test
    fun unconfiguredLegacyBuildStillDelegatesResume() {
        val context = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        var applyCount = 0
        val receiver = BootReceiver().apply {
            isPatrolGridConfigured = { false }
            retentionStartupState = { PatrolRetentionStartupState.BLOCKED }
            applyConfiguredCapture = { applyCount += 1 }
        }

        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertEquals(1, applyCount)
    }

    @Test
    fun unrelatedBroadcastDoesNothing() {
        val context = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        var applyCount = 0
        val receiver = configuredReceiver(PatrolRetentionStartupState.READY).apply {
            applyConfiguredCapture = { applyCount += 1 }
        }

        receiver.onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))

        assertEquals(0, applyCount)
    }

    private fun configuredReceiver(state: PatrolRetentionStartupState) = BootReceiver().apply {
        isPatrolGridConfigured = { true }
        retentionStartupState = { state }
    }
}
