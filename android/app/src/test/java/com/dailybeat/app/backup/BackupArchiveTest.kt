package com.dailybeat.app.backup

import com.dailybeat.app.data.model.LocationBreadcrumb
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.JSONObject
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
        var abandonAction: suspend () -> Unit = {}
        override val isConfigured = true
        override fun currentSession(): BackupSession? = null
        override suspend fun signUp(email:String,password:String):Result<BackupSignUpResult> = error("unused")
        override suspend fun signIn(email:String,password:String):Result<BackupSession> = error("unused")
        override suspend fun upload(snapshotJson:String):Result<Unit> = error("unused")
        override suspend fun download():Result<RemoteBackup?> = error("unused")
        override fun signOut() = Unit
        override suspend fun beginVersion(id:String) = Unit
        override suspend fun abandonVersion(id: String) = abandonAction()
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

    @Test fun `deeply nested manifest and page fail without touching local data`() = runBlocking {
        val store = Store(1)
        val remote = Remote()
        val archive = BackupArchive(store, remote, directory)
        archive.backup(passphrase)
        val version = remote.versions().first()
        val nested = "{\"unexpected\":" + "[".repeat(5_000) + "0" + "]".repeat(5_000) + "}"

        val manifestError = runCatching { archive.restore(passphrase, version.copy(manifest = nested)) }.exceptionOrNull()
        assertTrue(manifestError is IllegalArgumentException)
        assertEquals("Invalid backup archive.", manifestError?.message)
        remote.parts[version.id to 0] = nested
        val pageError = runCatching { archive.restore(passphrase, version) }.exceptionOrNull()
        assertTrue(pageError is IllegalArgumentException)
        assertEquals("Invalid backup page.", pageError?.message)
        assertEquals(-1, store.restored)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun `authenticated but deeply nested manifest content is rejected safely`() = runBlocking {
        val id = UUID.randomUUID().toString()
        val nested = "{\"unexpected\":" + "[".repeat(5_000) + "0" + "]".repeat(5_000) + "}"
        val manifest = ArchiveCipher(passphrase).use { crypto ->
            JSONObject().put("format", "dailybeat-archive").put("version", 1)
                .put("salt", crypto.saltText).put("sealed", JSONObject(crypto.seal(nested, id, -1))).toString()
        }
        val store = Store()
        val error = runCatching {
            BackupArchive(store, Remote(), directory).restore(passphrase, BackupVersion(id, "now", manifest))
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals("Invalid backup archive.", error?.message)
        assertEquals(-1, store.restored)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }
    @Test fun `maximum length escaped diary records are split without losing text`() = runBlocking {
        // JSON expands a control character to six bytes: four legal 500k-character
        // diary records must be split before encryption, including a single 3 MB row.
        val entries = (1..4).map { com.dailybeat.app.data.model.DiaryEntry("2026-01-0$it", "\u0001".repeat(500_000), 1234) }
        var restored = emptyList<com.dailybeat.app.data.model.DiaryEntry>()
        val store = object : PagedSnapshotStore {
            override suspend fun createSnapshot(): BackupSnapshot = error("whole snapshot")
            override suspend fun restore(snapshot: BackupSnapshot) = error("whole restore")
            override suspend fun forEachPage(emit: suspend (BackupSnapshot) -> Unit) {
                emit(BackupSnapshot.empty(1234).copy(diaries = entries))
            }
            override suspend fun restorePages(pages: Sequence<BackupSnapshot>) { restored = pages.flatMap { it.diaries }.toList() }
        }
        val remote = Remote()
        val archive = BackupArchive(store, remote, directory)
        archive.backup(passphrase)
        assertEquals(4, remote.parts.size)
        archive.restore(passphrase, remote.versions().first())
        assertEquals(entries, restored)
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

    @Test fun `poorly compressible legal diary pages split at the encrypted limit without losing records`() = runBlocking {
        // Legacy backups allow 500k-character diaries. Four such rows fit the plaintext page
        // cap, but their compressed/base64 envelope does not fit one remote 1 MiB part.
        val random = java.util.Random(24L)
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val entries = (1..4).map { day ->
            val text = CharArray(500_000) { alphabet[random.nextInt(alphabet.length)] }.concatToString()
            com.dailybeat.app.data.model.DiaryEntry("2026-02-0$day", text, 1234)
        }
        var restored = emptyList<com.dailybeat.app.data.model.DiaryEntry>()
        val page = BackupSnapshot.empty(1234).copy(diaries = entries)
        assertTrue(BackupSnapshotCodec.jsonByteSize(BackupSnapshotCodec.encode(page)) < ArchiveCipher.MAX_PLAIN)
        val store = object : PagedSnapshotStore {
            override suspend fun createSnapshot(): BackupSnapshot = error("whole snapshot")
            override suspend fun restore(snapshot: BackupSnapshot) = error("whole restore")
            override suspend fun forEachPage(emit: suspend (BackupSnapshot) -> Unit) = emit(page)
            override suspend fun restorePages(pages: Sequence<BackupSnapshot>) { restored = pages.flatMap { it.diaries }.toList() }
        }
        val remote = Remote()
        val archive = BackupArchive(store, remote, directory)

        archive.backup(passphrase)
        assertTrue(remote.parts.size > 1)
        assertTrue(remote.parts.values.all { ArchiveUploadSize.addPart(0L, it) <= ArchiveCipher.MAX_PART_BYTES })
        archive.restore(passphrase, remote.versions().first())

        assertEquals(entries, restored)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test fun `compact one MiB envelope is too large for PostgreSQL canonical storage`() {
        val nonce = "A".repeat(16)
        fun envelope(ciphertextLength: Int) = JSONObject().put("nonce", nonce)
            .put("ciphertext", "A".repeat(ciphertextLength)).toString()
        val atOldLimit = envelope(ArchiveCipher.MAX_PART_BYTES - 44)
        assertEquals(ArchiveCipher.MAX_PART_BYTES, atOldLimit.toByteArray(Charsets.UTF_8).size)
        assertThrows(IllegalArgumentException::class.java) { ArchiveUploadSize.addPart(0L, atOldLimit) }

        // Base64 length moves in groups of four. This is the largest representable
        // compact envelope whose canonical PostgreSQL form still fits one MiB.
        val fits = envelope(ArchiveCipher.MAX_PART_BYTES - 48)
        assertEquals(ArchiveCipher.MAX_PART_BYTES - 1L, ArchiveUploadSize.addPart(0L, fits))
    }

    @Test fun `archive quota includes canonical overhead for every part`() {
        val almostFullPart = JSONObject().put("nonce", "A".repeat(16))
            .put("ciphertext", "A".repeat(ArchiveCipher.MAX_PART_BYTES - 48)).toString()
        var storedBytes = 0L
        repeat(64) { storedBytes = ArchiveUploadSize.addPart(storedBytes, almostFullPart) }
        assertEquals(ArchiveCipher.MAX_ARCHIVE_BYTES - 64L, storedBytes)

        val lastPart = JSONObject().put("nonce", "A".repeat(16)).put("ciphertext", "A".repeat(48)).toString()
        assertTrue(64L * almostFullPart.length + lastPart.length < ArchiveCipher.MAX_ARCHIVE_BYTES)
        assertThrows(IllegalArgumentException::class.java) { ArchiveUploadSize.addPart(storedBytes, lastPart) }
    }

    @Test fun `upload accounting uses parsed ASCII fields and rejects unsupported envelope data`() {
        val escapedSlashes = """{ "ciphertext": "\/\/\/\/", "nonce": "AAAAAAAAAAAAAAAA" }"""
        assertEquals(51L, ArchiveUploadSize.addPart(0L, escapedSlashes))
        val unicode = JSONObject().put("nonce", "A".repeat(16)).put("ciphertext", "\u00e9").toString()
        assertThrows(IllegalArgumentException::class.java) { ArchiveUploadSize.addPart(0L, unicode) }
        val extra = JSONObject().put("nonce", "A".repeat(16)).put("ciphertext", "AAAA")
            .put("ignored", "cannot be omitted from the quota").toString()
        assertThrows(IllegalArgumentException::class.java) { ArchiveUploadSize.addPart(0L, extra) }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `failed upload cleanup cannot hold the operation open indefinitely`() = runTest {
        val store = Store()
        val remote = Remote().apply {
            failUpload = true
            abandonAction = { awaitCancellation() }
        }

        val result = runCatching { BackupArchive(store, remote, directory).backup(passphrase) }

        assertEquals("offline", result.exceptionOrNull()?.message)
        assertEquals(5_000L, currentTime)
        assertTrue(remote.published.isEmpty())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }
}
