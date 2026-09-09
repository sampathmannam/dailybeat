package com.dailybeat.app.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A phone running a different DailyBeat release must still be able to restore a backup: an
 * added or retired preference is not a reason to lose the officer's records.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupCompatibilityTest {

    @Test
    fun `a backup written before 3_6_0 still restores`() {
        val legacy = """
            {"schemaVersion":1,"createdAtMs":42,"events":[],"places":[],"diaries":[],"visits":[],
             "settings":{"officerName":"Rao","gpsCaptureEnabled":true,"callLogEnabled":true,
             "cloudLlmEnabled":true,"cloudProvider":"deepseek","cloudModel":"deepseek-chat",
             "cloudBaseUrl":"","autoEveningReport":true,"autoMiddayPulse":false,"supervisorName":"SP"}}
        """.trimIndent()

        val snapshot = BackupSnapshotCodec.decode(legacy)

        assertEquals("Rao", snapshot.settings.officerName)
        assertEquals("SP", snapshot.settings.supervisorName)
    }

    @Test
    fun `a backup missing a preference falls back to its default`() {
        val sparse = """
            {"schemaVersion":1,"createdAtMs":42,"events":[],"places":[],"diaries":[],"visits":[],
             "settings":{"officerName":"Rao"}}
        """.trimIndent()

        val settings = BackupSnapshotCodec.decode(sparse).settings

        assertEquals("Rao", settings.officerName)
        assertEquals(BackupSettings().cloudProvider, settings.cloudProvider)
        assertEquals(BackupSettings().autoEveningReport, settings.autoEveningReport)
    }

    @Test
    fun `backups still carry the retired call-log key for older readers`() {
        val encoded = BackupSnapshotCodec.encode(BackupSnapshot.empty(createdAtMs = 1))

        assertTrue(
            "Releases before 3.6.0 read this key strictly and would reject the backup without it.",
            encoded.contains("\"callLogEnabled\""),
        )
    }

    @Test
    fun `a newer backup version is still refused with a clear message`() {
        val error = runCatching {
            BackupSnapshotCodec.decode(
                """{"schemaVersion":99,"createdAtMs":1,"events":[],"places":[],"diaries":[],"visits":[],"settings":{}}""",
            )
        }.exceptionOrNull()

        assertEquals("Unsupported backup version: 99", error?.message)
    }
}
