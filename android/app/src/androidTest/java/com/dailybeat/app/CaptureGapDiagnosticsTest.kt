package com.dailybeat.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailybeat.app.audit.CaptureGapDiagnostics
import com.dailybeat.app.capture.CaptureStorageGate
import com.dailybeat.app.capture.LocationService
import com.dailybeat.app.data.model.LocationBreadcrumb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class CaptureGapDiagnosticsTest {
    @Test fun storedHistoryAuditIsReadOnlyAndExcludesCoordinatesAndDiaryContent() = runBlocking(Dispatchers.IO) {
        requireDisposableTestApp()
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        app.settingsRepository.setGpsEnabled(false)
        LocationService.stop(app)
        val day = LocalDate.now().minusDays(2)
        val start = day.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        CaptureStorageGate.mutex.withLock {
            app.db.breadcrumbs().deleteAll()
            app.db.breadcrumbs().insertAll(listOf(
                LocationBreadcrumb(timestampMs = start, latitude = 12.345678, longitude = 76.543219,
                    accuracyM = 10f, quality = "good"),
                LocationBreadcrumb(timestampMs = start + 30 * 60_000L, latitude = 12.346789,
                    longitude = 76.544321, accuracyM = 100f, quality = "approximate"),
            ))
            app.diaryRepository.saveForDate(day, "Private synthetic diary must never enter capture diagnostics")
        }
        val before = app.db.breadcrumbs().all()
        val diaryBefore = app.db.diaries().all()
        val report = CaptureGapDiagnostics.build(app)
        val json = JSONObject(report)
        assertTrue(json.getBoolean("containsPersonalRecords"))
        assertFalse(json.getBoolean("containsCoordinates"))
        assertFalse(json.getBoolean("containsDiaryContent"))
        assertFalse(report.contains("12.345678"))
        assertFalse(report.contains("76.543219"))
        assertFalse(report.contains("Private synthetic diary"))
        assertEquals(2, json.getInt("storedPointCount"))
        val recordedDay = json.getJSONArray("days").getJSONObject(0)
        assertEquals(day.toString(), recordedDay.getString("date"))
        assertEquals(1, recordedDay.getInt("gapCount"))
        assertEquals(30.0, recordedDay.getDouble("gapMinutes"), 0.0)
        assertEquals(1, recordedDay.getInt("approximatePointCount"))
        assertEquals(before, app.db.breadcrumbs().all())
        assertEquals(diaryBefore, app.db.diaries().all())
    }
}
