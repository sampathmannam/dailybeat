package com.dailybeat.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailybeat.app.security.PatrolCoordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PatrolTrackSecurityTest {

    @Test
    fun routePointRoundTripsThroughAndroidKeystore() {
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        val original = PatrolCoordinates(12.9715987, 77.594566, 5.5f)
        val encrypted = app.patrolTrackCipher.encrypt("mission-security-test", 1_725_000_000L, original)

        assertNotEquals(original.latitude.toString(), encrypted.toString(Charsets.UTF_8))
        assertEquals(
            original,
            app.patrolTrackCipher.decrypt("mission-security-test", 1_725_000_000L, encrypted),
        )
    }

    @Test(expected = Exception::class)
    fun routePointCannotBeMovedToAnotherMission() {
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        val encrypted = app.patrolTrackCipher.encrypt(
            "mission-a",
            1_725_000_000L,
            PatrolCoordinates(12.9715987, 77.594566, 5.5f),
        )

        app.patrolTrackCipher.decrypt("mission-b", 1_725_000_000L, encrypted)
    }

    @Test
    fun patrolTrackSchemaContainsNoPlaintextCoordinateColumns() {
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        val columns = mutableSetOf<String>()
        app.db.openHelper.readableDatabase.query("PRAGMA table_info(patrol_track_points)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) columns += cursor.getString(nameIndex)
        }

        assertTrue("encryptedPayload" in columns)
        assertFalse("latitude" in columns)
        assertFalse("longitude" in columns)
        assertFalse("accuracyM" in columns)
    }
}
