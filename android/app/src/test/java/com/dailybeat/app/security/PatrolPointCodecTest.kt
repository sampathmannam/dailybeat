package com.dailybeat.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PatrolPointCodecTest {

    @Test
    fun roundTripPreservesPatrolCoordinates() {
        val original = PatrolCoordinates(
            latitude = 12.9715987,
            longitude = 77.594566,
            accuracyM = 4.75f,
        )

        assertEquals(original, PatrolPointCodec.decode(PatrolPointCodec.encode(original)))
    }

    @Test
    fun invalidCoordinatesAreRejectedBeforeEncryption() {
        assertThrows(IllegalArgumentException::class.java) {
            PatrolPointCodec.encode(PatrolCoordinates(91.0, 77.0, 3f))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PatrolPointCodec.encode(PatrolCoordinates(12.0, 181.0, 3f))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PatrolPointCodec.encode(PatrolCoordinates(12.0, 77.0, -1f))
        }
    }

    @Test
    fun malformedPayloadIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PatrolPointCodec.decode(ByteArray(8))
        }
    }
}
