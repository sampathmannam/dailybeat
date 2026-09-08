package com.dailybeat.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dailybeat.app.data.model.DiaryEntry
import com.dailybeat.app.data.model.DsrCase
import com.dailybeat.app.data.model.DsrCaseSnapshot
import com.dailybeat.app.data.model.DsrCaseWork
import com.dailybeat.app.data.model.DsrProcedureCheck
import com.dailybeat.app.data.model.DsrCaseAudit
import com.dailybeat.app.data.model.DsrCaseMention
import com.dailybeat.app.data.model.DsrForecast
import com.dailybeat.app.data.model.DsrImport
import com.dailybeat.app.data.model.DsrMetricSnapshot
import com.dailybeat.app.data.model.DsrQualityIssue
import com.dailybeat.app.data.model.DsrStationSnapshot
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.GeocodeCache
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.Place

// DSR entities are dormant compatibility storage only. Keep their tables and migrations
// so removing the feature never deletes previously imported documents or review records.
// Active DSR development lives in the independent private sampathmannam/dsr repository.
@Database(
    entities = [
        Event::class,
        Place::class,
        DiaryEntry::class,
        LocationVisit::class,
        GeocodeCache::class,
        DsrImport::class,
        DsrCase::class,
        DsrCaseMention::class,
        DsrStationSnapshot::class,
        DsrMetricSnapshot::class,
        DsrForecast::class,
        DsrQualityIssue::class,
        DsrCaseSnapshot::class,
        DsrCaseWork::class,
        DsrProcedureCheck::class,
        DsrCaseAudit::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class DailyBeatDb : RoomDatabase() {
    abstract fun events(): EventDao
    abstract fun places(): PlaceDao
    abstract fun diaries(): DiaryDao
    abstract fun visits(): VisitDao
    abstract fun geocodes(): GeocodeDao
    abstract fun dsr(): DsrDao
}
