package com.dailybeat.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailybeat.app.domain.OfflineTownIndex
import com.dailybeat.app.domain.VisitLabels
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic public points only. Absolute responsiveness guard, not a battery benchmark. */
@RunWith(AndroidJUnit4::class)
class OfflineAreaIndexTest {
    @Test fun bundledIndexLoadsAndResolvesOfflineOnThePhone() {
        requireDisposableTestApp()
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        val loadMs = mutableListOf<Double>()
        val lookupMs = mutableListOf<Double>()
        repeat(5) {
            val start = System.nanoTime()
            val index = requireNotNull(javaClass.getResourceAsStream(OfflineTownIndex.RESOURCE)).use(OfflineTownIndex::read)
            loadMs += (System.nanoTime() - start) / 1_000_000.0
            val queryStart = System.nanoTime()
            repeat(1000) { i ->
                assertNotNull(index.nearest(11.0 + (i % 50) * 0.01, 78.0 + (i % 70) * 0.01))
            }
            lookupMs += (System.nanoTime() - queryStart) / 1_000_000.0
        }
        val directory = File(app.getExternalFilesDir(null), "offline-area-evidence").apply { mkdirs() }
        File(directory, "timings.txt").writeText("load_ms=$loadMs\n1000_lookups_ms=$lookupMs\n")
        assertTrue("Index load below 1 s: $loadMs", loadMs.all { it < 1000 })
        assertTrue("1000 lookups below 150 ms: $lookupMs", lookupMs.all { it < 150 })
        assertEquals("Approx. area · Near Rasipuram", VisitLabels.approximateLocation(11.4557, 78.1856))
        assertEquals("Approx. area · Near Namakkal", VisitLabels.approximateLocation(11.22126, 78.16524))
    }
}
