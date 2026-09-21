package com.dailybeat.app.geo

import android.Manifest
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.DailyBeatApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DailyBeatApp::class)
class GeocodingConsentTest {
    @Test
    fun `queued lookup rechecks current capture consent and permission`() = runBlocking(Dispatchers.IO) {
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        val permissions = shadowOf(app)
        permissions.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        app.settingsRepository.setGpsEnabled(true)
        app.settingsRepository.clearCapturePause()
        assertTrue(app.permitsGeocoding(11.45, 78.18))

        app.settingsRepository.pauseCaptureUntil(System.currentTimeMillis() + 60_000)
        assertFalse(app.permitsGeocoding(11.45, 78.18))
        app.settingsRepository.clearCapturePause()
        app.settingsRepository.setGpsEnabled(false)
        assertFalse(app.permitsGeocoding(11.45, 78.18))

        app.settingsRepository.setGpsEnabled(true)
        permissions.denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        assertFalse(app.permitsGeocoding(11.45, 78.18))
    }
}
