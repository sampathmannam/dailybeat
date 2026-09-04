package com.dailybeat.app.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts route coordinates with an app key held by Android Keystore.
 *
 * Mission id and timestamp are authenticated as additional data, so an encrypted
 * point cannot be silently moved to another mission or time in the database.
 */
class PatrolTrackCipher(private val context: Context) {

    @Volatile
    private var cachedKey: SecretKey? = null

    fun encrypt(
        missionId: String,
        timestampMs: Long,
        coordinates: PatrolCoordinates,
    ): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(aad(missionId, timestampMs))
        val ciphertext = cipher.doFinal(PatrolPointCodec.encode(coordinates))
        val iv = cipher.iv
        require(iv.size in 12..MAX_IV_BYTES) { "Unexpected AES-GCM IV size" }
        return ByteBuffer.allocate(2 + iv.size + ciphertext.size)
            .put(ENVELOPE_VERSION)
            .put(iv.size.toByte())
            .put(iv)
            .put(ciphertext)
            .array()
    }

    fun decrypt(
        missionId: String,
        timestampMs: Long,
        encryptedPayload: ByteArray,
    ): PatrolCoordinates {
        require(encryptedPayload.size >= MIN_ENVELOPE_BYTES) { "Encrypted patrol point is truncated" }
        val buffer = ByteBuffer.wrap(encryptedPayload)
        require(buffer.get() == ENVELOPE_VERSION) { "Unsupported patrol point encryption version" }
        val ivLength = buffer.get().toInt() and 0xff
        require(ivLength in 12..MAX_IV_BYTES && buffer.remaining() > ivLength) {
            "Invalid patrol point encryption envelope"
        }
        val iv = ByteArray(ivLength).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad(missionId, timestampMs))
        return PatrolPointCodec.decode(cipher.doFinal(ciphertext))
    }

    private fun getOrCreateKey(): SecretKey {
        cachedKey?.let { return it }
        return synchronized(this) {
            cachedKey ?: loadOrCreateKey().also { cachedKey = it }
        }
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val canUseStrongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        if (canUseStrongBox) {
            try {
                keyGenerator.init(keySpec(useStrongBox = true))
                return keyGenerator.generateKey()
            } catch (_: StrongBoxUnavailableException) {
                // Some devices advertise StrongBox but cannot allocate a key. Keystore remains the safe fallback.
            }
        }
        keyGenerator.init(keySpec(useStrongBox = false))
        return keyGenerator.generateKey()
    }

    private fun keySpec(useStrongBox: Boolean): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(useStrongBox)
        }
        return builder.build()
    }

    private fun aad(missionId: String, timestampMs: Long): ByteArray {
        val missionBytes = missionId.toByteArray(Charsets.UTF_8)
        require(missionBytes.isNotEmpty() && missionBytes.size <= MAX_MISSION_ID_BYTES) {
            "Mission id is missing or too long"
        }
        return ByteBuffer.allocate(Int.SIZE_BYTES + missionBytes.size + Long.SIZE_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(missionBytes.size)
            .put(missionBytes)
            .putLong(timestampMs)
            .array()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "patrolgrid_route_aes_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val MAX_IV_BYTES = 32
        const val MAX_MISSION_ID_BYTES = 512
        const val MIN_ENVELOPE_BYTES = 2 + 12 + 16
        const val ENVELOPE_VERSION: Byte = 1
    }
}
