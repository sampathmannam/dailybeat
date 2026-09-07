package com.dailybeat.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_events_timestamp ON events(timestamp)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_diaries_dateKey ON diaries(dateKey)")
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

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS dsr_imports (
                id TEXT NOT NULL PRIMARY KEY,
                sha256 TEXT NOT NULL,
                originalFileName TEXT NOT NULL,
                storedFileName TEXT NOT NULL,
                fileSizeBytes INTEGER NOT NULL,
                reportType TEXT NOT NULL,
                reportDate TEXT,
                importedAt INTEGER NOT NULL,
                pageCount INTEGER NOT NULL,
                caseCount INTEGER NOT NULL,
                forecastCount INTEGER NOT NULL,
                issueCount INTEGER NOT NULL,
                qualityScore INTEGER NOT NULL,
                active INTEGER NOT NULL,
                replacedImportId TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_dsr_imports_sha256 ON dsr_imports(sha256)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_dsr_imports_reportDate_reportType_active " +
                "ON dsr_imports(reportDate, reportType, active)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS dsr_cases (
                caseKey TEXT NOT NULL PRIMARY KEY,
                stationCode TEXT NOT NULL,
                stationName TEXT NOT NULL,
                crimeNumber TEXT NOT NULL,
                crimeYear INTEGER NOT NULL,
                displayCrimeNumber TEXT NOT NULL,
                head TEXT NOT NULL,
                lawSections TEXT NOT NULL,
                priority TEXT NOT NULL,
                firstSeenDate TEXT NOT NULL,
                lastSeenDate TEXT NOT NULL,
                needsReview INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_dsr_cases_stationCode ON dsr_cases(stationCode)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_dsr_cases_lastSeenDate_priority ON dsr_cases(lastSeenDate, priority)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS dsr_case_mentions (
                mentionKey TEXT NOT NULL PRIMARY KEY,
                importId TEXT NOT NULL,
                caseKey TEXT NOT NULL,
                reportDate TEXT NOT NULL,
                sourcePage INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_dsr_case_mentions_importId ON dsr_case_mentions(importId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_dsr_case_mentions_caseKey ON dsr_case_mentions(caseKey)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS dsr_station_snapshots (
                id TEXT NOT NULL PRIMARY KEY,
                importId TEXT NOT NULL,
                reportDate TEXT NOT NULL,
                stationCode TEXT NOT NULL,
                stationName TEXT NOT NULL,
                reportedCases INTEGER,
                chargedCases INTEGER,
                otherDisposals INTEGER,
                eSummonsReceived INTEGER,
                eSummonsServed INTEGER,
                eSakshyaRecorded INTEGER,
                eSakshyaLinked INTEGER,
                mvDdCases INTEGER,
                mvOtherCases INTEGER,
                takenOnFile INTEGER,
                convictions INTEGER,
                acquittals INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_dsr_station_snapshots_importId ON dsr_station_snapshots(importId)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_dsr_station_snapshots_reportDate_stationCode " +
                "ON dsr_station_snapshots(reportDate, stationCode)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS dsr_metric_snapshots (
                id TEXT NOT NULL PRIMARY KEY,
                importId TEXT NOT NULL,
                reportDate TEXT,
                reportType TEXT NOT NULL,
                metricCode TEXT NOT NULL,
                metricValue INTEGER NOT NULL,
                semantics TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_dsr_metric_snapshots_importId ON dsr_metric_snapshots(importId)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_dsr_metric_snapshots_reportType_metricCode_reportDate " +
                "ON dsr_metric_snapshots(reportType, metricCode, reportDate)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS dsr_forecasts (
                id TEXT NOT NULL PRIMARY KEY,
                importId TEXT NOT NULL,
                reportDate TEXT NOT NULL,
                eventDate TEXT NOT NULL,
                stationCode TEXT NOT NULL,
                stationName TEXT NOT NULL,
                category TEXT NOT NULL,
                priority TEXT NOT NULL,
                expectedCrowd INTEGER,
                details TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_dsr_forecasts_importId ON dsr_forecasts(importId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_dsr_forecasts_eventDate_priority ON dsr_forecasts(eventDate, priority)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS dsr_quality_issues (
                id TEXT NOT NULL PRIMARY KEY,
                importId TEXT NOT NULL,
                severity TEXT NOT NULL,
                code TEXT NOT NULL,
                message TEXT NOT NULL,
                caseKey TEXT,
                sourcePage INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_dsr_quality_issues_importId ON dsr_quality_issues(importId)")
        db.execSQL("PRAGMA optimize")
    }
}
