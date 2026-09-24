package com.dailybeat.app.backup

import org.json.JSONArray
import org.json.JSONObject
import com.dailybeat.app.capture.CaptureStorageGate
import com.dailybeat.app.util.isBoundedJson
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
    suspend fun restorePages(pages: Sequence<BackupSnapshot>, expectedDataGeneration: Long) {
        requireCurrentRestoreGeneration(expectedDataGeneration)
        restorePages(pages)
    }
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

private class ArchivePageTooLargeException(message: String) : IllegalArgumentException(message)

/** Upload quotas use PostgreSQL's jsonb::text bytes, not compact Android JSON length. */
internal object ArchiveUploadSize {
    fun addPart(previousBytes: Long, payload: String): Long {
        require(previousBytes in 0..ArchiveCipher.MAX_ARCHIVE_BYTES)
        val envelope = JSONObject(payload)
        val nonce = envelope.opt("nonce") as? String
        val ciphertext = envelope.opt("ciphertext") as? String
        require(envelope.length() == 2 && nonce != null && ciphertext != null &&
            nonce.all(::isBase64Character) && ciphertext.all(::isBase64Character)) {
            "Invalid generated backup envelope."
        }
        // The validated Base64 alphabet is ASCII and needs no JSON escaping, so its
        // UTF-8 byte count equals its character count. jsonb adds a space after each
        // colon and the comma: {"nonce": "...", "ciphertext": "..."}.
        val storedBytes = 31L + nonce.length + ciphertext.length
        if (storedBytes > ArchiveCipher.MAX_PART_BYTES) {
            throw ArchivePageTooLargeException("A compressed backup page is too large.")
        }
        return (previousBytes + storedBytes).also {
            require(it <= ArchiveCipher.MAX_ARCHIVE_BYTES) {
                "Encrypted backup exceeds the 64 MB account snapshot limit."
            }
        }
    }

    private fun isBase64Character(char: Char): Boolean =
        char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char == '+' || char == '/' || char == '='
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
        if (plain.size > MAX_PLAIN) {
            plain.fill(0)
            throw ArchivePageTooLargeException("A backup page is too large.")
        }
        val compressed = try { ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(plain) } }.toByteArray() }
        finally { plain.fill(0) }
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val encrypted = try { cipher(Cipher.ENCRYPT_MODE, id, index, nonce).doFinal(compressed) }
        finally { compressed.fill(0) }
        return JSONObject().put("nonce", Base64.getEncoder().encodeToString(nonce))
            .put("ciphertext", Base64.getEncoder().encodeToString(encrypted)).toString().also {
                // Retain the client/restore wire ceiling too: JSON encoders may escape '/'.
                if (it.length > MAX_PART_BYTES) {
                    throw ArchivePageTooLargeException("A compressed backup page is too large.")
                }
                ArchiveUploadSize.addPart(0L, it)
            }
    }
    fun open(payload: String, id: String, index: Int): String {
        require(payload.length <= MAX_PART_BYTES) { "Backup page is too large." }
        require(isBoundedJson(payload, objectOnly = true)) { "Invalid backup page." }
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
            var wireBytes = 0L
            var createdAt = 0L
            local.forEachPage { page ->
                createdAt = page.createdAtMs
                val nextIndex = {
                    hashes.length().also { index ->
                        require(index < ArchiveCipher.MAX_PARTS) { "Backup exceeds 2,048 pages. Choose a shorter retention period or export older records." }
                    }
                }
                for (payload in encryptedPages(page, crypto, id, nextIndex)) {
                    val index = hashes.length()
                    bytes = ArchiveUploadSize.addPart(bytes, payload)
                    wireBytes += payload.length
                    require(wireBytes <= ArchiveCipher.MAX_ARCHIVE_BYTES) { "Encrypted backup exceeds the 64 MB account snapshot limit." }
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
                    // Cleanup is best-effort, not permission to keep a cancelled backup alive
                    // through every network timeout/retry. The server expires pending uploads.
                    kotlinx.coroutines.withTimeoutOrNull(5_000L) {
                        runCatching { remote.abandonVersion(id) }
                    }
                }
                throw error
            }
            createdAt
        }
    }

    suspend fun restore(
        passphrase: CharArray,
        version: BackupVersion,
        expectedDataGeneration: Long = CaptureStorageGate.dataGeneration.get(),
    ): String = staging { dir ->
        requireCurrentRestoreGeneration(expectedDataGeneration)
        require(isBoundedJson(version.manifest, objectOnly = true)) { "Invalid backup archive." }
        val manifest = JSONObject(version.manifest)
        require(manifest.optString("format") == "dailybeat-archive" && manifest.optInt("version") == 1) { "Unsupported backup archive." }
        ArchiveCipher(passphrase, Base64.getDecoder().decode(manifest.getString("salt"))).use { crypto ->
            val openedContent = crypto.open(manifest.getJSONObject("sealed").toString(), version.id, -1)
            require(isBoundedJson(openedContent, objectOnly = true)) { "Invalid backup archive." }
            val content = JSONObject(openedContent)
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
            }, expectedDataGeneration)
            version.createdAt
        }
    }

    private suspend fun <T> staging(block: suspend (File) -> T): T {
        cacheDir.mkdirs()
        val dir = File(cacheDir, UUID.randomUUID().toString())
        check(dir.mkdir()) { "Could not prepare backup storage. Check free space." }
        return try { block(dir) } finally { dir.deleteRecursively() }
    }
    private fun hash(payload: String): String {
        require(isBoundedJson(payload, objectOnly = true)) { "Invalid backup page." }
        return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(JSONObject(payload).let {
            it.getString("nonce") + ":" + it.getString("ciphertext")
        }.toByteArray()))
    }

    private fun encryptedPages(
        page: BackupSnapshot,
        crypto: ArchiveCipher,
        id: String,
        nextIndex: () -> Int,
    ): Sequence<String> = sequence {
        val text = BackupSnapshotCodec.encode(page)
        val payload = try { crypto.seal(text, id, nextIndex()) } catch (_: ArchivePageTooLargeException) { null }
        if (payload != null) { yield(payload); return@sequence }
        // Both the expanded page and its encrypted/base64 representation have limits. A legal
        // page with less-compressible text can exceed the latter even below MAX_PLAIN.
        // Split at record boundaries; each yielded payload binds the final archive page index.
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
        yieldAll(encryptedPages(half(true), crypto, id, nextIndex))
        yieldAll(encryptedPages(half(false), crypto, id, nextIndex))
    }
}
