package com.dailybeat.app.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedDiaryBlockTest {

    @Test
    fun `replacement preserves officer text on both sides`() {
        val existing = "Before\n\n<start>old<end>\n\nAfter"

        val merged = GeneratedDiaryBlock.merge(existing, "<start>", "<end>", "<start>new<end>")

        assertEquals("Before\n\n<start>new<end>\n\nAfter", merged)
    }

    @Test
    fun `unbounded legacy section is preserved rather than guessed`() {
        val existing = "Officer text\n<start>legacy text that may contain edits"

        val merged = GeneratedDiaryBlock.merge(existing, "<start>", "<end>", "<start>new<end>")

        assertTrue(merged.contains("legacy text that may contain edits"))
        assertTrue(merged.endsWith("<start>new<end>"))
    }
}
