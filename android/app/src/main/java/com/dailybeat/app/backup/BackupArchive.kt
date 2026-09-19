package com.dailybeat.app.backup

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

interface PagedSnapshotStore : SnapshotStore {
    suspend fun forEachPage(emit: suspend (BackupSnapshot) -> Unit)
    suspend fun restorePages(pages: Sequence<BackupSnapshot>)
}

data class BackupVersion(val id: String, val createdAt: String, val manifest: String)
interface ArchiveBackupRemote : BackupRemote {
    suspend fun abandonVersion(id: String) {}
    suspend fun beginVersion(id: String)
    suspend fun uploadPart(id: String, index: Int, payload: String)
    suspend fun publishVersion(id: String, manifest: String, parts: Int)
    suspend fun versions(): List<BackupVersion>
    suspend fun downloadPart(id: String, index: Int): String
}

/** One expensive password derivation per archive; unique nonce and version/index AAD per part. */
internal class ArchiveCipher(passphrase: CharArray, salt: ByteArray = ByteArray(16).also(SecureRandom()::nextBytes)) : Closeable {
    val saltText: String = Base64.getEncoder().encodeToString(salt)
    private val key: ByteArray
    init {
        require(passphrase.size in BackupEnvelope.MIN_PASSPHRASE_CHARS..BackupEnvelope.MAX_PASSPHRASE_CHARS) {
            "Use a recovery passphrase of 20–256 characters."
        }
        require(salt.size == 16) { "Invalid backup salt." }
        val spec = PBEKeySpec(passphrase, salt, 600_000, 256)
        key = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    private fun cipher(mode: Int, id: String, index: Int, nonce: ByteArray): Cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
        require(UUID.fromString(id).toString() == id && index in -1 until MAX_PARTS && nonce.size == 12)
        init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        updateAAD("dailybeat-archive:1:PBKDF2-SHA256:600000:gzip:$id:$index".toByteArray())
    }
    fun seal(text: String, id: String, index: Int): String {
        val plain = text.toByteArray(Charsets.UTF_8)
        require(plain.size <= MAX_PLAIN) { "A backup page is too large." }
        val compressed = try { ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(plain) } }.toByteArray() }
        finally { plain.fill(0) }
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val encrypted = try { cipher(Cipher.ENCRYPT_MODE, id, index, nonce).doFinal(compressed) }
        finally { compressed.fill(0) }
        return JSONObject().put("nonce", Base64.getEncoder().encodeToString(nonce))
            .put("ciphertext", Base64.getEncoder().encodeToString(encrypted)).toString().also {
                require(it.length <= MAX_PART_BYTES) { "A compressed backup page is too large." }
            }
    }
    fun open(payload: String, id: String, index: Int): String {
        require(payload.length <= MAX_PART_BYTES) { "Backup page is too large." }
        val j = JSONObject(payload)
        val nonce = Base64.getDecoder().decode(j.getString("nonce"))
        val compressed = try {
            cipher(Cipher.DECRYPT_MODE, id, index, nonce).doFinal(Base64.getDecoder().decode(j.getString("ciphertext")))
        } catch (_: java.security.GeneralSecurityException) {
            throw IllegalArgumentException("Recovery passphrase is incorrect or the backup was changed. Nothing was restored.")
        }
        try {
            GZIPInputStream(compressed.inputStream()).use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(out.size() + n <= MAX_PLAIN) { "Expanded backup page is too large." }
                    out.write(buffer, 0, n)
                }
                return out.toString("UTF-8")
            }
        } finally { compressed.fill(0) }
    }
    override fun close() { key.fill(0) }
    companion object {
        const val MAX_PLAIN = 4 * 1024 * 1024
        const val MAX_PART_BYTES = 1024 * 1024
        const val MAX_PARTS = 2048
        const val MAX_ARCHIVE_BYTES = 64L * 1024 * 1024
    }
}

/** Files on disk contain only authenticated ciphertext and are deleted on success AND failure. */
class BackupArchive(private val local: PagedSnapshotStore, private val remote: ArchiveBackupRemote, private val cacheDir: File) {
    suspend fun backup(passphrase: CharArray): Long = staging { dir ->
        val id = UUID.randomUUID().toString()
        ArchiveCipher(passphrase).use { crypto ->
            val hashes = JSONArray()
            var bytes = 0L
            var createdAt = 0L
            local.forEachPage { page ->
                createdAt = page.createdAtMs
                for (encoded in splitPage(page)) {
                    val index = hashes.length()
                    require(index < ArchiveCipher.MAX_PARTS) { "Backup exceeds 2,048 pages. Choose a shorter retention period or export older records." }
                    val payload = crypto.seal(encoded, id, index)
                    bytes += payload.length
                    require(bytes <= ArchiveCipher.MAX_ARCHIVE_BYTES) { "Encrypted backup exceeds the 64 MB account snapshot limit." }
                    File(dir, "$index").writeText(payload)
                    hashes.put(hash(payload))
                }
            }
            val content = JSONObject().put("createdAtMs", createdAt).put("hashes", hashes).toString()
            val manifest = JSONObject().put("format", "dailybeat-archive").put("version", 1)
                .put("salt", crypto.saltText).put("sealed", JSONObject(crypto.seal(content, id, -1))).toString()
            // All local validation finishes before creating any remote version.
            try {
                remote.beginVersion(id)
                repeat(hashes.length()) { index -> remote.uploadPart(id, index, File(dir, "$index").readText()) }
                remote.publishVersion(id, manifest, hashes.length())
            } catch (error: Exception) {
                // The DELETE matches only our uncommitted UUID. An uncertain publish response can
                // never cause cleanup to remove a backup that the server already committed.
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    runCatching { remote.abandonVersion(id) }
                }
                throw error
            }
            createdAt
        }
    }

    suspend fun restore(passphrase: CharArray, version: BackupVersion): String = staging { dir ->
        val manifest = JSONObject(version.manifest)
        require(manifest.optString("format") == "dailybeat-archive" && manifest.optInt("version") == 1) { "Unsupported backup archive." }
        ArchiveCipher(passphrase, Base64.getDecoder().decode(manifest.getString("salt"))).use { crypto ->
            val content = JSONObject(crypto.open(manifest.getJSONObject("sealed").toString(), version.id, -1))
            val hashes = content.getJSONArray("hashes")
            require(hashes.length() in 1..ArchiveCipher.MAX_PARTS) { "Invalid backup page count." }
            var bytes = 0L
            repeat(hashes.length()) { index ->
                val payload = remote.downloadPart(version.id, index)
                bytes += payload.length
                require(payload.length <= ArchiveCipher.MAX_PART_BYTES && bytes <= ArchiveCipher.MAX_ARCHIVE_BYTES) { "Backup exceeds the restore limit." }
                require(hash(payload) == hashes.getString(index)) { "Backup page is missing or changed. Nothing was restored." }
                // Authenticate and validate every page BEFORE touching local records.
                BackupSnapshotCodec.decode(crypto.open(payload, version.id, index)).also {
                    require(it.createdAtMs == content.getLong("createdAtMs")) { "Backup pages do not belong together." }
                }
                File(dir, "$index").writeText(payload)
            }
            local.restorePages(sequence {
                repeat(hashes.length()) { index ->
                    yield(BackupSnapshotCodec.decode(crypto.open(File(dir, "$index").readText(), version.id, index)))
                }
            })
            version.createdAt
        }
    }

    private suspend fun <T> staging(block: suspend (File) -> T): T {
        cacheDir.mkdirs()
        val dir = File(cacheDir, UUID.randomUUID().toString())
        check(dir.mkdir()) { "Could not prepare backup storage. Check free space." }
        return try { block(dir) } finally { dir.deleteRecursively() }
    }
    private fun hash(payload: String): String = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(JSONObject(payload).let { it.getString("nonce") + ":" + it.getString("ciphertext") }.toByteArray()))

    private fun splitPage(page: BackupSnapshot): Sequence<String> = sequence {
        val text = BackupSnapshotCodec.encode(page)
        if (text.toByteArray().size <= ArchiveCipher.MAX_PLAIN) { yield(text); return@sequence }
        // A page contains one table; split large text rows before encryption, never drop a row.
        val size = listOf(page.events.size, page.places.size, page.diaries.size, page.visits.size,
            page.breadcrumbs.size, page.beatReviews.size, page.diaryRevisions.size, page.visitCorrections.size).max()
        require(size > 1) { "A record exceeds the backup page limit." }
        val midpoint = size / 2
        fun half(first: Boolean) = page.copy(
            events = if(first) page.events.take(midpoint) else page.events.drop(midpoint),
            places = if(first) page.places.take(midpoint) else page.places.drop(midpoint),
            diaries = if(first) page.diaries.take(midpoint) else page.diaries.drop(midpoint),
            visits = if(first) page.visits.take(midpoint) else page.visits.drop(midpoint),
            breadcrumbs = if(first) page.breadcrumbs.take(midpoint) else page.breadcrumbs.drop(midpoint),
            beatReviews = if(first) page.beatReviews.take(midpoint) else page.beatReviews.drop(midpoint),
            diaryRevisions = if(first) page.diaryRevisions.take(midpoint) else page.diaryRevisions.drop(midpoint),
            visitCorrections = if(first) page.visitCorrections.take(midpoint) else page.visitCorrections.drop(midpoint))
        yieldAll(splitPage(half(true))); yieldAll(splitPage(half(false)))
    }
}
