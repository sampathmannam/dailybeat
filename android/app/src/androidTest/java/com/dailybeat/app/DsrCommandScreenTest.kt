package com.dailybeat.app

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dailybeat.app.dsr.DsrImportOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Exercises the complete on-device DSR path with fictional future-dated data:
 * generated PDF -> private document store -> PDFBox -> parser -> Room -> Compose dashboard.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class DsrCommandScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var app: DailyBeatApp

    @Before
    fun resetQaApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("dailybeat_settings", android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        app = ApplicationProvider.getApplicationContext()
        runBlocking {
            withContext(Dispatchers.IO) {
                app.db.clearAllTables()
                File(app.filesDir, "dsr_imports").deleteRecursively()
            }
        }
        app.settingsRepository.setOnboardingComplete(true)
        app.settingsRepository.setOfficerName("IPS Test")
        composeRule.activityRule.scenario.recreate()
    }

    @Test
    fun digitalTextPdfReachesTheCommandDashboardAndDuplicateIsIdempotent() {
        val uri = createSyntheticDsrPdf("01.01.99 SYNTHETIC DSR.pdf", crimeNumber = "9001")

        val first = runBlocking { app.dsrImportService.import(uri) }
        val duplicate = runBlocking { app.dsrImportService.import(uri) }

        assertTrue(first is DsrImportOutcome.Imported)
        assertTrue(duplicate is DsrImportOutcome.AlreadyImported)
        assertEquals(1, runBlocking { app.dsrRepository.observeRecentImports().first() }.size)

        composeRule.onNodeWithTag("nav_dsr").performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Latest import"), timeoutMillis = 20_000)
        composeRule.onNodeWithText("DSR Command").assertIsDisplayed()
        composeRule.onNodeWithText("Reported").assertIsDisplayed()
        composeRule.onNodeWithTag("dsr_command_list")
            .performScrollToNode(hasText("Cr. 9001/2099", substring = true))
        composeRule.onNode(hasText("Cr. 9001/2099", substring = true)).assertIsDisplayed()
    }

    @Test
    fun correctedPdfForTheSameDateArchivesTheEarlierVersion() {
        val firstUri = createSyntheticDsrPdf("01.01.99 SYNTHETIC DSR.pdf", crimeNumber = "9001")
        val correctedUri = createSyntheticDsrPdf("01.01.99 SYNTHETIC DSR corrected.pdf", crimeNumber = "9002")

        runBlocking { app.dsrImportService.import(firstUri) }
        val corrected = runBlocking { app.dsrImportService.import(correctedUri) }
        val imports = runBlocking { app.dsrRepository.observeRecentImports().first() }

        assertTrue(corrected is DsrImportOutcome.Imported && corrected.replacedPreviousVersion)
        assertEquals(2, imports.size)
        assertEquals(1, imports.count { it.active })
        assertFalse(imports.first { it.originalFileName == "01.01.99 SYNTHETIC DSR.pdf" }.active)
    }

    @Test
    fun concurrentIdenticalImportsRemainIdempotent() {
        val uri = createSyntheticDsrPdf("01.01.99 SYNTHETIC concurrent DSR.pdf", crimeNumber = "9010")

        val outcomes = runBlocking {
            List(32) {
                async(Dispatchers.IO) { app.dsrImportService.import(uri) }
            }.awaitAll()
        }

        assertEquals(1, outcomes.count { it is DsrImportOutcome.Imported })
        assertEquals(31, outcomes.count { it is DsrImportOutcome.AlreadyImported })
        assertEquals(1, runBlocking { app.dsrRepository.observeRecentImports().first() }.size)
    }

    private fun createSyntheticDsrPdf(fileName: String, crimeNumber: String): Uri {
        val directory = File(app.filesDir, "DailyBeat").also { check(it.exists() || it.mkdirs()) }
        val file = File(directory, fileName)
        val lines = listOf(
            "DAILY DSR 01.01.2099",
            "Police Station Advance Forecast 02.01.99",
            "Rasipuram At 11.00 AM a synthetic protest drill will be held. Crowd: About 123 persons.",
            "Vennandur Nil",
            "Station Progress",
            "Rasipuram 1 1 - 4 3 2 1 5 6 1 - -",
            "Total 1 1 - 4 3 2 1 5 6 1 - -",
            "I-Reported Cases",
            "S.No Head Station Cr No Sec of Law",
            "1. 194 BNSS Rasipuram $crimeNumber/99 194 BNSS",
            "I-Charged Cases 2.O",
            "Synthetic verification text confirms this is a digital-text test document with no operational data.",
            "Additional fictional content keeps the extraction-quality check representative of a real report.",
            "All names, identifiers, dates and events in this generated test file are fictional.",
        )
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(1200, 1800, 1).create())
            val paint = Paint().apply {
                textSize = 20f
                typeface = Typeface.MONOSPACE
            }
            lines.forEachIndexed { index, line ->
                page.canvas.drawText(line, 36f, 52f + index * 32f, paint)
            }
            document.finishPage(page)
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        return FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
    }
}
