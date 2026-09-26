package com.dailybeat.app.domain

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Random
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class OfflineTownIndexTest {
    private fun resource() = requireNotNull(javaClass.getResourceAsStream(OfflineTownIndex.RESOURCE))

    @Test fun `bundled public data matches the reviewed extract`() {
        val sha = resource().use { MessageDigest.getInstance("SHA-256").digest(it.readBytes()) }
        assertEquals("3a36460fd221f2254fd533cf8ef2686f55a386e401abd82da29bd89f32f1f49c", sha.joinToString("") { "%02x".format(it) })
        assertNotNull(OfflineTownIndex.bundled)
    }

    @Test fun `indexed nearest matches independent exhaustive great circle search worldwide`() {
        val points = DataInputStream(GZIPInputStream(resource())).use { stream ->
            assertEquals(0x44425431, stream.readInt())
            val count = stream.readInt()
            assertEquals(31638, count)
            List(count) {
                val x = stream.readDouble(); val y = stream.readDouble(); val z = stream.readDouble()
                val name = ByteArray(stream.readUnsignedShort()).also(stream::readFully).toString(Charsets.UTF_8)
                stream.skipBytes(stream.readUnsignedShort())
                Triple(asin(z), atan2(y, x), name)
            }
        }
        val random = Random(20260926)
        val queries = listOf(90.0 to 180.0, -90.0 to -180.0, 0.0 to 180.0,
            0.0 to -180.0, 11.4557 to 78.1856, -33.8688 to -70.6693) +
            List(200) { (random.nextDouble() * 180 - 90) to (random.nextDouble() * 360 - 180) }
        for ((lat, lon) in queries) {
            val phi = Math.toRadians(lat); val lam = Math.toRadians(lon)
            val expected = points.minOf { (p, l, _) ->
                val a = sin((phi - p) / 2).pow(2) + cos(phi) * cos(p) * sin((lam - l) / 2).pow(2)
                12742.0 * asin(sqrt(a.coerceIn(0.0, 1.0)))
            }
            val result = requireNotNull(OfflineTownIndex.bundled?.nearest(lat, lon))
            assertEquals("Nearest at $lat,$lon", expected, result.distanceKm, 0.00001)
            assertTrue(result.name.isNotBlank())
        }
    }

    @Test fun `invalid query does not invent a town`() {
        val index = requireNotNull(OfflineTownIndex.bundled)
        listOf(0.0 to 0.0, 91.0 to 1.0, 1.0 to -181.0, Double.NaN to 1.0,
            1.0 to Double.NEGATIVE_INFINITY).forEach { (lat, lon) -> assertNull(index.nearest(lat, lon)) }
    }

    @Test fun `malformed or oversized bundled index is rejected before allocation`() {
        for ((magic, count) in listOf(0 to 1, 0x44425431 to -1, 0x44425431 to Int.MAX_VALUE)) {
            val bytes = ByteArrayOutputStream().also { output ->
                DataOutputStream(GZIPOutputStream(output)).use { it.writeInt(magic); it.writeInt(count) }
            }.toByteArray()
            assertThrows(IllegalArgumentException::class.java) { OfflineTownIndex.read(ByteArrayInputStream(bytes)) }
        }
        assertThrows(java.io.IOException::class.java) { OfflineTownIndex.read(ByteArrayInputStream(byteArrayOf(1, 2, 3))) }
        val oversized = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(ByteArray(5_000_001)) }
        }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            OfflineTownIndex.read(ByteArrayInputStream(oversized))
        }
    }
}
