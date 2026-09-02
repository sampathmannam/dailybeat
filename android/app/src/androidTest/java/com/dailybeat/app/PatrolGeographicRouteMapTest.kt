package com.dailybeat.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailybeat.app.data.model.PriorityLocation
import com.dailybeat.app.data.model.PriorityLocationState
import com.dailybeat.app.patrolgrid.PatrolMapPoint
import com.dailybeat.app.ui.patrol.PatrolRouteMap
import com.dailybeat.app.ui.theme.DailyBeatTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PatrolGeographicRouteMapTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun productionEvidenceUsesGeographicMapWhenCoordinatesExist() {
        composeRule.setContent {
            DailyBeatTheme {
                PatrolRouteMap(
                    trackingActive = false,
                    visitedPriorityCount = 1,
                    plannedPoints = listOf(
                        PatrolMapPoint(13.0000, 77.5000),
                        PatrolMapPoint(13.0100, 77.5100),
                    ),
                    recordedPoints = listOf(
                        PatrolMapPoint(13.0010, 77.5010),
                        PatrolMapPoint(13.0080, 77.5090),
                    ),
                    priorityLocations = listOf(
                        PriorityLocation(
                            id = "priority-1",
                            name = "Bus stand",
                            state = PriorityLocationState.VISITED,
                            detail = "Visited",
                            latitude = 13.0050,
                            longitude = 77.5050,
                        ),
                    ),
                    totalRecordedPoints = 2,
                    demoMode = false,
                )
            }
        }

        composeRule.onNodeWithTag("patrol_geographic_map").assertExists()
    }

    @Test
    fun activePatrolShowsGeographicMapWhileWaitingForFirstGpsFix() {
        composeRule.setContent {
            DailyBeatTheme {
                PatrolRouteMap(
                    trackingActive = true,
                    visitedPriorityCount = 0,
                    demoMode = false,
                )
            }
        }

        composeRule.onNodeWithTag("patrol_geographic_map").assertExists()
    }
}
