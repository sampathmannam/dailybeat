package com.dailybeat.app.backup

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupCoordinatorTest {

    @Test
    fun `backup uploads encoded local snapshot`() = runBlocking {
        val local = FakeLocalStore(BackupSnapshot.empty(4_321L))
        val remote = FakeRemote()
        val coordinator = BackupCoordinator(local, remote)

        val result = coordinator.backupNow()

        assertEquals(4_321L, result.getOrThrow())
        assertTrue(remote.uploaded.orEmpty().contains("\"schemaVersion\":1"))
        assertFalse(remote.uploaded.orEmpty().contains("apiKey", ignoreCase = true))
    }

    @Test
    fun `restore validates payload before changing local data`() = runBlocking {
        val local = FakeLocalStore(BackupSnapshot.empty(1L))
        val remote = FakeRemote().apply {
            downloaded = RemoteBackup("not-json", "2026-08-31T01:00:00Z")
        }
        val coordinator = BackupCoordinator(local, remote)

        val result = coordinator.restoreNow()

        assertTrue(result.isFailure)
        assertEquals(null, local.restored)
    }

    @Test
    fun `restore applies valid remote snapshot and returns timestamp`() = runBlocking {
        val expected = BackupSnapshot.empty(9L)
        val local = FakeLocalStore(BackupSnapshot.empty(1L))
        val remote = FakeRemote().apply {
            downloaded = RemoteBackup(BackupSnapshotCodec.encode(expected), "2026-08-31T02:00:00Z")
        }
        val coordinator = BackupCoordinator(local, remote)

        val updatedAt = coordinator.restoreNow().getOrThrow()

        assertEquals("2026-08-31T02:00:00Z", updatedAt)
        assertEquals(expected, local.restored)
    }

    private class FakeLocalStore(private val snapshot: BackupSnapshot) : SnapshotStore {
        var restored: BackupSnapshot? = null

        override suspend fun createSnapshot(): BackupSnapshot = snapshot

        override suspend fun restore(snapshot: BackupSnapshot) {
            restored = snapshot
        }
    }

    private class FakeRemote : BackupRemote {
        var uploaded: String? = null
        var downloaded: RemoteBackup? = null

        override val isConfigured: Boolean = true
        override fun currentSession(): BackupSession? = BackupSession("user", "person@example.com", "a", "r", Long.MAX_VALUE)
        override suspend fun signUp(email: String, password: String): Result<BackupSignUpResult> =
            Result.success(BackupSignUpResult(currentSession(), false))
        override suspend fun signIn(email: String, password: String): Result<BackupSession> =
            Result.success(currentSession()!!)

        override suspend fun upload(snapshotJson: String): Result<Unit> {
            uploaded = snapshotJson
            return Result.success(Unit)
        }

        override suspend fun download(): Result<RemoteBackup?> = Result.success(downloaded)

        override fun signOut() = Unit
    }
}
