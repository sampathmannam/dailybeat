package com.dailybeat.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dailybeat.app.data.model.DiaryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class CloudBackupLiveTest {

    @Test
    fun phoneBackupAndRestoreRoundTrip() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val email = arguments.getString("backupEmail").orEmpty()
        val password = arguments.getString("backupPassword").orEmpty()
        assumeTrue("Live backup credentials were not provided.", email.isNotBlank() && password.isNotBlank())
        assertEquals(arguments.getString("backupEmailSha"), sha256(email))
        assertEquals(arguments.getString("backupPasswordSha"), sha256(password))
        assertEquals(
            arguments.getString("backupConfigSha"),
            sha256("${BuildConfig.SUPABASE_URL}|${BuildConfig.SUPABASE_ANON_KEY}"),
        )

        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        assumeTrue("This build has no Supabase configuration.", app.backupCoordinator.isConfigured)

        try {
            withContext(Dispatchers.IO) {
                app.db.clearAllTables()
                app.eventRepository.addManualEvent("Live cloud backup round trip")
                app.db.diaries().upsert(
                    DiaryEntry("2026-08-31", "Live cloud backup diary", System.currentTimeMillis()),
                )
            }
            app.backupCoordinator.signIn(email, password).getOrThrow()
            app.backupCoordinator.backupNow().getOrThrow()

            withContext(Dispatchers.IO) { app.db.clearAllTables() }
            assertEquals(0, withContext(Dispatchers.IO) { app.db.events().all().size })

            app.backupCoordinator.restoreNow().getOrThrow()
            val restored = withContext(Dispatchers.IO) { app.db.events().all() }
            assertEquals(listOf("Live cloud backup round trip"), restored.map { it.rawText })
            assertEquals(
                "Live cloud backup diary",
                withContext(Dispatchers.IO) { app.db.diaries().forDate("2026-08-31")?.text },
            )
        } finally {
            app.backupCoordinator.signOut()
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
