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
    fun launcherUsesSelectedSmartFieldNoteBrand() {
        val colors = resource("values/colors.xml")
        val foreground = resource("drawable/ic_launcher_foreground.xml")

        assertTrue("#0B1633" in colors)
        assertTrue("#FFF7E8" in foreground)
        assertTrue("#FF6B4A" in foreground)
        assertTrue("#F4A629" in foreground)
        assertTrue(Regex("<path\\b").findAll(foreground).count() >= 4)
        assertFalse("M54,30 L70,54 L54,78 L38,54 Z" in foreground)
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
}
