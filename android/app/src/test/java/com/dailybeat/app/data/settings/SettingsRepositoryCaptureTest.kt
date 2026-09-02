package com.dailybeat.app.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryCaptureTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences().edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences().edit().clear().commit()
    }

    @Test
    fun `capture failure persists across process repository recreation and clears`() {
        val repository = SettingsRepository(context)
        repository.setPatrolCaptureError("Secure GPS recording stopped")

        assertEquals(
            "Secure GPS recording stopped",
            SettingsRepository(context).get().patrolCaptureError,
        )

        repository.setPatrolCaptureError(null)
        assertNull(SettingsRepository(context).get().patrolCaptureError)
    }

    @Test
    fun `stopping patrol atomically preserves pending close and disables capture`() {
        val repository = SettingsRepository(context)
        repository.setActivePatrolMission("mission-1")
        repository.setActivePatrolSession("session-1")
        repository.setActivePatrolDeadline(9_999L)
        repository.setGpsEnabled(true)

        val stopped = repository.stopActivePatrol(
            pendingCloseReason = "device_issue",
            endedAtMs = 8_888L,
        )

        assertEquals("mission-1", stopped.missionId)
        assertEquals("session-1", stopped.sessionId)
        val restored = SettingsRepository(context).get()
        assertFalse(restored.gpsCaptureEnabled)
        assertNull(restored.activePatrolMissionId)
        assertNull(restored.activePatrolSessionId)
        assertNull(restored.activePatrolDeadlineMs)
        assertEquals("mission-1", restored.pendingPatrolCloseMissionId)
        assertEquals("session-1", restored.pendingPatrolCloseSessionId)
        assertEquals("device_issue", restored.pendingPatrolCloseReason)
        assertEquals(8_888L, restored.pendingPatrolCloseEndedAtMs)
    }

    private fun preferences() =
        context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE)
}
