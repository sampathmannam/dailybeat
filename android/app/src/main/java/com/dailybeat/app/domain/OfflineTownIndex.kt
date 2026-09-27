package com.dailybeat.app.domain

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import kotlin.math.*

/** Public, immutable town references only. Never stores queries, sends requests or asserts boundaries. */
internal class OfflineTownIndex private constructor(
    private val data: ByteArray,
    private val vectorOffsets: IntArray,
    private val nameOffsets: IntArray,
    private val nameLengths: IntArray,
) {
    data class Reference(val name: String, val distanceKm: Double)
    private val vectors = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN).asReadOnlyBuffer()

    fun nearest(latitude: Double, longitude: Double): Reference? {
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 ||
            longitude !in -180.0..180.0 || (latitude == 0.0 && longitude == 0.0)) return null
        val phi = Math.toRadians(latitude)
        val lam = Math.toRadians(longitude)
        val query = doubleArrayOf(cos(phi) * cos(lam), cos(phi) * sin(lam), sin(phi))
        var best = -1
        var bestSquared = Double.POSITIVE_INFINITY
        fun search(low: Int, high: Int, depth: Int) {
            if (low >= high) return
            val mid = (low + high) ushr 1
            val base = vectorOffsets[mid]
            val x = vectors.getDouble(base)
            val y = vectors.getDouble(base + java.lang.Double.BYTES)
            val z = vectors.getDouble(base + 2 * java.lang.Double.BYTES)
            val dx = query[0] - x
            val dy = query[1] - y
            val dz = query[2] - z
            val squared = dx * dx + dy * dy + dz * dz
            if (squared < bestSquared) {
                bestSquared = squared
                best = mid
            }
            val axis = depth % 3
            val delta = when (axis) { 0 -> dx; 1 -> dy; else -> dz }
            if (delta < 0) search(low, mid, depth + 1) else search(mid + 1, high, depth + 1)
            // Chord distance preserves great-circle order, including poles and the date line.
            if (delta * delta <= bestSquared) {
                if (delta < 0) search(mid + 1, high, depth + 1) else search(low, mid, depth + 1)
            }
        }
        search(0, nameOffsets.size, 0)
        return if (best < 0) null else Reference(
            String(data, nameOffsets[best], nameLengths[best], Charsets.UTF_8),
            12_742.0 * asin((sqrt(bestSquared) / 2).coerceIn(0.0, 1.0)),
        )
    }

    companion object {
        internal const val RESOURCE = "/offline-towns-v1.dat.gz"
        private val RESOURCE_SHA256 = byteArrayOf(
            0x3a, 0x36, 0x46, 0x0f, 0xd2.toByte(), 0x21, 0xf2.toByte(), 0x25,
            0x4f, 0xd5.toByte(), 0x33, 0xcf.toByte(), 0x8e.toByte(), 0xf2.toByte(), 0x68, 0x6f,
            0x55, 0xa3.toByte(), 0x86.toByte(), 0xe4.toByte(), 0x01, 0xab.toByte(), 0xd8.toByte(), 0x2d,
            0xa2.toByte(), 0x9b.toByte(), 0xd8.toByte(), 0x9f.toByte(), 0x32, 0xf1.toByte(), 0xf4.toByte(), 0x9c.toByte(),
        )

        // A single immutable index per process. Application startup warms it on Dispatchers.IO.
        // Synchronous callers also work before warmup; no transient label can become stuck in UI.
        val bundled: OfflineTownIndex? by lazy {
            try {
                OfflineTownIndex::class.java.getResourceAsStream(RESOURCE)?.use(::read)
            } catch (_: IOException) { null } catch (_: IllegalArgumentException) { null }
        }

        internal fun read(input: InputStream): OfflineTownIndex {
            // Authenticate the immutable published data, then scan only record boundaries.
            // Vectors and names decode on demand instead of delaying the first label.
            val digest = MessageDigest.getInstance("SHA-256")
            val output = ByteArrayOutputStream(1_500_000)
            GZIPInputStream(DigestInputStream(input.buffered(64 * 1024), digest), 64 * 1024).use { gzip ->
                val chunk = ByteArray(64 * 1024)
                var size = 0
                while (true) {
                    val length = gzip.read(chunk)
                    if (length < 0) break
                    size += length
                    require(size <= 5_000_000) { "Oversized town index" }
                    output.write(chunk, 0, length)
                }
            }
            require(MessageDigest.isEqual(digest.digest(), RESOURCE_SHA256)) { "Unapproved town index" }
            val data = output.toByteArray()
            val stream = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
            try {
                require(stream.int == 0x44425431) { "Unsupported town index" }
                val count = stream.int
                require(count in 1..100_000) { "Invalid town count" }
                val vectorOffsets = IntArray(count)
                val nameOffsets = IntArray(count)
                val nameLengths = IntArray(count)
                var offset = stream.position()
                repeat(count) { row ->
                    require(data.size - offset >= 31) { "Truncated town record" }
                    vectorOffsets[row] = offset
                    offset += 3 * java.lang.Double.BYTES
                    val nameLength = ((data[offset].toInt() and 0xff) shl 8) or
                        (data[offset + 1].toInt() and 0xff)
                    offset += 2
                    require(nameLength in 1..800 && data.size - offset >= nameLength + 4) {
                        "Invalid town name"
                    }
                    nameOffsets[row] = offset
                    nameLengths[row] = nameLength
                    offset += nameLength
                    val countryLength = ((data[offset].toInt() and 0xff) shl 8) or
                        (data[offset + 1].toInt() and 0xff)
                    offset += 2
                    require(countryLength == 2) { "Invalid country" }
                    require((data[offset].toInt() and 0xff) in 'A'.code..'Z'.code)
                    require((data[offset + 1].toInt() and 0xff) in 'A'.code..'Z'.code)
                    offset += countryLength
                }
                require(offset == data.size) { "Trailing town data" }
                return OfflineTownIndex(data, vectorOffsets, nameOffsets, nameLengths)
            } catch (error: BufferUnderflowException) {
                throw IllegalArgumentException("Truncated town index", error)
            }
        }
    }
}
