package com.dailybeat.app

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailybeat.app.backup.*
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.model.LocationBreadcrumb
import com.dailybeat.app.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Android crypto + real Room recovery on a fresh database; no network or shared QA account. */
@RunWith(AndroidJUnit4::class)
class ArchiveRecoveryTest {
    @Test fun largeJournalRestoresOnFreshDatabaseAndTamperingPreservesLocalRecords() = runBlocking(Dispatchers.IO) {
        requireDisposableTestApp()
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        val preferenceNames = mutableSetOf<String>()
        val context = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val isolated = "archive-test-$name"
                preferenceNames += isolated
                return super.getSharedPreferences(isolated, mode)
            }
        }
        val settings = SettingsRepository(context).apply { setGpsEnabled(false); setHistoryRetentionDays(0) }
        val source = Room.inMemoryDatabaseBuilder(context, DailyBeatDb::class.java).build()
        val destination = Room.inMemoryDatabaseBuilder(context, DailyBeatDb::class.java).build()
        val staging = File(app.cacheDir, "archive-device-test")
        val passphrase = "isolated synthetic archive recovery on Android".toCharArray()
        val remote = MemoryArchiveRemote()
        try {
            repeat(80) { page ->
                source.breadcrumbs().insertAll((1..1000).map { index ->
                    val id = page * 1000L + index
                    LocationBreadcrumb(id = id, timestampMs = 1_700_000_000_000L + id * 45_000,
                        latitude = 11.45 + id % 1000 * 0.000001, longitude = 78.18, accuracyM = 20f)
                })
            }
            BackupArchive(LocalBackupStore(source, settings), remote, staging).backup(passphrase)
            val recovery = BackupArchive(LocalBackupStore(destination, settings), remote, staging)
            val version = remote.versions().single()
            assertTrue(runCatching { recovery.restore("wrong but sufficiently long passphrase".toCharArray(), version) }.isFailure)
            assertNull(destination.breadcrumbs().latest())
            recovery.restore(passphrase, version)
            assertEquals(80_000L, destination.breadcrumbs().latest()?.id)
            destination.openHelper.readableDatabase.query("SELECT COUNT(*) FROM location_breadcrumbs").use {
                assertTrue(it.moveToFirst()); assertEquals(80_000, it.getInt(0))
            }
            remote.parts[version.id to 0] = remote.parts.getValue(version.id to 1)
            assertTrue(runCatching { recovery.restore(passphrase, version) }.isFailure)
            assertEquals(80_000L, destination.breadcrumbs().latest()?.id)
            assertTrue(staging.listFiles().orEmpty().isEmpty())
        } finally {
            passphrase.fill('\u0000')
            source.close(); destination.close(); staging.deleteRecursively()
            preferenceNames.forEach(context::deleteSharedPreferences)
        }
    }
    private class MemoryArchiveRemote : ArchiveBackupRemote {
        val parts = mutableMapOf<Pair<String, Int>, String>()
        private val complete = mutableListOf<BackupVersion>()
        override val isConfigured = true
        override fun currentSession(): BackupSession? = null
        override suspend fun signUp(email: String, password: String): Result<BackupSignUpResult> = error("no network")
        override suspend fun signIn(email: String, password: String): Result<BackupSession> = error("no network")
        override suspend fun upload(snapshotJson: String): Result<Unit> = error("legacy path")
        override suspend fun download(): Result<RemoteBackup?> = error("legacy path")
        override fun signOut() = Unit
        override suspend fun beginVersion(id: String) = Unit
        override suspend fun uploadPart(id: String, index: Int, payload: String) { parts[id to index] = payload }
        override suspend fun publishVersion(id: String, manifest: String, parts: Int) { complete += BackupVersion(id, "synthetic", manifest) }
        override suspend fun versions(): List<BackupVersion> = complete.toList()
        override suspend fun downloadPart(id: String, index: Int): String = parts.getValue(id to index)
    }
}
