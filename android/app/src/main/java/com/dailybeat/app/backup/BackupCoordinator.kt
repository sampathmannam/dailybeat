package com.dailybeat.app.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupCoordinator(
    private val localStore: SnapshotStore,
    private val remote: BackupRemote,
    private val cacheDir: java.io.File? = null,
) {
    private val archive: BackupArchive? = if (localStore is PagedSnapshotStore && remote is ArchiveBackupRemote && cacheDir != null)
        BackupArchive(localStore, remote, cacheDir) else null
    suspend fun versions(): Result<List<BackupVersion>> = runCatching { (remote as? ArchiveBackupRemote)?.versions().orEmpty() }

    val isConfigured: Boolean get() = remote.isConfigured
    fun currentSession(): BackupSession? = remote.currentSession()
    suspend fun signUp(email: String, password: String): Result<BackupSignUpResult> = remote.signUp(email, password)
    suspend fun signIn(email: String, password: String): Result<BackupSession> = remote.signIn(email, password)

    suspend fun backupNow(passphrase: CharArray): Result<Long> = runCatching {
        try {
            if (archive != null) return@runCatching withContext(Dispatchers.IO) { archive.backup(passphrase) }
            val snapshot = localStore.createSnapshot()
            val encrypted = withContext(Dispatchers.Default) {
                BackupEnvelope.seal(BackupSnapshotCodec.encode(snapshot), passphrase)
            }
            remote.upload(encrypted).getOrThrow()
            snapshot.createdAtMs
        } finally { passphrase.fill('\u0000') }
    }

    suspend fun restoreNow(passphrase: CharArray, versionId: String? = null): Result<String> = runCatching {
        try {
            if (archive != null && remote is ArchiveBackupRemote) {
                val versions = remote.versions()
                val version = if (versionId == null) versions.firstOrNull() else versions.firstOrNull { it.id == versionId }
                    ?: throw IllegalStateException("Selected backup is no longer available. Refresh backup history.")
                if (version != null) return@runCatching withContext(Dispatchers.IO) { archive.restore(passphrase, version) }
            }
            val backup = remote.download().getOrThrow()
                ?: throw IllegalStateException("No encrypted backup exists. Use legacy restore only for older backups.")
            val snapshot = withContext(Dispatchers.Default) {
                BackupSnapshotCodec.decode(BackupEnvelope.open(backup.snapshotJson, passphrase))
            }
            localStore.restore(snapshot)
            backup.updatedAt
        } finally { passphrase.fill('\u0000') }
    }

    /** Explicit legacy recovery only. Never fall back after a failed authentication/tag check. */
    suspend fun restoreLegacyNow(): Result<String> = runCatching {
        val backup = remote.downloadLegacy().getOrThrow()
            ?: throw IllegalStateException("No legacy backup exists for this account.")
        val snapshot = BackupSnapshotCodec.decode(backup.snapshotJson)
        localStore.restore(snapshot)
        backup.updatedAt
    }
    suspend fun deleteCloudData(): Result<Unit> = remote.deleteCloudData()
    suspend fun deleteAccount(): Result<Unit> = remote.deleteAccount()
    fun signOut() { remote.signOut() }
}
