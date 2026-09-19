package com.dailybeat.app.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeocodingSettingsTest {
    private lateinit var context: Context
    @Before fun setup() { context=ApplicationProvider.getApplicationContext(); clear() }
    @After fun clear() { context.getSharedPreferences("dailybeat_settings",Context.MODE_PRIVATE).edit().clear().commit() }
    @Test fun `lookup is opt in and can be changed or disabled without an app update`() {
        val settings=SettingsRepository(context)
        assertEquals("",settings.geocodingEndpoint())
        settings.setGeocodingEndpoint("https://maps.example.org/reverse")
        assertEquals("https://maps.example.org/reverse",SettingsRepository(context).geocodingEndpoint())
        settings.setGeocodingEndpoint("")
        assertEquals("",settings.geocodingEndpoint())
    }
    @Test fun `public automatic endpoint credentials and insecure URLs are rejected`() {
        val settings=SettingsRepository(context)
        listOf("http://maps.example.org/reverse", "https://user:secret@maps.example.org/reverse",
            "https://maps.example.org/reverse?key=secret", "https://maps.example.org/reverse#fragment",
            "https://nominatim.openstreetmap.org/reverse", "https://NOMINATIM.openstreetmap.org./reverse").forEach {
            assertThrows(IllegalArgumentException::class.java) { settings.setGeocodingEndpoint(it) }
        }
        assertEquals("",settings.geocodingEndpoint())
    }
}
