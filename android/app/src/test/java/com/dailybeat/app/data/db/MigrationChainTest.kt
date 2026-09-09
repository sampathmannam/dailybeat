package com.dailybeat.app.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * DailyBeat is updated by sideloading a new APK over the old one, so an upgrade has exactly one
 * chance to migrate the officer's diary. A version bump without a matching migration would crash
 * every existing install on launch, and the only way out would be uninstalling, which destroys
 * the record. These guards fail the build instead.
 */
class MigrationChainTest {

    // Room's @Database annotation is not retained at runtime, so read the declared version
    // from the source of truth itself.
    private val declaredVersion: Int = Regex("""version\s*=\s*(\d+)""")
        .find(File("src/main/java/com/dailybeat/app/data/db/DailyBeatDb.kt").readText())
        ?.groupValues?.get(1)?.toInt()
        ?: error("Could not read the declared database version from DailyBeatDb.kt")

    @Test
    fun `every schema step from the oldest shipped version has a migration`() {
        val steps = DailyBeatMigrations.ALL
            .map { it.startVersion to it.endVersion }
            .sortedBy { it.first }

        val expected = (DailyBeatMigrations.OLDEST_SHIPPED_VERSION until declaredVersion)
            .map { it to it + 1 }

        assertEquals(
            "The migration chain has a gap. Every version step from " +
                "${DailyBeatMigrations.OLDEST_SHIPPED_VERSION} to $declaredVersion needs a migration, " +
                "or upgrading users will crash on launch and lose their diary.",
            expected,
            steps,
        )
    }

    @Test
    fun `the newest migration ends at the schema the app declares`() {
        val highest = DailyBeatMigrations.ALL.maxOf { it.endVersion }

        assertEquals(
            "The database version was bumped to $declaredVersion without adding a migration to it.",
            declaredVersion,
            highest,
        )
    }

    @Test
    fun `upgrades never fall back to wiping the database`() {
        val application = File("src/main/java/com/dailybeat/app/DailyBeatApp.kt").readText()

        assertTrue(
            "The app must register the full migration list.",
            "DailyBeatMigrations.ALL" in application,
        )
        assertTrue(
            "A destructive fallback would silently delete the officer's diary on upgrade.",
            "fallbackToDestructiveMigration" !in application,
        )
    }
}
