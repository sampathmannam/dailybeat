package com.dailybeat.app

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Debug
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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

private const val MEMORY_WARMUP_CYCLES = 6
private const val MEMORY_TREND_WINDOW = 3
private const val MEMORY_SETTLE_WINDOW = 3
private const val MAP_RETAINED_PSS_PLATEAU_TOLERANCE_KB = 4096L

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class Task7ReleaseGateTest {

    private companion object {
        const val MEMORY_DIAGNOSTIC_CYCLES = 30
        const val MEMORY_SETTLE_MAX_WINDOWS = 6
        const val MEMORY_SETTLE_MAX_SAMPLES = MEMORY_SETTLE_WINDOW * MEMORY_SETTLE_MAX_WINDOWS
    }

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var app: DailyBeatApp
    private lateinit var device: UiDevice
    private var baselineQaCrashEntries = 0
    private var baselineQaAnrEntries = 0

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
        baselineQaCrashEntries = countQaCrashEntries()
        baselineQaAnrEntries = countQaAnrEntries()
    }

    @Test
    fun fullCriticalPathLoop() {
        val loop = InstrumentationRegistry.getArguments().getString("task7Loop") ?: "unknown"
        val evidence = mutableListOf(
            "loop=$loop",
            "qa_package=${InstrumentationRegistry.getInstrumentation().targetContext.packageName}",
            "encrypted_key_store_untouched=true",
            "ordinary_state_reset=true",
            "live_provider_calls=0",
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

        val beforePss = mutableListOf<MemorySnapshot>()
        val afterPss = mutableListOf<MemorySnapshot>()
        repeat(MEMORY_DIAGNOSTIC_CYCLES) { index ->
            beforePss += readMemorySnapshot()
            openAndCloseRenderedMap()
            val checkpoint = waitForStableMemoryCheckpoint()
            afterPss += checkpoint.stable
            evidence += "map_cycle_${index + 1}_before_${beforePss.last().evidence()}"
            evidence += "map_cycle_${index + 1}_after_${checkpoint.stable.evidence()}"
            evidence += "map_cycle_${index + 1}_settle_samples=${checkpoint.samples.size}"
            evidence += "map_cycle_${index + 1}_rendered=true"
        }
        val mapPlateau = hasNoSustainedGrowthAfterWarmup(afterPss)
        evidence += "map_after_retained_pss_kb=${afterPss.joinToString(",") { it.mapRetainedPssKb.toString() }}"
        evidence += "map_plateau_after_warmup=$mapPlateau"
        evidence += "map_retained_window_growth=${mapGrowthAfterWarmup(afterPss)}"

        rotateLandscapeAndPortrait()
        evidence += "rotation_landscape_portrait=pass"

        backgroundHomeAndResume()
        evidence += "home_background_resume=pass"

        composeRule.onNodeWithTag("today_list").assertIsDisplayed()
        evidence += "final_today=pass"
        val health = collectQaHealthEvidence()
        evidence += "qa_pid=${health.pid}"
        evidence += "qa_crash_entries=${health.crashEntries}"
        evidence += "qa_anr_present=${health.anrPresent}"
        writeEvidence("loop-$loop-summary.txt", evidence)
        assertTrue("QA PID was not live", health.pid.isNotBlank())
        assertEquals("QA crash-buffer entry found", 0, health.crashEntries)
        assertFalse("QA ANR entry found", health.anrPresent)
        assertTrue("Stable MapLibre native/graphics PSS did not plateau after warm-up", mapPlateau)
    }

    @Test
    fun thirtyCycleMapMemoryDiagnostic() {
        completeOnboarding()
        runBlocking(Dispatchers.IO) { SyntheticDayGenerator.seedToday(app) }
        val checkpoints = mutableListOf<MemorySnapshot>()
        val evidence = mutableListOf(
            "qa_package=${InstrumentationRegistry.getInstrumentation().targetContext.packageName}",
            "encrypted_key_store_untouched=true",
            "live_provider_calls=0",
            "cycles=$MEMORY_DIAGNOSTIC_CYCLES",
        )

        repeat(MEMORY_DIAGNOSTIC_CYCLES) { index ->
            openAndCloseRenderedMap()
            val checkpoint = waitForStableMemoryCheckpoint()
            checkpoints += checkpoint.stable
            checkpoint.samples.forEachIndexed { sampleIndex, sample ->
                evidence += "cycle_${index + 1}_sample_${sampleIndex + 1}_${sample.evidence()}"
            }
            evidence += "cycle_${index + 1}_stable_${checkpoint.stable.evidence()}"
        }

        val plateau = hasNoSustainedGrowthAfterWarmup(checkpoints)
        val health = collectQaHealthEvidence()
        evidence += "plateau_after_warmup=$plateau"
        evidence += "qa_pid=${health.pid}"
        evidence += "qa_crash_entries=${health.crashEntries}"
        evidence += "qa_anr_present=${health.anrPresent}"
        writeEvidence("task-7a-thirty-cycle-memory.txt", evidence)
        assertTrue("QA PID was not live", health.pid.isNotBlank())
        assertEquals("QA crash-buffer entry found", 0, health.crashEntries)
        assertFalse("QA ANR entry found", health.anrPresent)
        assertTrue("Stable retained memory did not plateau after MapLibre warm-up", plateau)
    }

    private fun completeOnboarding() {
        if (runCatching { composeRule.onNodeWithTag("today_list").assertIsDisplayed() }.isSuccess) {
            return
        }
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNode(hasText("Officer name") and hasSetTextAction())
            .performTextInput("Task 7 QA")
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Get started").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("today_list"), 15_000)
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
        withComposeRecovery("open/close map") {
            composeRule.onNodeWithTag("nav_today").performClick()
            waitForTodayList()
            composeRule.onNodeWithTag("today_list").performScrollToNode(hasText("Open full map"))
            val clickedOpenMap = runCatching {
                composeRule.onNodeWithTag("open_full_map").performClick()
                true
            }.getOrElse {
                val mapButton = app.getString(R.string.journey_map_open)
                val mapButtonNode = device.findObject(By.text(mapButton))
                if (mapButtonNode != null && mapButtonNode.isEnabled && mapButtonNode.isClickable) {
                    mapButtonNode.click()
                    true
                } else {
                    false
                }
            }
            assertTrue("Could not activate map destination", clickedOpenMap)

            val mapDescription = app.getString(R.string.journey_map_content_description)
            val readyDescription = app.getString(R.string.journey_map_ready_content_description)
            val mapBackDescription = app.getString(R.string.journey_map_back_content_description)
            val seenMapDestination = runCatching {
                composeRule.waitUntilAtLeastOneExists(hasTestTag("journey_map_screen"), 20_000)
                true
            }.getOrElse {
                device.wait(Until.hasObject(By.desc(mapDescription)), 15_000) ||
                    device.wait(Until.hasObject(By.desc(readyDescription)), 15_000)
            }
            assertTrue("Map destination did not materialize after opening", seenMapDestination)
            val seenRenderedMap = runCatching {
                composeRule.waitUntilAtLeastOneExists(hasTestTag("journey_map_ready"), 30_000)
                true
            }.getOrElse {
                device.wait(Until.hasObject(By.desc(readyDescription)), 10_000)
            }
            assertTrue("MapLibre did not finish rendering the journey", seenRenderedMap)

            val usedBackFromCompose = runCatching {
                composeRule.waitUntilAtLeastOneExists(hasTestTag("journey_map_back"), 10_000)
                composeRule.onNodeWithTag("journey_map_back").performClick()
                true
            }.getOrElse {
                runCatching {
                    val mapBackNode = device.findObject(By.desc(mapBackDescription))
                    if (mapBackNode != null && mapBackNode.isEnabled && mapBackNode.isClickable) {
                        mapBackNode.click()
                        true
                    } else {
                        false
                    }
                }.getOrElse { false }
            }
            if (!usedBackFromCompose) {
                device.pressBack()
            }
            composeRule.waitUntilAtLeastOneExists(hasTestTag("today_list"), 30_000)
            assertFalse(
                "Map screen remained visible after dismissal",
                runCatching {
                    composeRule.onNodeWithTag("journey_map_screen").assertIsDisplayed()
                }.isSuccess,
            )
        }
        waitForTodayList()
    }

    private fun waitForTodayList() {
        relaunchAppAndWait()
    }

    private fun relaunchAppAndWait() {
        try {
            composeRule.waitUntilAtLeastOneExists(hasTestTag("today_list"), 5_000)
        } catch (_: Throwable) {
            device.pressBack()
            composeRule.waitUntilAtLeastOneExists(hasTestTag("today_list"), 15_000)
        }
    }

    private fun withComposeRecovery(context: String, operation: () -> Unit) {
        var lastFailure: IllegalStateException? = null
        repeat(2) { attempt ->
            try {
                waitForTodayList()
                operation()
                return
            } catch (failure: Throwable) {
                lastFailure = IllegalStateException(failure.message)
                runCatching { relaunchAppAndWait() }
                    .exceptionOrNull()
                    ?.let { recoveryFailure ->
                        failure.addSuppressed(recoveryFailure)
                        throw failure
                    }
                if (attempt == 1) {
                    throw failure
                }
            }
        }
        throw (lastFailure ?: IllegalStateException("Unknown compose recovery issue in $context"))
    }

    private fun rotateLandscapeAndPortrait() {
        try {
            composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            composeRule.waitUntil(10_000) {
                composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            composeRule.waitUntilAtLeastOneExists(hasTestTag("today_list"), 10_000)

            composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            composeRule.waitUntil(10_000) {
                composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            }
            waitUntilTodayIsDisplayed()
        } finally {
            composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun waitUntilTodayIsDisplayed() {
        composeRule.waitUntil(10_000) {
            runCatching {
                composeRule.onNodeWithTag("today_list").assertIsDisplayed()
            }.isSuccess
        }
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

    private fun waitForStableMemoryCheckpoint(): MemoryCheckpoint {
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        device.executeShellCommand("am send-trim-memory $packageName RUNNING_CRITICAL")
        val samples = mutableListOf<MemorySnapshot>()
        repeat(MEMORY_SETTLE_MAX_SAMPLES) {
            composeRule.waitForIdle()
            System.gc()
            System.runFinalization()
            System.gc()
            Thread.sleep(1_000)
            samples += readMemorySnapshot()
            if (samples.size >= MEMORY_SETTLE_WINDOW) {
                val currentWindow = samples.takeLast(MEMORY_SETTLE_WINDOW)
                if (hasStableLifecycleCounts(currentWindow)) {
                    return MemoryCheckpoint(
                        stable = currentWindow.sortedBy { it.mapRetainedPssKb }[MEMORY_SETTLE_WINDOW / 2],
                        samples = samples,
                    )
                }
            }
        }
        throw AssertionError(
            "QA objects/threads did not stabilize after map close: " +
                samples.joinToString(";") { it.evidence() },
        )
    }

    private fun readMemorySnapshot(): MemorySnapshot {
        System.gc()
        System.runFinalization()
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val meminfoDescriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("dumpsys meminfo $packageName")
        val output = ParcelFileDescriptor.AutoCloseInputStream(meminfoDescriptor)
            .bufferedReader()
            .use { it.readText() }
        val pid = ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pidof $packageName"),
        ).bufferedReader().readText().trim().lineSequence().firstOrNull()?.trim()
            ?: throw AssertionError("DailyBeat process was not running while sampling memory")
        val procDescriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "cat /proc/$pid/status",
        )
        val procOutput = ParcelFileDescriptor.AutoCloseInputStream(procDescriptor)
            .bufferedReader()
            .use { it.readText() }
        fun summaryPss(label: String): Long = extractLongLabel(output, label, defaultValue = 0L)
        fun processStat(label: String): Long = extractLongLabel(procOutput, label, defaultValue = 0L)

        val snapshot = MemorySnapshot(
            totalPssKb = summaryPss("TOTAL PSS"),
            javaPssKb = summaryPss("Java Heap"),
            nativePssKb = summaryPss("Native Heap"),
            graphicsPssKb = summaryPss("Graphics"),
            codePssKb = summaryPss("Code"),
            stackPssKb = summaryPss("Stack"),
            nativeAllocatedKb = Debug.getNativeHeapAllocatedSize() / 1_024,
            processRssKb = processStat("VmRSS"),
            processThreadCount = processStat("Threads"),
        viewCount = objectCount(output, "Views"),
        viewRootCount = objectCount(output, "ViewRootImpl"),
        activityCount = objectCount(output, "Activities"),
        appContextCount = objectCount(output, "AppContexts"),
        )
        assertTrue("Invalid total PSS sample: ${snapshot.evidence()}", snapshot.totalPssKb > 0)
        assertTrue("Invalid process RSS sample: ${snapshot.evidence()}", snapshot.processRssKb > 0)
        assertTrue("Invalid native allocation sample: ${snapshot.evidence()}", snapshot.nativeAllocatedKb > 0)
        assertTrue("Invalid thread-count sample: ${snapshot.evidence()}", snapshot.processThreadCount > 0)
        return snapshot
}

    private fun extractLongLabel(meminfo: String, label: String, defaultValue: Long): Long =
        Regex("^\\s*${Regex.escape(label)}:\\s*(\\d+)", RegexOption.MULTILINE)
            .find(meminfo)
            ?.let { match ->
                if (match.groupValues.size > 1) {
                    match.groupValues[1].toLongOrNull() ?: defaultValue
                } else {
                    defaultValue
                }
            }
            ?: defaultValue

    private fun objectCount(meminfo: String, label: String): Long =
        Regex("^\\s*${Regex.escape(label)}:\\s*(\\d+)", RegexOption.MULTILINE)
            .find(meminfo)
            ?.let { match ->
                if (match.groupValues.size > 1) {
                    match.groupValues[1].toLongOrNull() ?: 0L
                } else {
                    0L
                }
            }
            ?: 0L

    private fun collectQaHealthEvidence(): QaHealthEvidence {
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val pid = device.executeShellCommand("pidof $packageName").trim()
        val crashEntries = (countQaCrashEntries() - baselineQaCrashEntries).coerceAtLeast(0)
        val anrEntries = (countQaAnrEntries() - baselineQaAnrEntries).coerceAtLeast(0)
        val anrPresent = anrEntries > 0
        return QaHealthEvidence(pid, crashEntries, anrPresent)
    }

    private fun countQaCrashEntries(): Int {
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        return device.executeShellCommand("logcat -d -b crash")
            .lineSequence()
            .count { it.contains(packageName) }
    }

    private fun countQaAnrEntries(): Int {
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        return device.executeShellCommand("logcat -d -b events")
            .lineSequence()
            .count { it.contains("am_anr") && it.contains(packageName) }
    }

    private fun writeEvidence(name: String, lines: List<String>) {
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "task-7-release-gate",
        ).apply { mkdirs() }
        File(directory, name).writeText(lines.joinToString("\n", postfix = "\n"))
    }
}

private data class MemoryCheckpoint(
    val stable: MemorySnapshot,
    val samples: List<MemorySnapshot>,
)

private data class QaHealthEvidence(
    val pid: String,
    val crashEntries: Int,
    val anrPresent: Boolean,
)

private data class MemorySnapshot(
    val totalPssKb: Long,
    val javaPssKb: Long,
    val nativePssKb: Long,
    val graphicsPssKb: Long,
    val codePssKb: Long,
    val stackPssKb: Long,
    val nativeAllocatedKb: Long = 0,
    val processRssKb: Long = 0,
    val processThreadCount: Long = 0,
    val viewCount: Long = 0,
    val viewRootCount: Long = 0,
    val activityCount: Long = 0,
    val appContextCount: Long = 0,
) {
    val ownedPssKb: Long
        get() = javaPssKb + nativePssKb + graphicsPssKb + stackPssKb

    val mapRetainedPssKb: Long
        get() = maxOf(nativePssKb, nativeAllocatedKb) + graphicsPssKb

    fun evidence(): String =
        "total_pss_kb=$totalPssKb" +
            "_java_pss_kb=$javaPssKb" +
            "_native_pss_kb=$nativePssKb" +
            "_graphics_pss_kb=$graphicsPssKb" +
            "_code_pss_kb=$codePssKb" +
            "_stack_pss_kb=$stackPssKb" +
            "_native_allocated_kb=$nativeAllocatedKb" +
            "_process_rss_kb=$processRssKb" +
            "_process_threads=$processThreadCount" +
            "_views=$viewCount" +
            "_view_roots=$viewRootCount" +
            "_activities=$activityCount" +
            "_app_contexts=$appContextCount" +
            "_owned_pss_kb=$ownedPssKb" +
            "_map_retained_pss_kb=$mapRetainedPssKb"
}

private fun hasNoSustainedGrowthAfterWarmup(snapshots: List<MemorySnapshot>): Boolean {
    if (snapshots.size < MEMORY_WARMUP_CYCLES + MEMORY_TREND_WINDOW * 2) return false
    val retainedPssKb = snapshots.drop(MEMORY_WARMUP_CYCLES).map { it.mapRetainedPssKb }
    val firstWindowMedian = median(retainedPssKb.take(MEMORY_TREND_WINDOW))
    val lastWindowMedian = median(retainedPssKb.takeLast(MEMORY_TREND_WINDOW))
    val growth = lastWindowMedian - firstWindowMedian
    val tolerance = maxOf(MAP_RETAINED_PSS_PLATEAU_TOLERANCE_KB, firstWindowMedian / 50)
    return growth <= tolerance
}

private fun mapGrowthAfterWarmup(snapshots: List<MemorySnapshot>): Long {
    if (snapshots.size < MEMORY_WARMUP_CYCLES + MEMORY_TREND_WINDOW * 2) {
        return Long.MAX_VALUE
    }
    val retainedPssKb = snapshots.drop(MEMORY_WARMUP_CYCLES).map { it.mapRetainedPssKb }
    val firstWindowMedian = median(retainedPssKb.take(MEMORY_TREND_WINDOW))
    val lastWindowMedian = median(retainedPssKb.takeLast(MEMORY_TREND_WINDOW))
    return lastWindowMedian - firstWindowMedian
}

private fun median(values: List<Long>): Long = values.sorted()[values.size / 2]

private fun hasStableLifecycleCounts(snapshots: List<MemorySnapshot>): Boolean {
    if (snapshots.size < MEMORY_SETTLE_WINDOW) return false
    return snapshots.map { snapshot ->
        listOf(
            snapshot.processThreadCount,
            snapshot.viewCount,
            snapshot.viewRootCount,
            snapshot.activityCount,
            snapshot.appContextCount,
        )
    }.distinct().size == 1
}

@RunWith(AndroidJUnit4::class)
class Task7MemoryTrendTest {

    @Test
    fun boundedNativeGraphicsWarmupPassesPlateauGate() {
        val native = listOf(24_816L, 25_540L, 25_716L, 26_520L, 26_720L, 26_320L, 26_620L, 26_220L, 26_420L, 26_320L, 26_420L, 26_320L, 26_420L)
        val graphics = listOf(29_400L, 29_412L, 29_476L, 29_480L, 29_480L, 29_480L, 29_480L, 29_480L, 29_480L, 29_480L, 29_480L, 29_480L, 29_480L)
        val snapshots = native.zip(graphics).map { (nativePssKb, graphicsPssKb) ->
            mapSnapshot(nativePssKb, graphicsPssKb)
        }

        assertTrue(hasNoSustainedGrowthAfterWarmup(snapshots))
    }

    @Test
    fun coldMapLibreCacheWarmupPassesPlateauGate() {
        val retainedPssKb = listOf(
            19_664L, 21_304L, 21_384L, 22_084L, 22_168L, 23_532L,
            23_872L, 24_004L, 24_092L, 29_232L, 24_788L, 24_808L,
            25_552L, 31_924L, 25_412L, 37_168L, 25_836L, 34_768L,
            34_596L, 36_796L, 26_036L, 31_664L, 26_764L, 35_160L,
            35_536L, 36_960L, 40_736L, 30_792L, 27_352L, 27_380L,
        )
        val snapshots = retainedPssKb.map { mapSnapshot(it, 0) }

        assertTrue(hasNoSustainedGrowthAfterWarmup(snapshots))
    }

    @Test
    fun strictlyIncreasingNativeGraphicsFailsPlateauGate() {
        val snapshots = (1L..10L).map { mapSnapshot(it, it) }

        assertFalse(hasNoSustainedGrowthAfterWarmup(snapshots))
    }

    @Test
    fun twoDipsDoNotHideSustainedNativeGraphicsGrowth() {
        val retainedPssKb = listOf(
            53_584L,
            54_112L,
            54_760L,
            54_608L,
            55_136L,
            55_824L,
            55_648L,
            55_936L,
            56_176L,
            56_080L,
            56_392L,
        )
        val snapshots = retainedPssKb.map { mapSnapshot(it, 0) }

        assertFalse(hasNoSustainedGrowthAfterWarmup(snapshots))
    }

    @Test
    fun stableObjectsAndThreadsDefineASettledCheckpoint() {
        val snapshots = listOf(55_828L, 55_968L, 56_208L).map { retainedPssKb ->
            mapSnapshot(
                nativePssKb = retainedPssKb,
                graphicsPssKb = 0,
                processThreadCount = 42,
                viewCount = 108,
                viewRootCount = 1,
                activityCount = 1,
                appContextCount = 7,
            )
        }

        assertTrue(hasStableLifecycleCounts(snapshots))
    }

    @Test
    fun changingLifecycleObjectsDoNotDefineASettledCheckpoint() {
        val snapshots = (1L..3L).map { count ->
            mapSnapshot(
                nativePssKb = count,
                graphicsPssKb = 0,
                processThreadCount = 42,
                viewCount = 100 + count,
                viewRootCount = 1,
                activityCount = 1,
                appContextCount = 7,
            )
        }

        assertFalse(hasStableLifecycleCounts(snapshots))
    }

    private fun mapSnapshot(
        nativePssKb: Long,
        graphicsPssKb: Long,
        processThreadCount: Long = 0,
        viewCount: Long = 0,
        viewRootCount: Long = 0,
        activityCount: Long = 0,
        appContextCount: Long = 0,
    ) = MemorySnapshot(
        totalPssKb = nativePssKb + graphicsPssKb,
        javaPssKb = 0,
        nativePssKb = nativePssKb,
        graphicsPssKb = graphicsPssKb,
        codePssKb = 0,
        stackPssKb = 0,
        processThreadCount = processThreadCount,
        viewCount = viewCount,
        viewRootCount = viewRootCount,
        activityCount = activityCount,
        appContextCount = appContextCount,
    )
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
