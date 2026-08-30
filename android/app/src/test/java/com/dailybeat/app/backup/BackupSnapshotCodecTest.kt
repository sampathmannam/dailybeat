package com.dailybeat.app.backup

import com.dailybeat.app.data.model.DiaryEntry
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupSnapshotCodecTest {

    @Test
    fun `version one snapshot round trips all supported records and settings`() {
        val snapshot = BackupSnapshot(
            createdAtMs = 1_777_777L,
            events = listOf(
                Event(
                    id = 9,
                    timestamp = 1_111L,
                    type = "manual",
                    rawText = "Met the team",
                    placeName = "HQ",
                    latitude = 17.4,
                    longitude = 78.5,
                    peopleMentioned = "Alex",
                    caseNumbers = "C-7",
                    sourceId = "note-1",
                ),
            ),
            places = listOf(Place(id = 3, name = "HQ", latitude = 17.4, longitude = 78.5, radiusM = 125)),
            diaries = listOf(DiaryEntry(dateKey = "2026-08-31", text = "A useful day", updatedAt = 2_222L)),
            visits = listOf(
                LocationVisit(
                    id = 4,
                    startMs = 3_000L,
                    endMs = 4_000L,
                    latitude = 17.4,
                    longitude = 78.5,
                    placeName = "HQ",
                    address = "Main Road",
                    visitType = "dwell",
                ),
            ),
            settings = BackupSettings(
                officerName = "Sampath",
                gpsCaptureEnabled = true,
                callLogEnabled = false,
                cloudLlmEnabled = true,
                cloudProvider = "deepseek",
                cloudModel = "deepseek-chat",
                cloudBaseUrl = "",
                autoEveningReport = true,
                autoMiddayPulse = false,
                supervisorName = "Supervisor",
            ),
        )

        val encoded = BackupSnapshotCodec.encode(snapshot)

        assertEquals(snapshot, BackupSnapshotCodec.decode(encoded))
    }

    @Test
    fun `snapshot format cannot contain an api key`() {
        val snapshot = BackupSnapshot.empty(createdAtMs = 123L)

        val encoded = BackupSnapshotCodec.encode(snapshot)

        assertFalse(encoded.contains("apiKey", ignoreCase = true))
        assertFalse(encoded.contains("deepseek-secret"))
    }

    @Test
    fun `future snapshot version is rejected`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupSnapshotCodec.decode("""{"schemaVersion":2,"createdAtMs":1,"events":[],"places":[],"diaries":[],"visits":[],"settings":{}}""")
        }

        assertEquals("Unsupported backup version: 2", error.message)
    }

    @Test
    fun `malformed snapshot is rejected with safe message`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupSnapshotCodec.decode("not-json")
        }

        assertEquals("Backup is not valid JSON.", error.message)
    }
}
