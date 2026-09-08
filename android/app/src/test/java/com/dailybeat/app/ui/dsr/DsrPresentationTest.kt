package com.dailybeat.app.ui.dsr

import org.junit.Assert.assertEquals
import org.junit.Test

class DsrPresentationTest {

    @Test
    fun `quality issue case key is formatted as an operational reference`() {
        assertEquals(
            "Rasipuram AWPS · 42/2099",
            formatDsrCaseReference("AWPS_RPM|2099|42"),
        )
    }

    @Test
    fun `conflict suffix is not exposed as part of the case number`() {
        assertEquals(
            "Vennandur · 9001/2099",
            formatDsrCaseReference("VENNANDUR|2099|9001|CONFLICT-deadbeef"),
        )
    }

    @Test
    fun `unrecognised case key remains inspectable`() {
        assertEquals("legacy-key", formatDsrCaseReference("legacy-key"))
    }
}
