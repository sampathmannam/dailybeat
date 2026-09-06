package com.dailybeat.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Exercises the real upgrade paths shipped to users:
 * app v1.0.x wrote schema 2, app v2.x wrote schema 3, app v3.x wrote schema 4, current app expects schema 5.
 *
 * Each test builds the legacy database with the exact SQL Room generated for that
 * version, then opens it through the production [DailyBeatDb] builder so Room runs the
 * migrations and validates the resulting schema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private lateinit var context: Context
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath(DB_NAME)
        dbFile.parentFile?.mkdirs()
        deleteDatabaseFiles()
    }

    @After
    fun tearDown() {
        deleteDatabaseFiles()
    }

    @Test
    fun migratesFromSchema2WithUserDataIntact() {
        createLegacyDatabase(version = 2, withIndices = false)

        val db = openWithProductionMigrations()
        try {
            val (events, places, diaries) = runBlocking {
                Triple(db.events().all(), db.places().all(), db.diaries().all())
            }
            assertEquals(listOf("Briefing at HQ"), events.map { it.rawText })
            assertEquals(listOf("Headquarters"), places.map { it.name })
            assertEquals(listOf("2026-01-01"), diaries.map { it.dateKey })
            assertEquals(listOf("Legacy diary text"), diaries.map { it.text })

            // Tables added by MIGRATION_3_4 must be usable after the upgrade.
            assertEquals(emptyList<Long>(), runBlocking { db.visits().all().map { it.id } })
        } finally {
            db.close()
        }
    }

    @Test
    fun migratesFromSchema3WithUserDataIntact() {
        createLegacyDatabase(version = 3, withIndices = true)

        val db = openWithProductionMigrations()
        try {
            val diaries = runBlocking { db.diaries().all() }
            assertEquals(listOf("Legacy diary text"), diaries.map { it.text })
            assertEquals(listOf("Briefing at HQ"), runBlocking { db.events().all() }.map { it.rawText })
        } finally {
            db.close()
        }
    }

    @Test
    fun migratesFromSchema4WithUserDataIntact() {
        createLegacyDatabase(version = 4, withIndices = true, withVisitTables = true)

        val db = openWithProductionMigrations()
        try {
            assertEquals(listOf("Legacy diary text"), runBlocking { db.diaries().all() }.map { it.text })
            assertEquals(listOf("Rasipuram"), runBlocking { db.visits().all() }.map { it.placeName })
            // The column MIGRATION_4_5 adds must exist and read as null for pre-existing rows.
            assertEquals(null, runBlocking { db.geocodes().get("11.4557,78.1856") }?.placeName)
        } finally {
            db.close()
        }
    }

    /** Opens the database exactly the way [com.dailybeat.app.DailyBeatApp] does. */
    private fun openWithProductionMigrations(): DailyBeatDb =
        Room.databaseBuilder(context, DailyBeatDb::class.java, DB_NAME)
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
            .also { it.openHelper.writableDatabase }

    private fun createLegacyDatabase(
        version: Int,
        withIndices: Boolean,
        withVisitTables: Boolean = false,
    ) {
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, `type` TEXT NOT NULL, `rawText` TEXT NOT NULL, " +
                "`placeName` TEXT, `latitude` REAL, `longitude` REAL, `peopleMentioned` TEXT, " +
                "`caseNumbers` TEXT, `sourceId` TEXT)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `places` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, " +
                "`radiusM` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `diaries` (`dateKey` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`dateKey`))",
        )
        if (withIndices) {
            // Exactly what MIGRATION_2_3 leaves behind for a v2 -> v3 upgrader.
            db.execSQL("CREATE INDEX IF NOT EXISTS index_events_timestamp ON events(timestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_diaries_dateKey ON diaries(dateKey)")
        }

        if (withVisitTables) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `location_visits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, `latitude` REAL NOT NULL, " +
                    "`longitude` REAL NOT NULL, `placeName` TEXT, `address` TEXT, `visitType` TEXT NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_location_visits_startMs ON location_visits(startMs)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `geocode_cache` (`key` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                    "`fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))",
            )
            db.execSQL(
                "INSERT INTO location_visits (startMs, endMs, latitude, longitude, placeName, address, visitType) " +
                    "VALUES (1735689600000, 1735693200000, 11.4557, 78.1856, 'Rasipuram', 'Tamil Nadu', 'dwell')",
            )
            db.execSQL(
                "INSERT INTO geocode_cache (`key`, displayName, fetchedAt) " +
                    "VALUES ('11.4557,78.1856', 'Rasipuram, Tamil Nadu', 1735689600000)",
            )
        }

        db.execSQL(
            "INSERT INTO events (timestamp, type, rawText) VALUES (1735689600000, 'manual', 'Briefing at HQ')",
        )
        db.execSQL(
            "INSERT INTO places (name, latitude, longitude, radiusM) VALUES ('Headquarters', 12.97, 77.59, 100)",
        )
        db.execSQL(
            "INSERT INTO diaries (dateKey, text, updatedAt) VALUES ('2026-01-01', 'Legacy diary text', 1735689600000)",
        )
        db.version = version
        db.close()
    }

    private fun deleteDatabaseFiles() {
        listOf(dbFile, File("${dbFile.path}-wal"), File("${dbFile.path}-shm")).forEach { it.delete() }
    }

    private companion object {
        const val DB_NAME = "dailybeat-migration-test.db"
    }
}
