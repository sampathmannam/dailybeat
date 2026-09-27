package com.dailybeat.app.domain

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream
import kotlin.math.*

/** Public, immutable town references only. Never stores queries, sends requests or asserts boundaries. */
internal class OfflineTownIndex private constructor(
    private val axes: Array<DoubleArray>,
    private val data: ByteArray,
    private val nameOffsets: IntArray,
    private val nameLengths: IntArray,
) {
    data class Reference(val name: String, val distanceKm: Double)

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
            var squared = 0.0
            for (axis in 0..2) squared += (query[axis] - axes[axis][mid]).let { it * it }
            if (squared < bestSquared) {
                bestSquared = squared
                best = mid
            }
            val axis = depth % 3
            val delta = query[axis] - axes[axis][mid]
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

        // A single immutable index per process. Application startup warms it on Dispatchers.IO.
        // Synchronous callers also work before warmup; no transient label can become stuck in UI.
        val bundled: OfflineTownIndex? by lazy {
            try {
                OfflineTownIndex::class.java.getResourceAsStream(RESOURCE)?.use(::read)
            } catch (_: IOException) { null } catch (_: IllegalArgumentException) { null }
        }

        internal fun read(input: InputStream): OfflineTownIndex {
            // One bounded inflate and one array-backed parse avoids allocating 63,000
            // short strings on startup. Names decode only for actual nearest results.
            val output = ByteArrayOutputStream(1_500_000)
            GZIPInputStream(input.buffered(64 * 1024), 64 * 1024).use { gzip ->
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
            val data = output.toByteArray()
            val stream = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
            try {
                require(stream.int == 0x44425431) { "Unsupported town index" }
                val count = stream.int
                require(count in 1..100_000) { "Invalid town count" }
                val axes = Array(3) { DoubleArray(count) }
                val nameOffsets = IntArray(count)
                val nameLengths = IntArray(count)
                repeat(count) { row ->
                    for (axis in 0..2) {
                        axes[axis][row] = stream.double.also { require(it.isFinite() && it in -1.0..1.0) }
                    }
                    val x = axes[0][row]
                    val y = axes[1][row]
                    val z = axes[2][row]
                    require(abs(x * x + y * y + z * z - 1) < 1e-10)
                    val nameLength = stream.short.toInt() and 0xffff
                    require(nameLength in 1..800 && stream.remaining() >= nameLength) { "Invalid town name" }
                    nameOffsets[row] = stream.position()
                    nameLengths[row] = nameLength
                    stream.position(stream.position() + nameLength)
                    val countryLength = stream.short.toInt() and 0xffff
                    require(countryLength == 2 && stream.remaining() >= countryLength) { "Invalid country" }
                    repeat(countryLength) { require(stream.get().toInt() in 'A'.code..'Z'.code) }
                }
                require(!stream.hasRemaining()) { "Trailing town data" }
                return OfflineTownIndex(axes, data, nameOffsets, nameLengths)
            } catch (error: BufferUnderflowException) {
                throw IllegalArgumentException("Truncated town index", error)
            }
        }
    }
}
