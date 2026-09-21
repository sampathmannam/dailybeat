package com.dailybeat.app.audit

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BoundedLogLinesTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun oversizedCorruptLineDoesNotDisplaceCompleteRecentEntries() {
        val log = temporaryFolder.newFile().apply {
            writeText("x".repeat(2_000_000) + "\nfirst recent\nsecond recent\n")
        }
        assertEquals(listOf("first recent", "second recent"), newestLogLines(log, 80, 1024))
    }

    @Test fun oversizedIncompleteLineIsDiscarded() {
        val log = temporaryFolder.newFile().apply { writeText("x".repeat(2_000_000)) }
        assertEquals(emptyList<String>(), newestLogLines(log, 80, 1024))
    }

    @Test fun onlyRequestedRecentLinesAreReturned() {
        val log = temporaryFolder.newFile().apply { writeText("one\ntwo\nthree\n") }
        assertEquals(listOf("two", "three"), newestLogLines(log, 2, 1024))
        assertEquals(emptyList<String>(), newestLogLines(log, 0, 1024))
    }
}
