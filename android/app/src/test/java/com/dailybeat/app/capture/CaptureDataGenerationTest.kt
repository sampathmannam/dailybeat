package com.dailybeat.app.capture

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CaptureDataGenerationTest {
    @Test fun replacementWhileWriterWaitsRejectsWriteAfterLockIsAcquired() = runBlocking {
        val generation = CaptureStorageGate.dataGeneration.get()
        var wrote = false
        CaptureStorageGate.mutex.lock()
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { CaptureStorageGate.writeIfCurrent(generation) { wrote = true } }
        }
        try {
            CaptureStorageGate.dataGeneration.incrementAndGet()
        } finally {
            CaptureStorageGate.mutex.unlock()
        }
        assertTrue(pending.await().isFailure)
        assertFalse(wrote)
    }

    @Test fun capturePauseDoesNotCancelUnrelatedDiaryWrite() = runBlocking {
        val generation = CaptureStorageGate.dataGeneration.get()
        CaptureStorageGate.generation.incrementAndGet()
        assertEquals("saved", CaptureStorageGate.writeIfCurrent(generation) { "saved" })
    }
}
