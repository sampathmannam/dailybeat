package com.dailybeat.app.data.settings

import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.DailyBeatApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DailyBeatApp::class)
class CapturePauseTest {
    @Test
    fun pauseExpiresWithoutChangingThePersistentGpsPreference() {
        val repository = ApplicationProvider.getApplicationContext<DailyBeatApp>().settingsRepository
        val now = 1_000_000L
        val resumeAt = now + 60_000L
        repository.setGpsEnabled(true)
        repository.pauseCaptureUntil(resumeAt)

        assertTrue(repository.isCapturePaused(now))
        assertEquals(resumeAt, repository.capturePausedUntilMs(now))
        assertEquals(0L, repository.capturePausedUntilMs(resumeAt + 1))
        assertFalse(repository.isCapturePaused(resumeAt + 1))
        assertTrue(repository.get().gpsCaptureEnabled)
    }
}
