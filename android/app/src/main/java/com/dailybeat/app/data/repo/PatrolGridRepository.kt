package com.dailybeat.app.data.repo

import android.content.Context
import com.dailybeat.app.data.db.PatrolTrackDao
import com.dailybeat.app.data.model.PatrolAssignmentDraft
import com.dailybeat.app.data.model.PatrolMission
import com.dailybeat.app.data.model.PatrolMissionStatus
import com.dailybeat.app.data.model.PatrolRouteGuidance
import com.dailybeat.app.data.model.PatrolRoutePlan
import com.dailybeat.app.data.model.PatrolUnitOption
import com.dailybeat.app.data.model.PriorityLocation
import com.dailybeat.app.data.model.PriorityLocationState
import com.dailybeat.app.data.settings.SettingsRepository

data class PatrolGridSnapshot(
    val primaryMission: PatrolMission,
    val activeMissions: List<PatrolMission>,
    val upcomingMission: PatrolMission,
    val recordedTrackPoints: Int,
    val trackingActive: Boolean,
    val observationCount: Int,
)

class PatrolGridRepository(
    context: Context,
    private val trackDao: PatrolTrackDao,
    private val settings: SettingsRepository,
) {
    private val prefs = context.getSharedPreferences("patrolgrid_missions", Context.MODE_PRIVATE)

    suspend fun snapshot(): PatrolGridSnapshot {
        val trackingActive = settings.get().activePatrolMissionId == PRIMARY_MISSION_ID
        val primary = primaryMission(trackingActive)
        return PatrolGridSnapshot(
            primaryMission = primary,
            activeMissions = listOf(primary, marketMission()),
            upcomingMission = upcomingMission(),
            recordedTrackPoints = trackDao.countForMission(PRIMARY_MISSION_ID),
            trackingActive = trackingActive,
            observationCount = prefs.getInt(KEY_OBSERVATIONS, 0),
        )
    }

    fun startPatrol(missionId: String = PRIMARY_MISSION_ID) {
        prefs.edit()
            .putBoolean(KEY_ENDED, false)
            .putBoolean(KEY_DEVIATION, false)
            .apply()
        settings.setActivePatrolMission(missionId)
        settings.setGpsEnabled(true)
    }

    fun markCurrentPriorityVisited(): String? {
        val visited = prefs.getInt(KEY_VISITED_PRIORITY, 0)
        if (visited >= PRIORITY_NAMES.size) return null
        val visitedName = PRIORITY_NAMES[visited]
        prefs.edit().putInt(KEY_VISITED_PRIORITY, visited + 1).apply()
        return visitedName
    }

    fun addObservation(): Int {
        val next = prefs.getInt(KEY_OBSERVATIONS, 0) + 1
        prefs.edit().putInt(KEY_OBSERVATIONS, next).apply()
        return next
    }

    fun recordDeviation() {
        prefs.edit().putBoolean(KEY_DEVIATION, true).apply()
    }

    fun endPatrol() {
        prefs.edit().putBoolean(KEY_ENDED, true).apply()
        settings.setActivePatrolMission(null)
        settings.setActivePatrolSession(null)
        settings.setGpsEnabled(false)
    }

    fun assignPatrol(draft: PatrolAssignmentDraft) {
        require(ROUTE_PLANS.any { it.id == draft.routePlanId }) { "Unknown patrol route" }
        require(UNIT_OPTIONS.any {
            it.name == draft.unitName && it.personnelCount == draft.personnelCount
        }) { "Unknown patrol unit" }
        prefs.edit()
            .putString(KEY_PENDING_ROUTE, draft.routePlanId)
            .putString(KEY_PENDING_UNIT, draft.unitName)
            .putInt(KEY_PENDING_PERSONNEL, draft.personnelCount)
            .putString(KEY_PENDING_GUIDANCE, draft.guidance.name)
            .apply()
    }

    private fun primaryMission(trackingActive: Boolean): PatrolMission {
        val ended = prefs.getBoolean(KEY_ENDED, false)
        val hasDeviation = prefs.getBoolean(KEY_DEVIATION, false)
        val visited = prefs.getInt(KEY_VISITED_PRIORITY, 0).coerceIn(0, PRIORITY_NAMES.size)
        val locations = PRIORITY_NAMES.mapIndexed { index, name ->
            val state = when {
                index < visited -> PriorityLocationState.VISITED
                index == visited && !ended -> PriorityLocationState.CURRENT
                else -> PriorityLocationState.REMAINING
            }
            PriorityLocation(
                id = "priority-${index + 1}",
                name = name,
                state = state,
                detail = when (state) {
                    PriorityLocationState.VISITED -> if (index == 0) "Visited at 22:18" else "Visited"
                    PriorityLocationState.CURRENT -> "Current"
                    PriorityLocationState.REMAINING -> "Remaining"
                },
            )
        }
        val status = when {
            trackingActive -> PatrolMissionStatus.ACTIVE
            ended && (visited < PRIORITY_NAMES.size || hasDeviation) -> PatrolMissionStatus.NEEDS_REVIEW
            ended -> PatrolMissionStatus.COMPLETED
            else -> PatrolMissionStatus.ASSIGNED
        }
        return PatrolMission(
            id = PRIMARY_MISSION_ID,
            title = "Night patrol · Sector 4",
            dutyWindow = "22:00–02:00",
            unitName = "Unit 12",
            personnelCount = 4,
            status = status,
            statusLabel = when (status) {
                PatrolMissionStatus.ACTIVE -> "On route"
                PatrolMissionStatus.NEEDS_REVIEW -> "Needs context"
                PatrolMissionStatus.COMPLETED -> "Ready for review"
                else -> "Assigned"
            },
            context = when {
                hasDeviation -> "Operational deviation recorded"
                status == PatrolMissionStatus.ACTIVE -> "Covering priority locations as planned"
                status == PatrolMissionStatus.COMPLETED -> "Patrol ended; evidence available"
                status == PatrolMissionStatus.NEEDS_REVIEW -> "Patrol ended with an incomplete priority location"
                else -> "Briefing ready; patrol has not started"
            },
            priorityLocations = locations,
            lastUpdateLabel = if (trackingActive) "Now" else "8m ago",
            hasOperationalDeviation = hasDeviation,
        )
    }

    private fun marketMission(): PatrolMission = PatrolMission(
        id = "market-corridor",
        title = "Foot patrol · Market corridor",
        dutyWindow = "20:00–00:00",
        unitName = "Unit 7",
        personnelCount = 3,
        status = PatrolMissionStatus.PAUSED_WITH_REASON,
        statusLabel = "Paused with reason",
        context = "Festival crowd managed",
        priorityLocations = emptyList(),
        lastUpdateLabel = "3m ago",
        hasOperationalDeviation = true,
    )

    private fun upcomingMission(): PatrolMission {
        val route = ROUTE_PLANS.firstOrNull {
            it.id == prefs.getString(KEY_PENDING_ROUTE, null)
        } ?: ROUTE_PLANS.first()
        val unit = prefs.getString(KEY_PENDING_UNIT, null)
        val personnelCount = prefs.getInt(KEY_PENDING_PERSONNEL, 0)
        val guidance = prefs.getString(KEY_PENDING_GUIDANCE, null)
            ?.let { runCatching { PatrolRouteGuidance.valueOf(it) }.getOrNull() }
            ?: PatrolRouteGuidance.SUGGESTED_ROUTE
        val assigned = unit != null && personnelCount > 0
        return PatrolMission(
            id = "upcoming-${route.id}",
            title = route.title,
            dutyWindow = route.dutyWindow,
            unitName = unit ?: "Unassigned",
            personnelCount = personnelCount,
            status = PatrolMissionStatus.ASSIGNED,
            statusLabel = if (assigned) "Assigned" else "Briefing pending",
            context = if (assigned) {
                "${guidance.label} · ${route.priorityLocations.size} priority locations"
            } else {
                "Choose a unit and route guidance"
            },
            priorityLocations = route.priorityLocations.mapIndexed { index, name ->
                PriorityLocation(
                    id = "${route.id}-${index + 1}",
                    name = name,
                    state = PriorityLocationState.REMAINING,
                    detail = "Planned",
                )
            },
            lastUpdateLabel = "Upcoming",
        )
    }

    companion object {
        const val PRIMARY_MISSION_ID = "night-sector-4"
        private const val KEY_VISITED_PRIORITY = "visited_priority"
        private const val KEY_OBSERVATIONS = "observation_count"
        private const val KEY_DEVIATION = "operational_deviation"
        private const val KEY_ENDED = "patrol_ended"
        private const val KEY_PENDING_ROUTE = "pending_route"
        private const val KEY_PENDING_UNIT = "pending_unit"
        private const val KEY_PENDING_PERSONNEL = "pending_personnel"
        private const val KEY_PENDING_GUIDANCE = "pending_guidance"
        private val PRIORITY_NAMES = listOf("Bus stand", "Market junction", "Canal road")

        val ROUTE_PLANS = listOf(
            PatrolRoutePlan(
                id = "school-corridor",
                title = "Day patrol · School corridor",
                dutyWindow = "06:30–09:30",
                priorityLocations = listOf("Government school", "East crossing", "Bus stop"),
            ),
            PatrolRoutePlan(
                id = "market-loop",
                title = "Evening patrol · Market loop",
                dutyWindow = "17:00–21:00",
                priorityLocations = listOf("Market junction", "Parking lane", "Transit stop"),
            ),
            PatrolRoutePlan(
                id = "night-sector-6",
                title = "Night patrol · Sector 6",
                dutyWindow = "22:00–02:00",
                priorityLocations = listOf("Industrial gate", "Canal bridge", "Fuel station"),
            ),
        )

        val UNIT_OPTIONS = listOf(
            PatrolUnitOption("Unit 7", 3),
            PatrolUnitOption("Unit 9", 3),
            PatrolUnitOption("Unit 12", 4),
        )
    }
}

private val PatrolRouteGuidance.label: String
    get() = when (this) {
        PatrolRouteGuidance.SUGGESTED_ROUTE -> "Suggested route"
        PatrolRouteGuidance.AREA_COVERAGE -> "Flexible area coverage"
    }
