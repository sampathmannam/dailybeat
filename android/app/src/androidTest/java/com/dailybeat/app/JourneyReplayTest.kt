package com.dailybeat.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dailybeat.app.ui.components.JourneyMapModel
import com.dailybeat.app.ui.components.JourneyMapPreviewContent
import com.dailybeat.app.ui.components.JourneyPoint
import com.dailybeat.app.ui.components.JourneyRoutePreview
import com.dailybeat.app.ui.feed.RoutePoint
import com.dailybeat.app.ui.theme.DailyBeatTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class JourneyReplayTest {
    private val motionScale = object : MotionDurationScale {
        var factor = 1f
        override val scaleFactor: Float get() = factor
    }
    // CI disables system animations. Supply a test-local scale rather than changing device
    // settings, so animation advancement and reduced-motion behavior are both deterministic.
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>(effectContext = motionScale)
    private lateinit var app: DailyBeatApp
    private var previouslyOnline = false
    private val visible = mutableStateOf(true)
    private val original = JourneyMapModel.fromPoints(listOf(
        JourneyPoint(100L, 11.45, 78.18, "transit"),
        JourneyPoint(200L, 11.46, 78.19, "transit"),
        JourneyPoint(300L, 11.47, 78.20, "transit"),
    ))
    private val model = mutableStateOf(original)

    @Before fun prepare() {
        requireDisposableTestApp()
        app = ApplicationProvider.getApplicationContext()
        previouslyOnline = app.mapSettings.state.value.allowOnlineMaps
        app.mapSettings.setOnline(false)
        compose.mainClock.autoAdvance = false
    }

    @After fun cleanup() {
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnUiThread { visible.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        app.mapSettings.setOnline(previouslyOnline)
    }

    private fun showUnavailableMap() {
        val preferences = app.mapSettings.state.value.copy(allowOnlineMaps = false)
        compose.setContent {
            DailyBeatTheme {
                if (visible.value) JourneyMapPreviewContent(
                    model = model.value,
                    modifier = Modifier.fillMaxSize(),
                    onFailure = {},
                    preferences = preferences,
                    lease = null,
                    nativeMapEnabled = false,
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun progress(): Float = compose.onNodeWithTag("route_replay_progress")
        .fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

    @Test fun replayWorksWithUnavailableMapAndSurvivesAppendButNeverErase() {
        showUnavailableMap()
        compose.onNodeWithTag("journey_map_fallback").assertIsDisplayed()
        compose.onNodeWithTag("replay_route").assertIsEnabled().performClick()
        compose.mainClock.advanceTimeBy(1_000L)
        val beforeAppend = progress()
        assertTrue(beforeAppend > 0f && beforeAppend < 1f)
        compose.onNodeWithText("Pause replay").assertIsDisplayed()

        compose.runOnUiThread {
            model.value = JourneyMapModel.fromPoints(original.points.map { it.copy(stopLabel = "Updated name") } +
                JourneyPoint(400L, 11.48, 78.21, "transit"))
        }
        compose.mainClock.advanceTimeByFrame()
        assertTrue(progress() >= beforeAppend && progress() < 0.8f)
        compose.onNodeWithText("Pause replay").assertIsDisplayed()

        compose.onNodeWithTag("replay_route").performClick()
        compose.mainClock.advanceTimeByFrame()
        val paused = progress()
        compose.mainClock.advanceTimeBy(500L)
        assertEquals(paused, progress(), 0.001f)
        compose.onNodeWithText("Resume replay").performClick()
        compose.mainClock.advanceTimeBy(500L)
        assertTrue(progress() > paused)

        compose.runOnUiThread { model.value = JourneyMapModel.fromPoints(emptyList()) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(1f, progress(), 0f)
        compose.onNodeWithTag("replay_route").assertIsNotEnabled()
        compose.onNodeWithTag("journey_map_fallback").assertDoesNotExist()
    }

    @Test fun localFallbackVisiblyAdvancesAndCanBeSeekedWithoutAnimationOrTiles() {
        showUnavailableMap()
        compose.onNodeWithTag("route_replay_progress")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.25f) }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0.25f, progress(), 0.001f)
        val earlier = compose.onNodeWithTag("journey_map_fallback").captureToImage().asAndroidBitmap()
        compose.onNodeWithTag("route_replay_progress")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.75f) }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0.75f, progress(), 0.001f)
        val later = compose.onNodeWithTag("journey_map_fallback").captureToImage().asAndroidBitmap()
        try {
            assertFalse("Local replay must change the visible route, not just its percentage", earlier.sameAs(later))
        } finally {
            earlier.recycle()
            later.recycle()
        }
        compose.onNodeWithText("Resume replay").assertIsDisplayed()
    }

    @Test fun leavingForegroundPausesPlaybackWithoutLosingItsPosition() {
        showUnavailableMap()
        compose.onNodeWithTag("replay_route").performClick()
        compose.mainClock.advanceTimeBy(800L)
        val beforeDeparture = progress()
        assertTrue(beforeDeparture > 0f && beforeDeparture < 1f)
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        // Apply the lifecycle pause before spending virtual time outside the foreground.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(500L)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.mainClock.advanceTimeByFrame()
        val paused = progress()
        // Cancellation may consume one frame of this four-second replay, but never reset it.
        assertEquals(beforeDeparture, paused, 0.01f)
        compose.mainClock.advanceTimeBy(500L)
        assertEquals(paused, progress(), 0.001f)
        compose.onNodeWithText("Resume replay").assertIsDisplayed()
    }

    @Test fun reducedMotionFinishesImmediatelyButManualRouteInspectionStillWorks() {
        motionScale.factor = 0f
        showUnavailableMap()
        compose.onNodeWithTag("replay_route").performClick()
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        assertEquals(1f, progress(), 0f)
        compose.onNodeWithText("Replay route").assertIsDisplayed()
        compose.onNodeWithTag("route_replay_progress")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0.5f, progress(), 0.001f)
        compose.onNodeWithText("Resume replay").assertIsDisplayed()
    }

    @Test fun tappingTodayMapOpensJourneyInsteadOfBeingAnInertImage() {
        var opens = 0
        compose.setContent {
            DailyBeatTheme {
                if (visible.value) JourneyRoutePreview(
                    route = listOf(RoutePoint(11.45, 78.18, false, 100L), RoutePoint(11.47, 78.20, false, 300L)),
                    onOpenMap = { opens++ },
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("today_journey_map").assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(1, opens) }
        compose.onNodeWithTag("open_full_map").performClick()
        compose.runOnIdle { assertEquals(2, opens) }
    }

    @Test fun fallbackReplayControlsRemainReadableOnNarrowPhonesAtTwoHundredPercent() {
        motionScale.factor = 0f
        val phoneWidth = mutableStateOf(320.dp)
        val dark = mutableStateOf(false)
        val preferences = app.mapSettings.state.value.copy(allowOnlineMaps = false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                DailyBeatTheme(darkTheme = dark.value) {
                    // Match the app Scaffold's background/content colours and16dp inset.
                    Surface(
                        modifier = Modifier.width(phoneWidth.value).fillMaxHeight(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Column(Modifier.fillMaxSize().padding(16.dp)) {
                            if (visible.value) JourneyMapPreviewContent(
                                model = model.value,
                                modifier = Modifier.fillMaxSize(),
                                onFailure = {},
                                preferences = preferences,
                                lease = null,
                                nativeMapEnabled = false,
                            )
                        }
                    }
                }
            }
        }
        for ((width, darkMode) in listOf(320 to false, 360 to true)) {
            compose.runOnUiThread { phoneWidth.value = width.dp; dark.value = darkMode }
            repeat(2) { compose.mainClock.advanceTimeByFrame() }
            val slider = compose.onNodeWithTag("route_replay_progress")
            slider
                .performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
            compose.mainClock.advanceTimeByFrame()
            val sliderNode = slider.fetchSemanticsNode()
            val sliderLayoutHeight = with(sliderNode.layoutInfo.density) {
                sliderNode.layoutInfo.height.toDp()
            }
            // Material3's merged Slider semantics report its 44dp visual thumb. Verify the
            // complete layout reservation AND real touches beyond that visual thumb instead.
            assertTrue("Slider must reserve a 48dp touch target, was $sliderLayoutHeight",
                sliderLayoutHeight >= 48.dp)
            val touchEdgePx = with(sliderNode.layoutInfo.density) { 23.dp.toPx() }
            slider.performTouchInput { click(Offset(this.width * 0.25f, center.y - touchEdgePx)) }
            compose.mainClock.advanceTimeByFrame()
            assertTrue("The upper edge of the 48dp slider touch target must seek", progress() < 0.4f)
            slider.performTouchInput { click(Offset(this.width * 0.75f, center.y + touchEdgePx)) }
            compose.mainClock.advanceTimeByFrame()
            assertTrue("The lower edge of the 48dp slider touch target must seek", progress() > 0.6f)
            slider.performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
            compose.mainClock.advanceTimeByFrame()
            val bitmap = compose.onNodeWithTag("journey_map_card").captureToImage().asAndroidBitmap()
            val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
                "journey-replay-evidence").apply { mkdirs() }
            try {
                File(directory, "fallback-${width}dp-${if (darkMode) "dark" else "light"}-200pct.png")
                    .outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
            compose.onNodeWithTag("journey_map_fallback").assertIsDisplayed().assertHeightIsAtLeast(100.dp)
            compose.onNodeWithTag("replay_route").assertHeightIsAtLeast(48.dp)
            listOf("Resume replay", "50% travelled").forEach { text ->
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue("Missing text layout: $text", layouts.isNotEmpty())
                layouts.forEach { layout ->
                    // Compose can retain a wider paragraph container while measuring Text to
                    // its intrinsic width. Check actual laid-out lines, not that unused width.
                    assertFalse("$text loses lines at ${width}dp/200%", layout.multiParagraph.didExceedMaxLines)
                    for (line in 0 until layout.lineCount) {
                        assertFalse("$text is ellipsized", layout.isLineEllipsized(line))
                        assertTrue("$text line $line clips horizontally at ${width}dp/200%: " +
                            "${layout.getLineLeft(line)}..${layout.getLineRight(line)} in ${layout.size.width}px",
                            layout.getLineLeft(line) >= -0.5f &&
                                layout.getLineRight(line) <= layout.size.width + 0.5f)
                        assertTrue("$text line $line clips vertically at ${width}dp/200%",
                            layout.getLineTop(line) >= -0.5f &&
                                layout.getLineBottom(line) <= layout.size.height + 0.5f)
                    }
                    if (text == "50% travelled") {
                        assertEquals("Percentage must not be squeezed into a narrow leftover column", 1, layout.lineCount)
                    }
                }
            }
        }
    }
}
