package com.dailybeat.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandAssetsTest {
    private fun resource(path: String): String =
        File("src/main/res/$path").readText()

    private fun source(path: String): String =
        File("src/main/java/com/dailybeat/app/$path").readText()

    private fun manifest(): String = File("src/main/AndroidManifest.xml").readText()

    private fun visibleStrings(): String =
        Regex("<string\\b[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .findAll(resource("values/strings.xml"))
            .joinToString("\n") { it.groupValues[1] }
            .lowercase()

    @Test
    fun cloudOnlyCopyDoesNotAdvertiseOfflineOrLocalModels() {
        val strings = visibleStrings()

        assertFalse("fully offline" in strings)
        assertFalse("local gguf" in strings)
        assertFalse("local model" in strings)
    }

    @Test
    fun userFacingDiaryCopyDoesNotContainDairyTypo() {
        val strings = visibleStrings()

        assertFalse("dairy" in strings)
    }

    @Test
    fun launcherUsesPatrolGridBrand() {
        val colors = resource("values/colors.xml")
        val foreground = resource("drawable/ic_launcher_foreground.xml")

        assertTrue("#0F172A" in colors)
        assertTrue("#1E3A5F" in foreground)
        assertTrue("#E8A317" in foreground)
        assertTrue("#16A34A" in foreground)
        assertTrue(Regex("<path\\b").findAll(foreground).count() >= 5)
        assertFalse("M54,30 L70,54 L54,78 L38,54 Z" in foreground)
    }

    @Test
    fun adaptiveLaunchersSupportAndroidThemedIcons() {
        val launcher = resource("mipmap-anydpi/ic_launcher.xml")
        val roundLauncher = resource("mipmap-anydpi/ic_launcher_round.xml")
        val monochrome = resource("drawable/ic_launcher_monochrome.xml")

        assertTrue("<monochrome android:drawable=\"@drawable/ic_launcher_monochrome\"" in launcher)
        assertTrue("<monochrome android:drawable=\"@drawable/ic_launcher_monochrome\"" in roundLauncher)
        assertTrue(Regex("<path\\b").findAll(monochrome).count() >= 2)
    }

    @Test
    fun sensitiveUserDataIsExcludedFromAndroidBackupAndTransfer() {
        val manifest = manifest()

        assertTrue("android:allowBackup=\"false\"" in manifest)
        assertTrue("android:fullBackupContent=\"false\"" in manifest)
        assertTrue("android:dataExtractionRules=\"@xml/data_extraction_rules\"" in manifest)
        assertTrue("<cloud-backup>" in resource("xml/data_extraction_rules.xml"))
        assertTrue("<device-transfer>" in resource("xml/data_extraction_rules.xml"))
    }

    @Test
    fun apiKeyStoreNeverFallsBackToPlaintextPreferences() {
        val keyStore = source("data/settings/SecureApiKeyStore.kt")

        assertFalse("FALLBACK_FILE" in keyStore)
        assertFalse("context.getSharedPreferences" in keyStore)
    }

    /**
     * The legacy DailyBeat capture-and-upload stack was removed because it shipped
     * third-party network egress and external-storage writes into a police build that
     * never reached any of it. Nothing here is allowed to come back by accident: each
     * of these either talks to a third party or writes patrol data somewhere
     * `allowBackup=false` does not cover.
     */
    @Test
    fun legacyCaptureAndEgressStackStaysDeleted() {
        val removed = listOf(
            "capture/VoiceCaptureOrchestrator.kt",
            "capture/SpeechTranscriber.kt",
            "capture/WhisperBridge.kt",
            "capture/VoiceRecorder.kt",
            "capture/CallLogWorker.kt",
            "capture/VisitTracker.kt",
            "geo/OsmGeocoder.kt",
            "cloud/CloudLlmClient.kt",
            "audit/CaptureAuditLog.kt",
            "export/PdfExporter.kt",
            "export/PackageExporter.kt",
        )
        val resurrected = removed.filter {
            File("src/main/java/com/dailybeat/app/$it").exists()
        }
        assertTrue(
            "Legacy capture/egress files are back: $resurrected",
            resurrected.isEmpty(),
        )
    }

    /**
     * The files being gone is not enough. Microphone and call-log capture come back
     * the moment a permission is declared and something checks it, and with the
     * helpers already present that diff looks smaller than the capability change is.
     */
    @Test
    fun microphoneAndCallLogCaptureCannotBeReintroducedQuietly() {
        val forbidden = listOf("RECORD_AUDIO", "READ_CALL_LOG")
        val offenders = mutableListOf<String>()
        forbidden.forEach { permission ->
            if (permission in manifest()) offenders += "AndroidManifest.xml: $permission"
        }
        File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                forbidden.filter { it in text }.forEach { offenders += "${file.name}: $it" }
            }
        assertTrue("Audio/call-log capture is being reintroduced: $offenders", offenders.isEmpty())
    }

    /**
     * PatrolGrid may contact its own Supabase backend and the pinned OpenFreeMap tile
     * host, and nothing else. A police build must not acquire a new outbound host
     * without that being a deliberate, reviewed change.
     */
    @Test
    fun sourceContainsNoUnapprovedOutboundHosts() {
        val allowed = setOf(
            "tiles.openfreemap.org",
            "fonts.googleapis.com",
            "schemas.android.com",
            "www.w3.org",
            "maven.apache.org",
        )
        val offenders = mutableListOf<String>()
        File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                Regex("""https://([a-zA-Z0-9.-]+)""").findAll(file.readText())
                    .map { it.groupValues[1] }
                    .filter { host -> host !in allowed && !host.endsWith(".supabase.co") }
                    .forEach { host -> offenders += "${file.name}: $host" }
            }
        assertTrue("Unapproved outbound hosts in source: $offenders", offenders.isEmpty())
    }

    /** External storage is the one location `allowBackup=false` does not protect. */
    @Test
    fun noSourceWritesPatrolDataToExternalStorage() {
        val offenders = File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { "getExternalFilesDir" in it.readText() || "getExternalStorageDirectory" in it.readText() }
            .map { it.name }
            .toList()
        assertTrue("Source writes to external storage: $offenders", offenders.isEmpty())
    }
}
