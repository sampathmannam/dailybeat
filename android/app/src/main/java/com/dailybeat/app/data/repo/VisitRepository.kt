package com.dailybeat.app.data.repo

import com.dailybeat.app.data.db.VisitDao
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.VisitCorrection
import androidx.room.withTransaction
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.util.DateKeys
import com.dailybeat.app.util.DayBounds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

class VisitRepository(
    private val visitDao: VisitDao,
    private val db: DailyBeatDb? = null,
) {

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

    fun observeLastDays(days: Int, endDate: LocalDate, zoneId: ZoneId): Flow<List<LocationVisit>> {
        require(days in 1..36500)
        val (start, _) = DayBounds.dayStartEnd(endDate.minusDays(days.toLong() - 1), zoneId)
        val (_, end) = DayBounds.dayStartEnd(endDate, zoneId)
        return visitDao.observeBetween(start, end)
    }

    /** Hidden history also supplies labels that must not leak through later notes/rollups. */
    suspend fun outboundVisitsForDate(date: LocalDate): List<LocationVisit> =
        (visitsForDate(date) + visitDao.hiddenEntries()).distinctBy { it.id }

    suspend fun insert(visit: LocationVisit) = visitDao.insert(visit)

    suspend fun update(visit: LocationVisit) = visitDao.update(visit)

    suspend fun rename(visit: LocationVisit, name: String) {
        inTransaction {
            check(visitDao.rename(visit.id, visit.placeName, name) == 1) {
                "This stop changed while you were reviewing it. Reopen it and try again."
            }
            visitDao.insertCorrection(
                VisitCorrection(
                    visitId = visit.id,
                    correctedAt = System.currentTimeMillis(),
                    field = "placeName",
                    oldValue = visit.placeName,
                    newValue = name,
                ),
            )
        }
    }

    suspend fun setHidden(visit: LocationVisit, hidden: Boolean) {
        inTransaction {
            check(visitDao.setHidden(visit.id, visit.hidden, hidden) == 1) {
                "This stop changed while you were reviewing it. Reopen it and try again."
            }
            visitDao.insertCorrection(
                VisitCorrection(
                    visitId = visit.id,
                    correctedAt = System.currentTimeMillis(),
                    field = "hidden",
                    oldValue = visit.hidden.toString(),
                    newValue = hidden.toString(),
                ),
            )
        }
    }

    private suspend fun <T> inTransaction(block: suspend () -> T): T =
        db?.withTransaction { block() } ?: block()

    private fun LocationVisit.clippedTo(dayStart: Long, dayEnd: Long): LocationVisit = copy(
        startMs = maxOf(startMs, dayStart),
        endMs = minOf(endMs, dayEnd),
    )
}
