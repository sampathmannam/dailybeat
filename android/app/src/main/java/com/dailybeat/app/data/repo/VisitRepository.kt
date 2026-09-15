package com.dailybeat.app.data.repo

import com.dailybeat.app.data.db.VisitDao
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.util.DateKeys
import com.dailybeat.app.util.DayBounds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class VisitRepository(private val visitDao: VisitDao) {

    fun observeTodayVisits(): Flow<List<LocationVisit>> =
        observeForDate(DateKeys.today())

    fun observeForDate(date: LocalDate): Flow<List<LocationVisit>> {
        val (start, end) = DayBounds.dayStartEnd(date)
        return visitDao.observeBetween(start, end).map { visits ->
            visits.map { it.clippedTo(start, end) }
        }
    }

    suspend fun visitsForDate(date: LocalDate): List<LocationVisit> {
        val (start, end) = DayBounds.dayStartEnd(date)
        return visitDao.between(start, end).map { it.clippedTo(start, end) }
    }

    suspend fun visitsLastDays(days: Int): List<LocationVisit> {
        val endDate = DateKeys.today()
        val startDate = endDate.minusDays((days - 1).toLong().coerceAtLeast(0))
        val (startMs, _) = DayBounds.dayStartEnd(startDate)
        val (_, endMs) = DayBounds.dayStartEnd(endDate)
        return visitDao.between(startMs, endMs)
    }

    /** Hidden history also supplies labels that must not leak through later notes/rollups. */
    suspend fun outboundVisitsForDate(date: LocalDate): List<LocationVisit> =
        (visitsForDate(date) + visitDao.hiddenEntries()).distinctBy { it.id }

    suspend fun insert(visit: LocationVisit) = visitDao.insert(visit)

    suspend fun update(visit: LocationVisit) = visitDao.update(visit)

    suspend fun rename(visit: LocationVisit, name: String) {
        check(visitDao.rename(visit.id, visit.placeName, name) == 1) {
            "This stop changed while you were reviewing it. Reopen it and try again."
        }
    }

    suspend fun setHidden(visit: LocationVisit, hidden: Boolean) {
        check(visitDao.setHidden(visit.id, visit.hidden, hidden) == 1) {
            "This stop changed while you were reviewing it. Reopen it and try again."
        }
    }

    private fun LocationVisit.clippedTo(dayStart: Long, dayEnd: Long): LocationVisit = copy(
        startMs = maxOf(startMs, dayStart),
        endMs = minOf(endMs, dayEnd),
    )
}
