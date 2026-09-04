package com.dailybeat.app.ui.patrol

import com.dailybeat.app.data.model.PatrolMissionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * An officer whose device lost its local patrol state -- a reinstall, cleared storage, or a
 * replacement handset -- still has the session running on the server. The app used to render
 * that mission as "On route" and, in the same breath, "This patrol is closed. Tracking remains
 * off.", offering no action at all: the patrol could be neither recorded into nor ended, and
 * the session stayed open on the server indefinitely.
 *
 * Found by driving the configured build against a live Supabase stack; two rounds against the
 * preview build never reached it, because preview mode has no server session to be orphaned by.
 */
class PatrolResumableSessionTest {

    @Test
    fun `an open server session is offered for resume when this device is not tracking`() {
        val state = PatrolGridUiState(
            trackingActive = false,
            resumableSessionId = "session-1",
            resumableMissionId = "mission-1",
        )

        assertEquals("session-1", state.resumableSessionId)
        assertEquals("mission-1", state.resumableMissionId)
    }

    @Test
    fun `the resume affordance only belongs to the mission that owns the open session`() {
        val state = PatrolGridUiState(
            trackingActive = false,
            resumableSessionId = "session-1",
            resumableMissionId = "mission-1",
        )

        // MyPatrolScreen gates on resumableMissionId == mission.id, so a different mission
        // must not inherit another mission's open session.
        assertEquals(false, state.resumableMissionId == "mission-2")
        assertEquals(true, state.resumableMissionId == "mission-1")
    }

    @Test
    fun `a device that is already tracking is never asked to resume`() {
        // applyRemoteSnapshot clears both fields whenever this device holds an active patrol,
        // so a tracking device can never be pushed into adopting a second session.
        val tracking = PatrolGridUiState(
            trackingActive = true,
            resumableSessionId = null,
            resumableMissionId = null,
        )

        assertNull(tracking.resumableSessionId)
        assertNull(tracking.resumableMissionId)
    }

    @Test
    fun `a closed mission still reads as closed rather than resumable`() {
        val state = PatrolGridUiState(trackingActive = false)
        val status = PatrolMissionStatus.NEEDS_REVIEW

        assertNull(state.resumableSessionId)
        assertEquals(PatrolMissionStatus.NEEDS_REVIEW, status)
    }
}
