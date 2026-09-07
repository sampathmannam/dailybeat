package com.dailybeat.app

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailybeat.app.audit.OperationalFailureLog
import com.dailybeat.app.capture.LocationService
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Passive capture is the whole point of the app, and it does not survive a force-stop or an
 * app update. [MainActivity] must therefore re-arm it every time the officer opens the app;
 * without that, tracking stays dead until the next reboot while the UI still claims it is on.
 */
@RunWith(AndroidJUnit4::class)
class CaptureLifecycleTest {

    private lateinit var app: DailyBeatApp

    @Before
    fun setUp() {
        grantCorePermissions()
        app = ApplicationProvider.getApplicationContext()
        app.settingsRepository.setOnboardingComplete(true)
        app.settingsRepository.setGpsEnabled(true)
        OperationalFailureLog.clear(app)
        stopCaptureAndWait()
    }

    @After
    fun tearDown() {
        stopCaptureAndWait()
    }

    @Test
    fun openingTheAppReArmsPassiveCapture() {
        assertFalse("Precondition: capture must be stopped before launch.", LocationService.isRunning)

        ActivityScenario.launch(MainActivity::class.java).use {
            val restarted = waitFor(timeoutMs = 15_000) { LocationService.isRunning }
            assertTrue(
                "Opening the app did not restart passive GPS capture. Diagnostics: " +
                    OperationalFailureLog.readRecent(app).joinToString(),
                restarted,
            )
        }
    }

    @Test
    fun captureStaysOffWhenTheOfficerTurnedGpsOff() {
        app.settingsRepository.setGpsEnabled(false)

        ActivityScenario.launch(MainActivity::class.java).use {
            Thread.sleep(3_000)
            assertFalse(
                "Capture started even though GPS is switched off in Settings.",
                LocationService.isRunning,
            )
        }
        app.settingsRepository.setGpsEnabled(true)
    }

    private fun stopCaptureAndWait() {
        LocationService.stop(app)
        waitFor(timeoutMs = 10_000) { !LocationService.isRunning }
    }

    private fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }
}
