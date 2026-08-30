package com.dailybeat.app.backup

class BackupCoordinator(
    private val localStore: SnapshotStore,
    private val remote: BackupRemote,
) {
    val isConfigured: Boolean get() = remote.isConfigured

    fun currentSession(): BackupSession? = remote.currentSession()

    suspend fun signUp(email: String, password: String): Result<BackupSignUpResult> =
        remote.signUp(email, password)

    suspend fun signIn(email: String, password: String): Result<BackupSession> =
        remote.signIn(email, password)

    suspend fun backupNow(): Result<Long> = runCatching {
        val snapshot = localStore.createSnapshot()
        remote.upload(BackupSnapshotCodec.encode(snapshot)).getOrThrow()
        snapshot.createdAtMs
    }

    suspend fun restoreNow(): Result<String> = runCatching {
        val remoteBackup = remote.download().getOrThrow()
            ?: throw IllegalStateException("No cloud backup exists for this account.")
        val snapshot = BackupSnapshotCodec.decode(remoteBackup.snapshotJson)
        localStore.restore(snapshot)
        remoteBackup.updatedAt
    }

    fun signOut() {
        remote.signOut()
    }
}
