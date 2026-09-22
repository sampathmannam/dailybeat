package com.dailybeat.app.capture

import android.Manifest
import android.app.Application
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34], application = Application::class)
class PlatformLocationSourceTest {
    @Test fun `stop removes compat transport as well as listener on every supported API`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val manager = context.getSystemService(LocationManager::class.java)
        val platform = shadowOf(manager)
        platform.setLocationEnabled(true)
        platform.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        val source = PlatformLocationSource(context, preferGps = true)
        source.start(ActiveCaptureProfile.MOVING, {}, {}, { throw it })
        assertEquals(1, platform.getLegacyLocationRequests(LocationManager.GPS_PROVIDER).size)

        source.stop()

        assertTrue(platform.getLegacyLocationRequests(LocationManager.GPS_PROVIDER).isEmpty())
    }

    @Test fun `profile changes do not accumulate active GPS registrations`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val manager = context.getSystemService(LocationManager::class.java)
        val platform = shadowOf(manager)
        platform.setLocationEnabled(true)
        platform.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        val source = PlatformLocationSource(context, preferGps = true)

        repeat(4) {
            source.start(ActiveCaptureProfile.MOVING, {}, {}, { throw it })
            source.start(ActiveCaptureProfile.SETTLING, {}, {}, { throw it })
            assertEquals(1, platform.getLegacyLocationRequests(LocationManager.GPS_PROVIDER).size)
        }
        source.stop()
        assertTrue(platform.getLegacyLocationRequests(LocationManager.GPS_PROVIDER).isEmpty())
    }
}
