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
    private val axes: DoubleArray,
    private val names: Array<String>,
) {
    data class Reference(val name: String, val distanceKm: Double)

    fun nearest(latitude: Double, longitude: Double): Reference? {
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 ||
            longitude !in -180.0..180.0 || (latitude == 0.0 && longitude == 0.0)) return null
        val phi = Math.toRadians(latitude)
        val lam = Math.toRadians(longitude)
        val cosPhi = cos(phi)
        val queryX = cosPhi * cos(lam)
        val queryY = cosPhi * sin(lam)
        val queryZ = sin(phi)
        var best = -1
        var bestSquared = Double.POSITIVE_INFINITY
        // An explicit bounded stack avoids recursive closure/ref allocations for every
        // lookup. The balanced index has fewer than 18 levels at its maximum size.
        val scratch = searchScratch.get()!!
        val pendingLow = scratch.low
        val pendingHigh = scratch.high
        val pendingDepth = scratch.depth
        val pendingPlaneSquared = scratch.planeSquared
        var pending = 0
        var low = 0
        var high = names.size
        var depth = 0
        try {
            while (true) {
                if (low >= high) {
                    while (pending > 0 && pendingPlaneSquared[pending - 1] > bestSquared) pending--
                    if (pending == 0) break
                    pending--
                    low = pendingLow[pending]
                    high = pendingHigh[pending]
                    depth = pendingDepth[pending]
                    continue
                }
                val mid = (low + high) ushr 1
                val base = mid * 3
                val x = axes[base]
                val y = axes[base + 1]
                val z = axes[base + 2]
                val dx = queryX - x
                val dy = queryY - y
                val dz = queryZ - z
                val squared = dx * dx + dy * dy + dz * dz
                if (squared < bestSquared) {
                    bestSquared = squared
                    best = mid
                }
                val axis = depth % 3
                val delta = when (axis) { 0 -> dx; 1 -> dy; else -> dz }
                val planeSquared = delta * delta
                val farLow = if (delta < 0) mid + 1 else low
                val farHigh = if (delta < 0) high else mid
                // Chord distance preserves great-circle order, including poles and the date line.
                if (farLow < farHigh && planeSquared <= bestSquared) {
                    check(pending < pendingLow.size) { "Town index depth exceeded" }
                    pendingLow[pending] = farLow
                    pendingHigh[pending] = farHigh
                    pendingDepth[pending] = depth + 1
                    pendingPlaneSquared[pending] = planeSquared
                    pending++
                }
                if (delta < 0) high = mid else low = mid + 1
                depth++
            }
            return if (best < 0) null else Reference(
                names[best],
                12_742.0 * asin((sqrt(bestSquared) / 2).coerceIn(0.0, 1.0)),
            )
        } finally {
            // Reuse only workspace, never query-derived distances. Other stack values are
            // public index offsets. Each thread has its own workspace for concurrent callers.
            pendingPlaneSquared.fill(0.0)
        }
    }

    companion object {
        private class SearchScratch {
            val low = IntArray(32)
            val high = IntArray(32)
            val depth = IntArray(32)
            val planeSquared = DoubleArray(32)
        }
        private val searchScratch = ThreadLocal.withInitial { SearchScratch() }
        internal const val RESOURCE = "/offline-towns-v2.dat.gz"
        private val RESOURCE_SHA256 = byteArrayOf(
            0x61, 0xe8.toByte(), 0x26, 0x40, 0xf4.toByte(), 0x4c, 0x0c, 0xf8.toByte(),
            0x4b, 0xfd.toByte(), 0x13, 0xcf.toByte(), 0x1c, 0x74, 0xa2.toByte(), 0x11,
            0x34, 0xd6.toByte(), 0xc0.toByte(), 0xef.toByte(), 0x84.toByte(), 0x06, 0x6c,
            0xd3.toByte(), 0x7b, 0xd7.toByte(), 0x0c, 0x12, 0x9e.toByte(), 0x21, 0x39, 0x29,
        )

        // A single immutable index per process. Application startup warms it on Dispatchers.IO.
        // Synchronous callers also work before warmup; no transient label can become stuck in UI.
        val bundled: OfflineTownIndex? by lazy {
            try {
                OfflineTownIndex::class.java.getResourceAsStream(RESOURCE)?.use(::read)
            } catch (_: IOException) { null } catch (_: IllegalArgumentException) { null }
        }

        internal fun read(input: InputStream): OfflineTownIndex {
            // Authenticate the immutable published data, then bulk-load its contiguous
            // vectors. Decode immutable public names once so a first batch of lookups does
            // not allocate strings or initialize text decoding on the capture path.
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
                require(stream.int == 0x44425432) { "Unsupported town index" }
                val count = stream.int
                require(count in 1..100_000) { "Invalid town count" }
                require(data.size - stream.position() >= count * 3 * java.lang.Double.BYTES) {
                    "Truncated town vectors"
                }
                val axes = DoubleArray(count * 3)
                stream.slice().order(ByteOrder.BIG_ENDIAN).asDoubleBuffer().get(axes)
                val names = Array(count) { "" }
                var offset = stream.position() + count * 3 * java.lang.Double.BYTES
                repeat(count) { row ->
                    require(data.size - offset >= 5) { "Truncated town record" }
                    val nameLength = ((data[offset].toInt() and 0xff) shl 8) or
                        (data[offset + 1].toInt() and 0xff)
                    offset += 2
                    require(nameLength in 1..800 && data.size - offset >= nameLength + 4) {
                        "Invalid town name"
                    }
                    names[row] = String(data, offset, nameLength, Charsets.UTF_8)
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
                return OfflineTownIndex(axes, names)
            } catch (error: BufferUnderflowException) {
                throw IllegalArgumentException("Truncated town index", error)
            }
        }
    }
}
