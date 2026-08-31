package com.dailybeat.app

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.dailybeat.app.cloud.CloudTokenBudgets
import com.dailybeat.app.cloud.DayContextBuilder
import com.dailybeat.app.cloud.ReportIntegrityValidator
import com.dailybeat.app.data.settings.CloudProvider
import com.dailybeat.app.synthetic.SyntheticDayGenerator
import com.dailybeat.app.util.DateKeys
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class Task7ReleaseGateTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var app: DailyBeatApp
    private lateinit var device: UiDevice

    @Before
    fun resetOrdinaryQaStateWithoutTouchingEncryptedKey() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        app = ApplicationProvider.getApplicationContext()
        runBlocking {
            withContext(Dispatchers.IO) { app.db.clearAllTables() }
        }
        app.settingsRepository.setCloudLlmEnabled(false)
        app.settingsRepository.setAutoEveningReport(false)
        app.settingsRepository.setAutoMiddayPulse(false)
        grantCorePermissions()
        device = UiDevice.getInstance(instrumentation)
        composeRule.activityRule.scenario.recreate()
    }

    @Test
    fun fullCriticalPathLoop() {
        val loop = InstrumentationRegistry.getArguments().getString("task7Loop") ?: "unknown"
        val evidence = mutableListOf(
            "loop=$loop",
            "qa_package=${InstrumentationRegistry.getInstrumentation().targetContext.packageName}",
            "encrypted_key_store_untouched=true",
            "ordinary_state_reset=true",
        )

        completeOnboarding()
        evidence += "onboarding=pass"

        val first = runBlocking(Dispatchers.IO) { SyntheticDayGenerator.seedToday(app) }
        val second = runBlocking(Dispatchers.IO) { SyntheticDayGenerator.seedToday(app) }
        assertEquals(7, first.visitsInserted)
        assertEquals(8, first.eventsInserted)
        assertEquals(0, second.visitsInserted)
        assertEquals(0, second.eventsInserted)
        evidence += "synthetic_first=7_visits_8_events"
        evidence += "synthetic_second=0_visits_0_events"

        verifyNormalNavigationDoesNotCreateFullMap()
        evidence += "normal_navigation=pass"
        evidence += "normal_navigation_full_map_nodes=0"

        val beforePss = mutableListOf<Long>()
        val afterPss = mutableListOf<Long>()
        repeat(3) { index ->
            beforePss += settledTotalPssKb()
            openAndCloseRenderedMap()
            afterPss += settledTotalPssKb()
            evidence += "map_cycle_${index + 1}_before_pss_kb=${beforePss.last()}"
            evidence += "map_cycle_${index + 1}_after_pss_kb=${afterPss.last()}"
            evidence += "map_cycle_${index + 1}_rendered=true"
        }
        val monotonicGrowth = afterPss.zipWithNext().all { (firstValue, secondValue) ->
            secondValue > firstValue
        }
        evidence += "map_after_pss_kb=${afterPss.joinToString(",")}"
        evidence += "map_monotonic_growth=$monotonicGrowth"

        rotateLandscapeAndPortrait()
        evidence += "rotation_landscape_portrait=pass"

        backgroundHomeAndResume()
        evidence += "home_background_resume=pass"

        composeRule.onNodeWithTag("today_list").assertIsDisplayed()
        evidence += "final_today=pass"
        writeEvidence("loop-$loop-summary.txt", evidence)
        assertFalse("Retained PSS increased monotonically after map closes", monotonicGrowth)
    }

    private fun completeOnboarding() {
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNode(hasText("Officer name") and hasSetTextAction())
            .performTextInput("Task 7 QA")
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Get started").performClick()
        composeRule.onNodeWithTag("today_list").assertIsDisplayed()
    }

    private fun verifyNormalNavigationDoesNotCreateFullMap() {
        val mapDescription = app.getString(R.string.journey_map_content_description)
        val readyDescription = app.getString(R.string.journey_map_ready_content_description)
        val destinations = listOf(
            "nav_diary" to "nav_diary",
            "nav_history" to "nav_history",
            "nav_settings" to "nav_settings",
            "nav_today" to "today_list",
        )
        destinations.forEach { (navigationTag, visibleTag) ->
            composeRule.onNodeWithTag(navigationTag).performClick()
            composeRule.onNodeWithTag(visibleTag).assertIsDisplayed()
            assertFalse(device.hasObject(By.desc(mapDescription)))
            assertFalse(device.hasObject(By.desc(readyDescription)))
        }
    }

    private fun openAndCloseRenderedMap() {
        composeRule.onNodeWithTag("today_list").performScrollToNode(hasTestTag("open_full_map"))
        composeRule.onNodeWithTag("open_full_map").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("journey_map_screen"), 10_000)
        composeRule.waitUntilAtLeastOneExists(hasTestTag("journey_map_ready"), 30_000)

        val backDescription = app.getString(R.string.journey_map_back_content_description)
        device.pressBack()
        assertTrue(device.wait(Until.gone(By.desc(backDescription)), 10_000))
        composeRule.onNodeWithTag("today_list").assertIsDisplayed()
    }

    private fun rotateLandscapeAndPortrait() {
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        composeRule.waitUntil(10_000) {
            composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        composeRule.onNodeWithTag("today_list").assertIsDisplayed()
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        composeRule.waitUntil(10_000) {
            composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        }
        composeRule.onNodeWithTag("today_list").assertIsDisplayed()
    }

    private fun backgroundHomeAndResume() {
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        assertTrue(device.pressHome())
        assertTrue(device.wait(Until.gone(By.pkg(packageName).depth(0)), 10_000))
        device.executeShellCommand(
            "am start -W -n $packageName/com.dailybeat.app.MainActivity",
        )
        composeRule.waitUntilAtLeastOneExists(hasTestTag("today_list"), 10_000)
    }

    private fun settledTotalPssKb(): Long {
        System.gc()
        System.runFinalization()
        Thread.sleep(1_000)
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("dumpsys meminfo $packageName")
        val output = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { it.readText() }
        return requireNotNull(Regex("TOTAL PSS:\\s+(\\d+)").find(output))
            .groupValues[1]
            .toLong()
    }

    private fun writeEvidence(name: String, lines: List<String>) {
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "task-7-release-gate",
        ).apply { mkdirs() }
        File(directory, name).writeText(lines.joinToString("\n", postfix = "\n"))
    }
}

@RunWith(AndroidJUnit4::class)
class Task7LiveContractGateTest {

    @Test
    fun oneCappedDailyDiaryCallPersistsOnlyAfterCitationValidation() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        assertTrue("QA encrypted API key is not present", app.settingsRepository.secureApiKey.hasApiKey())
        app.settingsRepository.setAutoEveningReport(false)
        app.settingsRepository.setAutoMiddayPulse(false)
        app.settingsRepository.setCloudProvider(CloudProvider.DEEPSEEK.id)
        app.settingsRepository.setCloudModel(CloudProvider.DEEPSEEK.defaultModel)
        app.settingsRepository.setCloudLlmEnabled(true)

        val date = DateKeys.today()
        val visits = withContext(Dispatchers.IO) { app.visitRepository.visitsForDate(date) }
        val events = withContext(Dispatchers.IO) { app.eventRepository.eventsForDate(date) }
        assertEquals(7, visits.size)
        assertEquals(8, events.size)
        val source = DayContextBuilder.buildDetailed(
            date = date,
            officerName = app.settingsRepository.get().officerName,
            visits = visits,
            events = events,
        )
        val limitedContext = com.dailybeat.app.cloud.ContextLimiter.trimForLlm(source.text)
        val userPrompt = """
            Generate today's official daily diary from this passive activity log.
            Cite every fact with [V#] and [E#] refs from the DATA block.
            End with a one-line summary of the day.

            DATA:
            $limitedContext
        """.trimIndent()

        withContext(Dispatchers.IO) { app.db.diaries().deleteAll() }
        assertNull(withContext(Dispatchers.IO) { app.diaryRepository.todayText() })

        val startedAt = System.nanoTime()
        val result = app.cloudLlm.generate(
            app.settingsRepository.get(),
            DayContextBuilder.SYSTEM_PROMPT,
            userPrompt,
            CloudTokenBudgets.DAILY_DIARY,
        )
        val latencyMs = (System.nanoTime() - startedAt) / 1_000_000
        val output = result.getOrNull()
        val citations = output
            ?.let { Regex("\\[[VE]\\d+]").findAll(it).map { match -> match.value }.distinct().toList() }
            .orEmpty()
        val integrity = output?.let {
            ReportIntegrityValidator.validate(it, source.visitRefCount, source.eventRefCount)
        }
        if (integrity?.isValid == true) {
            withContext(Dispatchers.IO) { app.diaryRepository.saveForDate(date, output) }
        }
        val saved = withContext(Dispatchers.IO) { app.diaryRepository.todayText() }
        val passed = output != null && integrity?.isValid == true && saved == output

        val evidence = listOf(
            "live_call_count=1",
            "pass=$passed",
            "latency_ms=$latencyMs",
            "output_length_chars=${output?.length ?: 0}",
            "citation_ids=${citations.joinToString(",")}",
            "citations_valid=${integrity?.isValid == true}",
            "saved_before_validation=false",
            "saved_after_validation=${saved != null}",
        )
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "task-7-release-gate",
        ).apply { mkdirs() }
        File(directory, "live-contract-summary.txt")
            .writeText(evidence.joinToString("\n", postfix = "\n"))
        app.settingsRepository.setCloudLlmEnabled(false)

        assertNotNull("One capped live request failed", output)
        assertTrue("Live response citations did not match synthetic context", integrity?.isValid == true)
        assertTrue("Validated live response was not saved exactly once", saved == output)
    }
}
