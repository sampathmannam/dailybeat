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
        assertFalse("(offline)" in source("export/PdfExporter.kt").lowercase())
    }

    @Test
    fun userFacingDiaryCopyDoesNotContainDairyTypo() {
        val strings = visibleStrings()
        val prompt = source("llm/DairyPrompt.kt").lowercase()
        val diaryScreen = source("ui/diary/DiaryScreen.kt").lowercase()

        assertFalse("dairy" in strings)
        assertFalse("formal dairy" in prompt)
        assertFalse("ips dairy" in prompt)
        assertFalse("share dairy pdf" in diaryScreen)
    }

    @Test
    fun launcherUsesSelectedJourneyLedgerBrand() {
        val colors = resource("values/colors.xml")
        val foreground = resource("drawable/ic_launcher_foreground.xml")

        assertTrue("#0B2D5B" in colors)
        assertTrue("#F8FAFC" in foreground)
        assertTrue("#FFD60A" in foreground)
        assertTrue("#0B2D5B" in foreground)
        assertTrue("M31,19" in foreground) // daily record page
        assertTrue("M37,72 C41,66" in foreground) // mapped route
        assertTrue("M62,27 C53.7" in foreground) // destination pin
        assertTrue(Regex("<path\\b").findAll(foreground).count() >= 5)
    }

    @Test
    fun adaptiveLaunchersSupportAndroidThemedIcons() {
        val launcher = resource("mipmap-anydpi-v26/ic_launcher.xml")
        val roundLauncher = resource("mipmap-anydpi-v26/ic_launcher_round.xml")
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

    @Test
    fun officerNameIsReadableWhileApiKeyIsMasked() {
        val settings = source("ui/settings/SettingsScreen.kt")
        val officerField = settings.substringAfter("value = state.officerName")
            .substringBefore("value = state.supervisorName")
        val apiKeyField = settings.substringAfter("value = state.apiKeyDraft")
            .substringBefore("if (state.hasApiKey)")

        assertFalse("PasswordVisualTransformation" in officerField)
        assertTrue("PasswordVisualTransformation" in apiKeyField)
        assertTrue("KeyboardType.Password" in apiKeyField)
    }

    @Test
    fun productionVoiceCaptureNeverInventsAnEmulatorTranscript() {
        val orchestrator = source("capture/VoiceCaptureOrchestrator.kt")

        assertFalse("emulatorDemoTranscript" in orchestrator)
        assertFalse(File("src/main/java/com/dailybeat/app/capture/WhisperBridge.kt").exists())
        assertFalse(File("src/main/java/com/dailybeat/app/capture/VoiceRecorder.kt").exists())
    }
}
