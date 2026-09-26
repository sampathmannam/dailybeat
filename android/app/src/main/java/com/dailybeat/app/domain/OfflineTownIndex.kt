package com.dailybeat.app.domain

import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlin.math.*

/** Public, immutable town references only. Never stores queries, sends requests or asserts boundaries. */
internal class OfflineTownIndex private constructor(
    private val axes: Array<DoubleArray>,
    private val names: Array<String>,
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
        search(0, names.size, 0)
        return if (best < 0) null else Reference(names[best], 12_742.0 * asin((sqrt(bestSquared) / 2).coerceIn(0.0, 1.0)))
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

        internal fun read(input: InputStream): OfflineTownIndex =
            // A larger compressed-input and decoded-output buffer avoids thousands of
            // tiny inflater/resource reads during the first load on slower devices.
            DataInputStream(GZIPInputStream(input.buffered(64 * 1024), 64 * 1024).buffered(64 * 1024)).use { stream ->
                require(stream.readInt() == 0x44425431) { "Unsupported town index" }
                val count = stream.readInt()
                require(count in 1..100_000) { "Invalid town count" }
                val axes = Array(3) { DoubleArray(count) }
                val names = Array(count) { "" }
                fun text(): String {
                    val length = stream.readUnsignedShort()
                    require(length in 1..800) { "Invalid town text length" }
                    return ByteArray(length).also(stream::readFully).toString(Charsets.UTF_8)
                }
                repeat(count) { row ->
                    for (axis in 0..2) {
                        axes[axis][row] = stream.readDouble().also { require(it.isFinite() && it in -1.0..1.0) }
                    }
                    require(abs(axes.sumOf { it[row] * it[row] } - 1) < 1e-10)
                    names[row] = text()
                    val country = text() // Source country retained in the resource.
                    require(country.length == 2 && country.all { it in 'A'..'Z' })
                }
                require(stream.read() == -1) { "Trailing town data" }
                OfflineTownIndex(axes, names)
            }
    }
}
