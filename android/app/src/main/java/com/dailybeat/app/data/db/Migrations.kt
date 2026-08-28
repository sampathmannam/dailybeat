package com.dailybeat.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_events_timestamp ON events(timestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_diaries_dateKey ON diaries(dateKey)")
    }
}
