package com.dailybeat.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InputPolicyTest {
    @Test
    fun singleLinePreservesUnicodeAndRemovesPastedControls() {
        assertEquals(
            "அலுவலர் 🙂 اسم",
            InputPolicy.singleLine("அலுவலர் 🙂\nاسم\u0000", 120),
        )
    }

    @Test
    fun boundedNeverSplitsAnEmojiSurrogatePair() {
        val bounded = InputPolicy.bounded("123🙂suffix", 4)

        assertEquals("123", bounded)
        assertFalse(bounded.last().isSurrogate())
    }

    @Test
    fun multilineKeepsNewlinesButDropsOtherControls() {
        assertEquals("one\ntwo", InputPolicy.multiline("one\r\ntwo\u0000", 20))
    }
}
