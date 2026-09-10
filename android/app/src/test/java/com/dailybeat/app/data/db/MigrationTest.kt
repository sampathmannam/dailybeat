package com.dailybeat.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Exercises the real upgrade paths shipped to users:
 * app v1.0.x wrote schema 2, app v2.x wrote schema 3, app v3.x wrote schema 4, and
 * the two v3.6 development lines wrote different schema-5 shapes. The app now expects 8,
 * retaining dormant DSR records even though DSR's active feature has moved to a separate app.
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

    @Test
    fun migratesFromReleasedV36Schema5WithDsrAndWithoutGeocodePlaceName() {
        createSchema5Variant(keepDsrTables = true, keepGeocodePlaceName = false)

        val db = openWithProductionMigrations()
        try {
            assertEquals(listOf("Legacy diary text"), runBlocking { db.diaries().all() }.map { it.text })
            assertEquals(null, runBlocking { db.geocodes().get("11.4557,78.1856") }?.placeName)
            assertEquals(
                "legacy-dsr.pdf",
                runBlocking { db.dsr().importByHash("legacy-sha") }?.originalFileName,
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun migratesFromReliabilityQaSchema5WithGeocodePlaceNameAndWithoutDsr() {
        createSchema5Variant(keepDsrTables = false, keepGeocodePlaceName = true)

        val db = openWithProductionMigrations()
        try {
            assertEquals(listOf("Legacy diary text"), runBlocking { db.diaries().all() }.map { it.text })
            assertEquals(null, runBlocking { db.geocodes().get("11.4557,78.1856") }?.placeName)
            assertNull(runBlocking { db.dsr().importByHash("not-present") })
        } finally {
            db.close()
        }
    }

    @Test
    fun migratesSchema6WithoutLosingDsrCaseReferencesOrDiary() {
        createSchema5Variant(keepDsrTables = true, keepGeocodePlaceName = true)
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            // Early v3.x installs could already be at schema 3 before the diary index was
            // introduced, so they skipped MIGRATION_2_3 and reached schema 6 without it.
            raw.execSQL("DROP INDEX IF EXISTS index_diaries_dateKey")
            raw.execSQL("""
                INSERT INTO dsr_cases VALUES ('RASIPURAM|2099|1', 'RASIPURAM', 'Rasipuram', '1', 2099,
                    '1/2099', 'Test head', '194 BNSS', 'HIGH', '2099-01-01', '2099-01-02', 1)
            """.trimIndent())
            raw.execSQL("INSERT INTO dsr_case_mentions VALUES ('legacy-import|RASIPURAM|2099|1', 'legacy-import', 'RASIPURAM|2099|1', '2099-01-01', 3)")
            raw.version = 6
        }
        val db = openWithProductionMigrations()
        try {
            val snapshot = runBlocking { db.dsr().snapshotsForImport("legacy-import") }.single()
            assertEquals("Test head", snapshot.caseData.head)
            assertEquals(3, snapshot.sourcePage)
            assertEquals(true, snapshot.legacySnapshot)
            assertEquals(1, runBlocking { db.dsr().importById("legacy-import") }?.parserVersion)
            assertEquals(listOf("Legacy diary text"), runBlocking { db.diaries().all() }.map { it.text })
        } finally { db.close() }
    }

    /** Opens the database exactly the way [com.dailybeat.app.DailyBeatApp] does. */
    private fun openWithProductionMigrations(): DailyBeatDb =
        Room.databaseBuilder(context, DailyBeatDb::class.java, DB_NAME)
            .addMigrations(*DailyBeatMigrations.ALL)
            .build()
            .also { it.openHelper.writableDatabase }

    private fun createSchema5Variant(
        keepDsrTables: Boolean,
        keepGeocodePlaceName: Boolean,
    ) {
        createLegacyDatabase(version = 4, withIndices = true, withVisitTables = true)

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(5) {
                        override fun onCreate(db: SupportSQLiteDatabase) =
                            error("Expected the schema-4 fixture to exist")

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                            assertEquals(4, oldVersion)
                            assertEquals(5, newVersion)
                            MIGRATION_4_5.migrate(db)
                        }
                    },
                )
                .build(),
        )
        helper.writableDatabase
        helper.close()

        val raw = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE)
        if (!keepDsrTables) {
            DSR_TABLES.forEach { raw.execSQL("DROP TABLE IF EXISTS `$it`") }
        } else {
            raw.execSQL(
                "INSERT INTO dsr_imports (id, sha256, originalFileName, storedFileName, " +
                    "fileSizeBytes, reportType, reportDate, importedAt, pageCount, caseCount, " +
                    "forecastCount, issueCount, qualityScore, active, replacedImportId) VALUES " +
                    "('legacy-import', 'legacy-sha', 'legacy-dsr.pdf', 'legacy.pdf', 42, 'DSR', " +
                    "'2026-09-06', 1, 1, 0, 0, 0, 100, 1, NULL)",
            )
        }
        if (!keepGeocodePlaceName) {
            raw.execSQL("ALTER TABLE geocode_cache RENAME TO geocode_cache_complete")
            raw.execSQL(
                "CREATE TABLE geocode_cache (`key` TEXT NOT NULL, displayName TEXT NOT NULL, " +
                    "fetchedAt INTEGER NOT NULL, PRIMARY KEY(`key`))",
            )
            raw.execSQL(
                "INSERT INTO geocode_cache (`key`, displayName, fetchedAt) " +
                    "SELECT `key`, displayName, fetchedAt FROM geocode_cache_complete",
            )
            raw.execSQL("DROP TABLE geocode_cache_complete")
        }
        raw.version = 5
        raw.close()
    }

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
        val DSR_TABLES = listOf(
            "dsr_quality_issues",
            "dsr_forecasts",
            "dsr_metric_snapshots",
            "dsr_station_snapshots",
            "dsr_case_mentions",
            "dsr_cases",
            "dsr_imports",
        )
    }
}
