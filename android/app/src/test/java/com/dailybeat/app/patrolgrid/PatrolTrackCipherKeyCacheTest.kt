package com.dailybeat.app.patrolgrid

import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.security.PatrolCoordinates
import com.dailybeat.app.security.PatrolTrackCipher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PatrolTrackCipherKeyCacheTest {

    @Before
    fun installTestKeyStore() {
        TestAndroidKeyStore.install()
    }

    @After
    fun removeTestKeyStore() {
        TestAndroidKeyStore.uninstall()
    }

    @Test
    fun `cipher reuses cached non-exportable key handle after first lookup`() {
        val cipher = PatrolTrackCipher(ApplicationProvider.getApplicationContext())
        val firstCoordinates = PatrolCoordinates(13.001, 77.501, 7f)
        val secondCoordinates = PatrolCoordinates(13.002, 77.502, 8f)

        val firstPayload = cipher.encrypt("mission-1", 1_000L, firstCoordinates)
        assertEquals(1, TestAndroidKeyStore.keys.size)

        // Removing the JVM provider's backing entry makes any second key-store lookup
        // generate a different key. The live cipher should retain only the cached handle.
        TestAndroidKeyStore.keys.clear()
        val secondPayload = cipher.encrypt("mission-1", 2_000L, secondCoordinates)

        assertTrue(TestAndroidKeyStore.keys.isEmpty())
        assertEquals(firstCoordinates, cipher.decrypt("mission-1", 1_000L, firstPayload))
        assertEquals(secondCoordinates, cipher.decrypt("mission-1", 2_000L, secondPayload))
    }
}
