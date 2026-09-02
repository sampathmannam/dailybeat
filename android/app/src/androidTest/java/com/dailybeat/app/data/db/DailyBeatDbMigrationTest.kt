package com.dailybeat.app.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dailybeat.app.security.PatrolCoordinates
import com.dailybeat.app.security.PatrolTrackCipher
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyBeatDbMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DailyBeatDb::class.java,
    )

    @Test
    fun version2To3MatchesExportedSchemaAndPreservesData() {
        helper.createDatabase("migration-v2-to-v3.db", 2).apply {
            execSQL(
                """
                INSERT INTO diaries (dateKey, text, updatedAt)
                VALUES ('2026-08-31', 'Pre-index diary', 1724999000)
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            "migration-v2-to-v3.db",
            3,
            true,
            MIGRATION_2_3,
        )

        assertEquals(
            "Pre-index diary",
            db.singleString("SELECT text FROM diaries WHERE dateKey = '2026-08-31'"),
        )
        assertTrue(db.hasIndex("events", "index_events_timestamp"))
        assertFalse(db.hasIndex("diaries", "index_diaries_dateKey"))
    }

    @Test
    fun version3To4CreatesJourneyTablesAndPreservesEvents() {
        helper.createDatabase("migration-v3-to-v4.db", 3).apply {
            execSQL(
                """
                INSERT INTO events (id, timestamp, type, rawText)
                VALUES (12, 1724999500, 'manual', 'Pre-journey event')
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            "migration-v3-to-v4.db",
            4,
            true,
            MIGRATION_3_4,
        )

        assertEquals(
            "Pre-journey event",
            db.singleString("SELECT rawText FROM events WHERE id = 12"),
        )
        assertTrue("location_visits" in db.userTableNames())
        assertTrue("geocode_cache" in db.userTableNames())
    }

    @Test
    fun version2To7PreservesLegacyRecordsAndCreatesCurrentSchema() {
        helper.createDatabase("migration-v2-to-v7.db", 2).apply {
            execSQL(
                """
                INSERT INTO events (
                    id, timestamp, type, rawText, placeName, latitude, longitude,
                    peopleMentioned, caseNumbers, sourceId
                ) VALUES (7, 1725000000, 'manual', 'Night patrol started', 'Central Junction',
                    12.9716, 77.5946, 'Unit 3', 'CR-42', 'legacy-source')
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO places (id, name, latitude, longitude, radiusM)
                VALUES (4, 'Central Junction', 12.9716, 77.5946, 125)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO diaries (dateKey, text, updatedAt)
                VALUES ('2026-09-01', 'Legacy diary entry', 1725000123)
                """.trimIndent(),
            )
            // Older builds created this index during 2 -> 3 even though the
            // Room entity did not declare it. Keep that real-world state in
            // the fixture so the final migration's compatibility cleanup runs.
            execSQL("CREATE INDEX index_diaries_dateKey ON diaries(dateKey)")
            close()
        }

        val db = helper.runMigrationsAndValidate(
            "migration-v2-to-v7.db",
            7,
            true,
            *allMigrations(),
        )

        db.query(
            """
            SELECT timestamp, type, rawText, placeName, latitude, longitude,
                peopleMentioned, caseNumbers, sourceId
            FROM events WHERE id = 7
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1725000000L, cursor.getLong(0))
            assertEquals("manual", cursor.getString(1))
            assertEquals("Night patrol started", cursor.getString(2))
            assertEquals("Central Junction", cursor.getString(3))
            assertEquals(12.9716, cursor.getDouble(4), 0.000001)
            assertEquals(77.5946, cursor.getDouble(5), 0.000001)
            assertEquals("Unit 3", cursor.getString(6))
            assertEquals("CR-42", cursor.getString(7))
            assertEquals("legacy-source", cursor.getString(8))
        }
        assertEquals("Central Junction", db.singleString("SELECT name FROM places WHERE id = 4"))
        assertEquals(
            "Legacy diary entry",
            db.singleString("SELECT text FROM diaries WHERE dateKey = '2026-09-01'"),
        )
        assertEquals(7L, db.singleLong("PRAGMA user_version"))
        assertTrue("location_visits" in db.userTableNames())
        assertTrue("geocode_cache" in db.userTableNames())
        assertTrue("patrol_track_points" in db.userTableNames())
        assertFalse(db.hasIndex("diaries", "index_diaries_dateKey"))
    }

    @Test
    fun version4To5PreservesJourneyDataAndCreatesPlaintextTrackTable() {
        helper.createDatabase("migration-v4-to-v5.db", 4).apply {
            execSQL(
                """
                INSERT INTO location_visits (
                    id, startMs, endMs, latitude, longitude, placeName, address, visitType
                ) VALUES (9, 1000, 2000, 13.0827, 80.2707, 'Patrol Point',
                    'Sector 5', 'dwell')
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO geocode_cache (`key`, displayName, fetchedAt)
                VALUES ('13.0827,80.2707', 'Patrol Point, Sector 5', 1500)
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            "migration-v4-to-v5.db",
            5,
            true,
            MIGRATION_4_5,
        )

        db.query(
            "SELECT startMs, endMs, latitude, longitude, placeName, address, visitType " +
                "FROM location_visits WHERE id = 9",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1000L, cursor.getLong(0))
            assertEquals(2000L, cursor.getLong(1))
            assertEquals(13.0827, cursor.getDouble(2), 0.000001)
            assertEquals(80.2707, cursor.getDouble(3), 0.000001)
            assertEquals("Patrol Point", cursor.getString(4))
            assertEquals("Sector 5", cursor.getString(5))
            assertEquals("dwell", cursor.getString(6))
        }
        assertEquals(
            "Patrol Point, Sector 5",
            db.singleString("SELECT displayName FROM geocode_cache WHERE `key` = '13.0827,80.2707'"),
        )
        val trackColumns = db.columnNames("patrol_track_points")
        assertTrue("latitude" in trackColumns)
        assertTrue("longitude" in trackColumns)
        assertTrue("accuracyM" in trackColumns)
        assertFalse("encryptedPayload" in trackColumns)
    }

    @Test
    fun version5To6EncryptsExistingCoordinatesAndRemovesPlaintext() {
        helper.createDatabase("migration-v5-to-v6.db", 5).apply {
            execSQL(
                """
                INSERT INTO patrol_track_points (
                    id, missionId, timestampMs, latitude, longitude, accuracyM
                ) VALUES (31, 'mission-5', 1725000456, 12.9715987, 77.594566, 5.5)
                """.trimIndent(),
            )
            close()
        }

        val cipher = cipher()
        val db = helper.runMigrationsAndValidate(
            "migration-v5-to-v6.db",
            6,
            true,
            migration5To6(cipher),
        )

        db.query(
            """
            SELECT id, missionId, timestampMs, encryptedPayload
            FROM patrol_track_points WHERE id = 31
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(31L, cursor.getLong(0))
            assertEquals("mission-5", cursor.getString(1))
            assertEquals(1725000456L, cursor.getLong(2))
            val decoded = cipher.decrypt("mission-5", 1725000456L, cursor.getBlob(3))
            assertEquals(12.9715987, decoded.latitude, 0.0000001)
            assertEquals(77.594566, decoded.longitude, 0.0000001)
            assertEquals(5.5f, decoded.accuracyM, 0.0001f)
        }

        val columns = db.columnNames("patrol_track_points")
        assertTrue("encryptedPayload" in columns)
        assertFalse("sessionId" in columns)
        assertFalse("clientPointId" in columns)
        assertFalse("syncedAtMs" in columns)
        assertFalse("latitude" in columns)
        assertFalse("longitude" in columns)
        assertFalse("accuracyM" in columns)
    }

    @Test
    fun version6To7PreservesCiphertextAndEnforcesClientPointUniqueness() {
        val cipher = cipher()
        val originalPayload = cipher.encrypt(
            "mission-6",
            1725000789L,
            PatrolCoordinates(11.0168, 76.9558, 8.25f),
        )
        helper.createDatabase("migration-v6-to-v7.db", 6).apply {
            execSQL(
                """
                INSERT INTO patrol_track_points (id, missionId, timestampMs, encryptedPayload)
                VALUES (?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(44L, "mission-6", 1725000789L, originalPayload),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            "migration-v6-to-v7.db",
            7,
            true,
            MIGRATION_6_7,
        )

        db.query(
            "SELECT encryptedPayload, sessionId, clientPointId, syncedAtMs " +
                "FROM patrol_track_points WHERE id = 44",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertArrayEquals(originalPayload, cursor.getBlob(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
        }

        db.execSQL(
            "UPDATE patrol_track_points SET clientPointId = 'point-44' WHERE id = 44",
        )
        assertThrows(SQLiteConstraintException::class.java) {
            db.execSQL(
                """
                INSERT INTO patrol_track_points (
                    missionId, timestampMs, encryptedPayload, clientPointId
                ) VALUES (?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>("mission-6", 1725000790L, originalPayload, "point-44"),
            )
        }
    }

    private fun allMigrations() = arrayOf(
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        migration5To6(cipher()),
        MIGRATION_6_7,
    )

    private fun cipher() = PatrolTrackCipher(
        InstrumentationRegistry.getInstrumentation().targetContext,
    )

    private fun SupportSQLiteDatabase.singleString(sql: String): String =
        query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun SupportSQLiteDatabase.singleLong(sql: String): Long =
        query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private fun SupportSQLiteDatabase.userTableNames(): Set<String> =
        query("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'android_%'")
            .use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }

    private fun SupportSQLiteDatabase.columnNames(table: String): Set<String> =
        query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }

    private fun SupportSQLiteDatabase.hasUniqueIndex(table: String, expectedName: String): Boolean =
        query("PRAGMA index_list(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val uniqueIndex = cursor.getColumnIndexOrThrow("unique")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == expectedName && cursor.getInt(uniqueIndex) == 1) {
                    found = true
                }
            }
            found
        }

    private fun SupportSQLiteDatabase.hasIndex(table: String, expectedName: String): Boolean =
        query("PRAGMA index_list(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == expectedName) found = true
            }
            found
        }
}
