package com.dailybeat.app.data.repo

import com.dailybeat.app.data.db.BreadcrumbDao
import com.dailybeat.app.data.model.LocationBreadcrumb
import com.dailybeat.app.util.DateKeys
import com.dailybeat.app.util.DayBounds
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class BreadcrumbRepository(private val dao: BreadcrumbDao) {
    fun observeToday(): Flow<List<LocationBreadcrumb>> = observeForDate(DateKeys.today())

    fun observeForDate(date: LocalDate): Flow<List<LocationBreadcrumb>> {
        val (start, end) = DayBounds.dayStartEnd(date)
        return dao.observeBetween(start, end)
    }

    suspend fun forDate(date: LocalDate): List<LocationBreadcrumb> {
        val (start, end) = DayBounds.dayStartEnd(date)
        return dao.between(start, end)
    }

    suspend fun all(): List<LocationBreadcrumb> = dao.all()
    suspend fun latest(): LocationBreadcrumb? = dao.latest()
    suspend fun insert(point: LocationBreadcrumb): Long = dao.insert(point)
}
