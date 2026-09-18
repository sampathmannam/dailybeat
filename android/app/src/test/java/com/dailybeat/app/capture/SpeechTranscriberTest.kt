package com.dailybeat.app.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class SpeechTranscriberTest {

    @Test
    fun `voice recognition follows the phone language`() {
        assertEquals("ta-IN", recognitionLanguageTag(Locale.forLanguageTag("ta-IN")))
        assertEquals("en-GB", recognitionLanguageTag(Locale.UK))
    }

    @Test
    fun `undefined locale lets Android choose its recognition default`() {
        assertNull(recognitionLanguageTag(Locale.ROOT))
    }
}
