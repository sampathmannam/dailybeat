package com.dailybeat.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dailybeat.app.security.PatrolCoordinates
import com.dailybeat.app.security.PatrolTrackCipher

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_events_timestamp ON events(timestamp)")
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
            CREATE TABLE IF NOT EXISTS patrol_track_points (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                missionId TEXT NOT NULL,
                timestampMs INTEGER NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                accuracyM REAL NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_patrol_track_points_missionId_timestampMs " +
                "ON patrol_track_points(missionId, timestampMs)",
        )
    }
}

fun migration5To6(cipher: PatrolTrackCipher) = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE patrol_track_points_secure (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                missionId TEXT NOT NULL,
                timestampMs INTEGER NOT NULL,
                encryptedPayload BLOB NOT NULL
            )
            """.trimIndent(),
        )

        val insert = db.compileStatement(
            """
            INSERT INTO patrol_track_points_secure (id, missionId, timestampMs, encryptedPayload)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
        )
        db.query(
            "SELECT id, missionId, timestampMs, latitude, longitude, accuracyM FROM patrol_track_points",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val missionId = cursor.getString(1)
                val timestampMs = cursor.getLong(2)
                val encryptedPayload = cipher.encrypt(
                    missionId = missionId,
                    timestampMs = timestampMs,
                    coordinates = PatrolCoordinates(
                        latitude = cursor.getDouble(3),
                        longitude = cursor.getDouble(4),
                        accuracyM = cursor.getFloat(5),
                    ),
                )
                insert.clearBindings()
                insert.bindLong(1, id)
                insert.bindString(2, missionId)
                insert.bindLong(3, timestampMs)
                insert.bindBlob(4, encryptedPayload)
                insert.executeInsert()
            }
        }

        // DROP TABLE frees pages without overwriting them, so the plaintext latitude
        // and longitude of every historical patrol would stay recoverable by carving
        // the freelist out of dailybeat.db on a recovered device. secure_delete makes
        // SQLite zero the content it frees. It is set here rather than as a global
        // pragma because it costs write amplification on every later delete, and this
        // is the one place the database has ever held plaintext coordinates. VACUUM
        // would also reclaim the pages but cannot run inside the transaction Room
        // wraps a migration in.
        // Run through query(), not execSQL(): a PRAGMA that sets a value also returns
        // it, and execSQL rejects any statement producing a result row with
        // "Queries can be performed using SQLiteDatabase query or rawQuery methods only".
        db.query("PRAGMA secure_delete = ON").use { it.moveToFirst() }
        db.execSQL("DROP TABLE patrol_track_points")
        db.execSQL("ALTER TABLE patrol_track_points_secure RENAME TO patrol_track_points")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_patrol_track_points_missionId_timestampMs " +
                "ON patrol_track_points(missionId, timestampMs)",
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // A historical 2 -> 3 migration created this redundant index even
        // though DiaryEntry never declared it. Remove it before Room validates
        // the current schema so legacy databases can open without data loss.
        db.execSQL("DROP INDEX IF EXISTS index_diaries_dateKey")
        db.execSQL("ALTER TABLE patrol_track_points ADD COLUMN sessionId TEXT")
        db.execSQL("ALTER TABLE patrol_track_points ADD COLUMN clientPointId TEXT")
        db.execSQL("ALTER TABLE patrol_track_points ADD COLUMN syncedAtMs INTEGER")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_patrol_track_points_clientPointId " +
                "ON patrol_track_points(clientPointId)",
        )
    }
}
