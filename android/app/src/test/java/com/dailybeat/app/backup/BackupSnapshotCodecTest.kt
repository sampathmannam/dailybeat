package com.dailybeat.app.backup

import com.dailybeat.app.data.model.DiaryEntry
import com.dailybeat.app.data.model.BeatReview
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.LocationBreadcrumb
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
    fun `current snapshot round trips all supported records and settings`() {
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
            places = listOf(
                Place(
                    id = 3,
                    name = "HQ",
                    latitude = 17.4,
                    longitude = 78.5,
                    radiusM = 125,
                    isPrivate = true,
                ),
            ),
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
                    reviewState = "needs_review",
                    hidden = true,
                    manuallyEdited = true,
                ),
            ),
            settings = BackupSettings(
                officerName = "Sampath",
                themePreference = "dark",
                gpsCaptureEnabled = true,
                cloudLlmEnabled = true,
                cloudProvider = "deepseek",
                cloudModel = "deepseek-chat",
                cloudBaseUrl = "",
                autoEveningReport = true,
                autoMiddayPulse = false,
                supervisorName = "Supervisor",
            ),
            breadcrumbs = listOf(
                LocationBreadcrumb(
                    id = 5,
                    timestampMs = 3_500L,
                    latitude = 17.41,
                    longitude = 78.51,
                    accuracyM = 18f,
                    quality = "good",
                ),
            ),
            beatReviews = listOf(
                BeatReview(
                    dateKey = "2026-08-31",
                    title = "Court rounds",
                    state = "complete",
                    completedAt = 4_500L,
                    updatedAt = 4_600L,
                ),
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
            BackupSnapshotCodec.decode("""{"schemaVersion":3,"createdAtMs":1,"events":[],"places":[],"diaries":[],"visits":[],"settings":{}}""")
        }

        assertEquals("Unsupported backup version: 3", error.message)
    }

    @Test
    fun `version one backup remains restorable with new collections empty`() {
        val decoded = BackupSnapshotCodec.decode(
            """{"schemaVersion":1,"createdAtMs":1,"events":[],"places":[],"diaries":[],"visits":[],"settings":{}}""",
        )

        assertEquals(BackupSnapshot.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(emptyList<Any>(), decoded.breadcrumbs)
        assertEquals(emptyList<Any>(), decoded.beatReviews)
    }

    @Test
    fun `malformed snapshot is rejected with safe message`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupSnapshotCodec.decode("not-json")
        }

        assertEquals("Backup is not valid JSON.", error.message)
    }

    @Test
    fun `invalid coordinates are rejected before restore`() {
        val invalid = BackupSnapshot.empty(createdAtMs = 123L).copy(
            places = listOf(
                Place(id = 1, name = "Impossible", latitude = 91.0, longitude = 0.0, radiusM = 100),
            ),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupSnapshotCodec.encode(invalid)
        }

        assertEquals("Backup contains an invalid latitude.", error.message)
    }

    @Test
    fun `invalid visit review state is rejected before restore`() {
        val invalid = BackupSnapshot.empty(createdAtMs = 123L).copy(
            visits = listOf(
                LocationVisit(
                    startMs = 1L,
                    endMs = 2L,
                    latitude = 17.4,
                    longitude = 78.5,
                    visitType = "dwell",
                    reviewState = "unknown",
                ),
            ),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupSnapshotCodec.encode(invalid)
        }

        assertEquals("Backup contains an invalid visit review state.", error.message)
    }

    @Test
    fun `invalid day review timestamps are rejected before restore`() {
        val invalid = BackupSnapshot.empty(createdAtMs = 123L).copy(
            beatReviews = listOf(
                BeatReview(
                    dateKey = "2026-09-10",
                    title = "Rounds",
                    state = "complete",
                    completedAt = -1L,
                    updatedAt = 1L,
                ),
            ),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupSnapshotCodec.encode(invalid)
        }

        assertEquals("Backup contains an invalid day review time.", error.message)
    }

    @Test
    fun `jsonByteSize counts utf-8 bytes, not utf-16 chars`() {
        // A Tamil letter is one UTF-16 char but three UTF-8 bytes. The server cap and the download
        // ceiling both count bytes, so the guard must too.
        val tamil = "த"
        assertEquals(1, tamil.length)
        assertEquals(3, BackupSnapshotCodec.jsonByteSize(tamil))
    }

    @Test
    fun `a snapshot under the char count but over the byte cap is rejected before upload`() {
        // 9 diaries of half a million Tamil characters: 4.5M chars — well under the old 10M-char
        // guard that used to gate uploads — but ~13.5M UTF-8 bytes, past the 12 MiB server cap.
        // Under the char-based guard this encoded silently and the server rejected it with a bare
        // 400; the byte-based guard now refuses it here with a message the officer can act on.
        val tamilBlock = "த".repeat(500_000)
        val diaries = (1..9).map { day ->
            DiaryEntry(dateKey = "2026-02-%02d".format(day), text = tamilBlock, updatedAt = day.toLong())
        }
        val snapshot = BackupSnapshot.empty(createdAtMs = 1L).copy(diaries = diaries)

        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupSnapshotCodec.encode(snapshot)
        }
        assertEquals("Backup is too large to upload safely.", error.message)
    }
}
