package com.dailybeat.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every migration the app ships, in order. The officer's diary is the record of their duty, so
 * an upgrade must never fall back to a destructive migration; [DailyBeatMigrations] is the single
 * list the app and its guard test both read.
 */
object DailyBeatMigrations {
    val ALL: Array<Migration> get() = arrayOf(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

    /** The oldest schema ever shipped to a user (app v1.0.0). */
    const val OLDEST_SHIPPED_VERSION = 2
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_events_timestamp ON events(timestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_diaries_dateKey ON diaries(dateKey)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE geocode_cache ADD COLUMN placeName TEXT")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS location_visits (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                startMs INTEGER NOT NULL,
                endMs INTEGER NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                placeName TEXT,
                address TEXT,
                visitType TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_location_visits_startMs ON location_visits(startMs)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS geocode_cache (
                `key` TEXT NOT NULL PRIMARY KEY,
                displayName TEXT NOT NULL,
                fetchedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}
