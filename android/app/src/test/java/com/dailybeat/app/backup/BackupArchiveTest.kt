package com.dailybeat.app.backup

import com.dailybeat.app.data.model.LocationBreadcrumb
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupArchiveTest {
    private val passphrase get() = "six random words for isolated backup testing".toCharArray()
    private val directory = Files.createTempDirectory("backup-archive-test").toFile()
    @After fun cleanup() { directory.deleteRecursively() }
    private class Store(private val count: Int = 0) : PagedSnapshotStore {
        var restored = -1
        override suspend fun createSnapshot(): BackupSnapshot = error("Full snapshot must not be loaded")
        override suspend fun restore(snapshot: BackupSnapshot) = error("Full restore must not be loaded")
        override suspend fun forEachPage(emit: suspend (BackupSnapshot) -> Unit) {
            emit(BackupSnapshot.empty(1234))
            for (start in 0 until count step 1000) {
                emit(BackupSnapshot.empty(1234).copy(breadcrumbs = (start until minOf(start+1000,count)).map {
                    LocationBreadcrumb(id=it+1L,timestampMs=1_700_000_000_000L+it*45_000L,
                        latitude=11.4557+it%5000*0.000001,longitude=78.1856,accuracyM=20f)
                }))
            }
        }
        override suspend fun restorePages(pages: Sequence<BackupSnapshot>) {
            restored = pages.sumOf { it.breadcrumbs.size }
        }
    }
    private class Remote : ArchiveBackupRemote {
        val parts = mutableMapOf<Pair<String,Int>,String>()
        val published = mutableListOf<BackupVersion>()
        var failUpload = false
        override val isConfigured = true
        override fun currentSession(): BackupSession? = null
        override suspend fun signUp(email:String,password:String):Result<BackupSignUpResult> = error("unused")
        override suspend fun signIn(email:String,password:String):Result<BackupSession> = error("unused")
        override suspend fun upload(snapshotJson:String):Result<Unit> = error("unused")
        override suspend fun download():Result<RemoteBackup?> = error("unused")
        override fun signOut() = Unit
        override suspend fun beginVersion(id:String) = Unit
        override suspend fun uploadPart(id:String,index:Int,payload:String) { if(failUpload) error("offline"); parts[id to index]=payload }
        override suspend fun publishVersion(id:String,manifest:String,parts:Int) { published.add(0,BackupVersion(id,"now",manifest)) }
        override suspend fun versions() = published.toList()
        override suspend fun downloadPart(id:String,index:Int) = parts.getValue(id to index)
    }
    @Test fun `eighty thousand route points round trip without a whole snapshot or plaintext file`() = runBlocking {
        val store = Store(80_000); val remote = Remote(); val archive = BackupArchive(store,remote,directory)
        archive.backup(passphrase)
        assertTrue(remote.parts.size > 80)
        assertTrue(remote.parts.values.all { !it.contains("latitude") && it.length < 1024*1024 })
        archive.restore(passphrase,remote.versions().first())
        assertEquals(80_000,store.restored)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }
    @Test fun `failed replacement leaves prior version restorable`() = runBlocking {
        val store = Store(2000); val remote = Remote(); val archive = BackupArchive(store,remote,directory)
        archive.backup(passphrase); remote.failUpload = true
        assertTrue(runCatching { archive.backup(passphrase) }.isFailure)
        assertEquals(1,remote.versions().size)
        archive.restore(passphrase,remote.versions().first())
        assertEquals(2000,store.restored)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }
    @Test fun `wrong passphrase or swapped page cannot touch local data`() = runBlocking {
        val store = Store(2000); val remote = Remote(); val archive = BackupArchive(store,remote,directory)
        archive.backup(passphrase)
        val version = remote.versions().first()
        assertTrue(runCatching { archive.restore("a different sufficiently long secret".toCharArray(),version) }.isFailure)
        assertEquals(-1,store.restored)
        remote.parts[version.id to 0] = remote.parts.getValue(version.id to 1)
        assertTrue(runCatching { archive.restore(passphrase,version) }.isFailure)
        assertEquals(-1,store.restored)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }
    @Test fun `authentication binds version id and page index and bounds expansion`() {
        val id = UUID.randomUUID().toString()
        ArchiveCipher(passphrase).use { crypto ->
            val payload = crypto.seal("private text",id,0)
            assertTrue(runCatching { crypto.open(payload,id,1) }.isFailure)
            assertTrue(runCatching { crypto.open(payload,UUID.randomUUID().toString(),0) }.isFailure)
            assertTrue(runCatching { crypto.seal("x".repeat(ArchiveCipher.MAX_PLAIN+1),id,0) }.isFailure)
        }
    }
}
