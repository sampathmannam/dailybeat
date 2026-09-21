package com.dailybeat.app.data.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.capture.CaptureStorageGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CapturePrivacyEpochTest {
    private lateinit var settings: SettingsRepository

    @Before fun before() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE).edit().clear().commit()
        settings = SettingsRepository(context)
    }

    @Test fun queuedCallbackRemainsInvalidAfterDisableAndImmediateReenable() {
        val callbackEpoch = CaptureStorageGate.generation.get()
        settings.setGpsEnabled(false)
        settings.setGpsEnabled(true)
        assertNotEquals(callbackEpoch, CaptureStorageGate.generation.get())
    }

    @Test fun queuedCallbackRemainsInvalidAfterPauseAndImmediateResume() {
        val callbackEpoch = CaptureStorageGate.generation.get()
        settings.pauseCaptureUntil(System.currentTimeMillis() + 60_000)
        settings.clearCapturePause()
        assertNotEquals(callbackEpoch, CaptureStorageGate.generation.get())
    }

    @Test fun ordinaryEnablingDoesNotInvalidateValidCaptureWork() {
        val callbackEpoch = CaptureStorageGate.generation.get()
        settings.setGpsEnabled(true)
        settings.pauseCaptureUntil(0)
        settings.clearCapturePause()
        assertEquals(callbackEpoch, CaptureStorageGate.generation.get())
    }
}
