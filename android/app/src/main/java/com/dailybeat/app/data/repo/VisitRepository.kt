package com.dailybeat.app.data.repo

import com.dailybeat.app.data.db.VisitDao
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.util.DateKeys
import com.dailybeat.app.util.DayBounds
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class VisitRepository(private val visitDao: VisitDao) {

    fun observeTodayVisits(): Flow<List<LocationVisit>> =
        observeForDate(DateKeys.today())

    fun observeForDate(date: LocalDate): Flow<List<LocationVisit>> {
        val (start, end) = DayBounds.dayStartEnd(date)
        return visitDao.observeBetween(start, end)
    }

    suspend fun visitsForDate(date: LocalDate): List<LocationVisit> {
        val (start, end) = DayBounds.dayStartEnd(date)
        return visitDao.between(start, end)
    }

    suspend fun visitsLastDays(days: Int): List<LocationVisit> {
        val endDate = DateKeys.today()
        val startDate = endDate.minusDays((days - 1).toLong().coerceAtLeast(0))
        val (startMs, _) = DayBounds.dayStartEnd(startDate)
        val (_, endMs) = DayBounds.dayStartEnd(endDate)
        return visitDao.between(startMs, endMs)
    }

    suspend fun insert(visit: LocationVisit) = visitDao.insert(visit)
}
