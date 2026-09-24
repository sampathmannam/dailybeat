package com.dailybeat.app.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.capture.CaptureStorageGate
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.settings.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRestoreGenerationTest {
    private lateinit var context: Context
    private lateinit var db: DailyBeatDb
    private lateinit var settings: SettingsRepository
    private lateinit var local: LocalBackupStore
    private val directory = Files.createTempDirectory("backup-restore-generation").toFile()
    private val passphrase get() = "harbour comet velvet cedar orbit lantern".toCharArray()

    @Before fun before() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, DailyBeatDb::class.java).build()
        settings = SettingsRepository(context)
        local = LocalBackupStore(db, settings) { 9_999L }
    }

    @After fun after() {
        db.close()
        context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE).edit().clear().commit()
        directory.deleteRecursively()
    }

    private fun snapshot(label: String) = BackupSnapshot.empty(9_999L).copy(
        events = listOf(Event(id = 1L, timestamp = 1_000L, type = "manual", rawText = label)),
        settings = BackupSettings(officerName = label, gpsCaptureEnabled = false),
    )

    private suspend fun eraseWhileLocked() {
        withContext(Dispatchers.IO) { db.clearAllTables() }
        settings.setOfficerName("Erased phone")
        CaptureStorageGate.invalidatePersonalData()
    }

    @Test fun delayedLegacyDownloadCannotRepopulateAnErasedPhone() = runBlocking {
        local.restore(snapshot("Original phone"))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val remote = Remote().apply {
            downloadAction = {
                started.complete(Unit)
                release.await()
                RemoteBackup(BackupSnapshotCodec.encode(snapshot("Old backup")), "old")
            }
        }
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            BackupCoordinator(local, remote).restoreLegacyNow()
        }
        try {
            withTimeout(20_000L) { started.await() }
            CaptureStorageGate.mutex.withLock { eraseWhileLocked() }
            val generation = CaptureStorageGate.dataGeneration.get()
            release.complete(Unit)

            assertTrue(withTimeout(20_000L) { pending.await() }.isFailure)
            assertTrue(db.events().all().isEmpty())
            assertEquals("Erased phone", settings.get().officerName)
            assertEquals(generation, CaptureStorageGate.dataGeneration.get())
        } finally { release.complete(Unit); pending.cancelAndJoin() }
    }

    @Test fun delayedV1DownloadCannotOverwriteANewerSuccessfulRestore() = runBlocking {
        local.restore(snapshot("Original phone"))
        val encrypted = BackupEnvelope.seal(BackupSnapshotCodec.encode(snapshot("Old backup")), passphrase)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val remote = Remote().apply {
            downloadAction = {
                started.complete(Unit)
                release.await()
                RemoteBackup(encrypted, "old")
            }
        }
        val recovery = passphrase
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            BackupCoordinator(local, remote).restoreNow(recovery)
        }
        try {
            withTimeout(20_000L) { started.await() }
            local.restore(snapshot("Newly restored"))
            val generation = CaptureStorageGate.dataGeneration.get()
            release.complete(Unit)

            assertTrue(withTimeout(20_000L) { pending.await() }.isFailure)
            assertEquals(listOf("Newly restored"), db.events().all().map { it.rawText })
            assertEquals("Newly restored", settings.get().officerName)
            assertEquals(generation, CaptureStorageGate.dataGeneration.get())
            assertTrue(recovery.all { it == '\u0000' })
        } finally { release.complete(Unit); pending.cancelAndJoin() }
    }

    @Test fun delayedArchivePageCannotOverwriteANewerSuccessfulRestore() = runBlocking {
        local.restore(snapshot("Archived history"))
        val remote = ArchiveRemote()
        BackupArchive(local, remote, directory).backup(passphrase)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        remote.beforePart = {
            started.complete(Unit)
            release.await()
        }
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            BackupCoordinator(local, remote, directory).restoreNow(passphrase)
        }
        try {
            withTimeout(20_000L) { started.await() }
            local.restore(snapshot("Newly restored"))
            val generation = CaptureStorageGate.dataGeneration.get()
            release.complete(Unit)

            assertTrue(withTimeout(20_000L) { pending.await() }.isFailure)
            assertEquals(listOf("Newly restored"), db.events().all().map { it.rawText })
            assertEquals("Newly restored", settings.get().officerName)
            assertEquals(generation, CaptureStorageGate.dataGeneration.get())
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally { release.complete(Unit); pending.cancelAndJoin() }
    }

    @Test fun directSnapshotRestoreWaitingForTheApplyLockCannotUndoErase() = runBlocking {
        val pending = CaptureStorageGate.mutex.withLock {
            val queued = async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { local.restore(snapshot("Queued old history")) }
            }
            assertFalse(queued.isCompleted)
            eraseWhileLocked()
            queued
        }
        val generation = CaptureStorageGate.dataGeneration.get()

        assertTrue(withTimeout(20_000L) { pending.await() }.isFailure)
        assertTrue(db.events().all().isEmpty())
        assertEquals("Erased phone", settings.get().officerName)
        assertEquals(generation, CaptureStorageGate.dataGeneration.get())
    }

    @Test fun directPagedRestoreWaitingForTheApplyLockCannotUndoReplacement() = runBlocking {
        val pending = CaptureStorageGate.mutex.withLock {
            val queued = async(start = CoroutineStart.UNDISPATCHED) {
                runCatching { local.restorePages(sequenceOf(snapshot("Queued old history"))) }
            }
            assertFalse(queued.isCompleted)
            db.events().insertAll(snapshot("Replacement history").events)
            settings.setOfficerName("Replacement history")
            CaptureStorageGate.invalidatePersonalData()
            queued
        }
        val generation = CaptureStorageGate.dataGeneration.get()

        assertTrue(withTimeout(20_000L) { pending.await() }.isFailure)
        assertEquals(listOf("Replacement history"), db.events().all().map { it.rawText })
        assertEquals("Replacement history", settings.get().officerName)
        assertEquals(generation, CaptureStorageGate.dataGeneration.get())

        // A fresh explicit restore remains possible; the old job alone was rejected.
        local.restorePages(sequenceOf(snapshot("Fresh restore")))
        assertEquals(listOf("Fresh restore"), db.events().all().map { it.rawText })
    }

    private open class Remote : BackupRemote {
        var downloadAction: suspend () -> RemoteBackup? = { null }
        override val isConfigured = true
        override fun currentSession(): BackupSession? = null
        override suspend fun signUp(email: String, password: String): Result<BackupSignUpResult> = error("unused")
        override suspend fun signIn(email: String, password: String): Result<BackupSession> = error("unused")
        override suspend fun upload(snapshotJson: String): Result<Unit> = error("unused")
        override suspend fun download(): Result<RemoteBackup?> = Result.success(downloadAction())
        override suspend fun downloadLegacy(): Result<RemoteBackup?> = Result.success(downloadAction())
        override fun signOut() = Unit
    }

    private class ArchiveRemote : Remote(), ArchiveBackupRemote {
        private val parts = mutableMapOf<Pair<String, Int>, String>()
        private val published = mutableListOf<BackupVersion>()
        var beforePart: suspend () -> Unit = {}
        override suspend fun beginVersion(id: String) = Unit
        override suspend fun uploadPart(id: String, index: Int, payload: String) { parts[id to index] = payload }
        override suspend fun publishVersion(id: String, manifest: String, parts: Int) {
            published += BackupVersion(id, "archive", manifest)
        }
        override suspend fun versions(): List<BackupVersion> = published.toList()
        override suspend fun downloadPart(id: String, index: Int): String {
            beforePart()
            return parts.getValue(id to index)
        }
    }
}
