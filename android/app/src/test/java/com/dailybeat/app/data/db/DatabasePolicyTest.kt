package com.dailybeat.app.data.db

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class DatabasePolicyTest {

    @Test
    fun productionDatabaseNeverSilentlyDeletesUserDataForMissingMigration() {
        val application = File("src/main/java/com/dailybeat/app/DailyBeatApp.kt").readText()

        assertFalse("fallbackToDestructiveMigration" in application)
    }
}
