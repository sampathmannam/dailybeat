package com.dailybeat.app.backup

import com.dailybeat.app.util.isBoundedJson
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Versioned authenticated envelope. The recovery passphrase never enters the upload.
 * Uses platform JCA: AES-256-GCM, fresh 128-bit salt and 96-bit nonce per backup.
 */
object BackupEnvelope {
    const val MIN_PASSPHRASE_CHARS = 20
    const val MAX_PASSPHRASE_CHARS = 256
    private const val ITERATIONS = 600_000
    private const val MAX_PLAINTEXT_BYTES = 8 * 1024 * 1024
    private const val MAX_ENVELOPE_CHARS = 12 * 1024 * 1024
    private const val FORMAT = "dailybeat-encrypted-backup"
    private const val KDF = "PBKDF2-HMAC-SHA256"
    private val aad = "$FORMAT:1:$KDF:$ITERATIONS:AES-256-GCM".toByteArray(Charsets.UTF_8)

    fun seal(snapshot: String, passphrase: CharArray): String {
        require(passphrase.size in MIN_PASSPHRASE_CHARS..MAX_PASSPHRASE_CHARS) {
            "Use a recovery passphrase of 20–256 characters, ideally six random words."
        }
        val plain = snapshot.toByteArray(Charsets.UTF_8)
        require(plain.size <= MAX_PLAINTEXT_BYTES) { "Backup is larger than the supported 8 MB limit." }
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val key = derive(passphrase, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(aad)
            return JSONObject().put("format", FORMAT).put("envelopeVersion", 1)
                .put("kdf", KDF).put("iterations", ITERATIONS)
                .put("salt", encode(salt)).put("nonce", encode(nonce))
                .put("ciphertext", encode(cipher.doFinal(plain))).toString()
        } finally { key.fill(0); plain.fill(0) }
    }

    fun open(envelope: String, passphrase: CharArray): String {
        require(envelope.length <= MAX_ENVELOPE_CHARS) { "Backup envelope is too large." }
        require(passphrase.size in MIN_PASSPHRASE_CHARS..MAX_PASSPHRASE_CHARS) {
            "Enter the recovery passphrase used when this backup was created."
        }
        require(isBoundedJson(envelope, objectOnly = true)) { "Invalid encrypted backup." }
        val obj = JSONObject(envelope)
        require(obj.optString("format") == FORMAT && obj.opt("envelopeVersion") == 1 &&
            obj.optString("kdf") == KDF && obj.opt("iterations") == ITERATIONS) {
            "This is not a supported encrypted backup. Legacy restores require explicit selection."
        }
        val salt = decode(obj.getString("salt"))
        val nonce = decode(obj.getString("nonce"))
        val ciphertext = decode(obj.getString("ciphertext"))
        require(salt.size == 16 && nonce.size == 12 && ciphertext.size in 16..MAX_PLAINTEXT_BYTES + 16) {
            "Invalid encrypted backup."
        }
        val key = derive(passphrase, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(aad)
            val plain = try { cipher.doFinal(ciphertext) } catch (_: java.security.GeneralSecurityException) {
                throw IllegalArgumentException("Recovery passphrase is incorrect or the backup was changed. Nothing was restored.")
            }
            return try { String(plain, Charsets.UTF_8) } finally { plain.fill(0) }
        } finally { key.fill(0) }
    }

    private fun derive(passphrase: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)
}
