package com.dailybeat.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailybeat.app.ui.components.JourneyMapModel
import com.dailybeat.app.ui.components.JourneyMapSnapshot
import com.dailybeat.app.ui.components.JourneyPoint
import com.dailybeat.app.ui.theme.DailyBeatTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JourneyMapZeroHeightTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun zeroHeightFallbackDoesNotBlockDrawingOrInteraction() {
        requireDisposableTestApp()
        var clicks = 0
        val model = JourneyMapModel.fromPoints(listOf(
            JourneyPoint(100L, 11.45, 78.18, "transit"),
            JourneyPoint(200L, 11.46, 78.19, "transit"),
        ))
        compose.setContent {
            DailyBeatTheme {
                Column {
                    JourneyMapSnapshot(
                        model = model,
                        modifier = Modifier.width(300.dp).height(0.dp),
                        testTag = "zero_height_map",
                        allowNetwork = false,
                    )
                    Button(onClick = { clicks++ }) { Text("Map controls remain responsive") }
                }
            }
        }
        compose.onNodeWithTag("zero_height_map").assertHeightIsEqualTo(0.dp)
        compose.onNodeWithText("Map controls remain responsive").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}
