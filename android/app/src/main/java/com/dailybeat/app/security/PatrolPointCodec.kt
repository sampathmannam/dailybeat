package com.dailybeat.app.security

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PatrolCoordinates(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float,
)

/**
 * Stable binary representation for a patrol location before authenticated encryption.
 */
object PatrolPointCodec {
    private const val PAYLOAD_SIZE_BYTES = Double.SIZE_BYTES * 2 + Float.SIZE_BYTES

    fun encode(coordinates: PatrolCoordinates): ByteArray {
        require(coordinates.latitude.isFinite() && coordinates.latitude in -90.0..90.0) {
            "Latitude is outside the valid range"
        }
        require(coordinates.longitude.isFinite() && coordinates.longitude in -180.0..180.0) {
            "Longitude is outside the valid range"
        }
        require(coordinates.accuracyM.isFinite() && coordinates.accuracyM >= 0f) {
            "Accuracy must be a finite, non-negative value"
        }
        return ByteBuffer.allocate(PAYLOAD_SIZE_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .putDouble(coordinates.latitude)
            .putDouble(coordinates.longitude)
            .putFloat(coordinates.accuracyM)
            .array()
    }

    fun decode(payload: ByteArray): PatrolCoordinates {
        require(payload.size == PAYLOAD_SIZE_BYTES) { "Unexpected patrol point payload size" }
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        return PatrolCoordinates(
            latitude = buffer.double,
            longitude = buffer.double,
            accuracyM = buffer.float,
        ).also(::validateDecoded)
    }

    private fun validateDecoded(coordinates: PatrolCoordinates) {
        require(coordinates.latitude.isFinite() && coordinates.latitude in -90.0..90.0)
        require(coordinates.longitude.isFinite() && coordinates.longitude in -180.0..180.0)
        require(coordinates.accuracyM.isFinite() && coordinates.accuracyM >= 0f)
    }
}
