package com.dailybeat.app.patrolgrid

import android.security.keystore.KeyGenParameterSpec
import java.io.InputStream
import java.io.OutputStream
import java.security.InvalidAlgorithmParameterException
import java.security.Key
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Collections
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/** Minimal JVM provider used to exercise AndroidX encrypted preferences under Robolectric. */
internal object TestAndroidKeyStore {
    const val PROVIDER_NAME = "AndroidKeyStore"
    val keys = ConcurrentHashMap<String, Key>()

    fun install() {
        Security.removeProvider(PROVIDER_NAME)
        keys.clear()
        check(Security.addProvider(TestAndroidKeyStoreProvider()) != -1)
    }

    fun uninstall() {
        Security.removeProvider(PROVIDER_NAME)
        keys.clear()
    }
}

class TestAndroidKeyStoreProvider : Provider(
    TestAndroidKeyStore.PROVIDER_NAME,
    1.0,
    "PatrolGrid JVM-only Android Keystore provider",
) {
    init {
        put("KeyStore.AndroidKeyStore", TestAndroidKeyStoreSpi::class.java.name)
        put("KeyGenerator.AES", TestAndroidAesKeyGeneratorSpi::class.java.name)
    }
}

class TestAndroidKeyStoreSpi : KeyStoreSpi() {
    override fun engineGetKey(alias: String?, password: CharArray?): Key? =
        alias?.let(TestAndroidKeyStore.keys::get)

    override fun engineGetCertificateChain(alias: String?): Array<Certificate>? = null

    override fun engineGetCertificate(alias: String?): Certificate? = null

    override fun engineGetCreationDate(alias: String?): Date? =
        alias?.takeIf(TestAndroidKeyStore.keys::containsKey)?.let { Date(0) }

    override fun engineSetKeyEntry(
        alias: String?,
        key: Key?,
        password: CharArray?,
        chain: Array<out Certificate>?,
    ) {
        if (alias != null && key != null) TestAndroidKeyStore.keys[alias] = key
    }

    override fun engineSetKeyEntry(alias: String?, key: ByteArray?, chain: Array<out Certificate>?) {
        if (alias != null && key != null) TestAndroidKeyStore.keys[alias] = SecretKeySpec(key, "AES")
    }

    override fun engineSetCertificateEntry(alias: String?, cert: Certificate?) = Unit

    override fun engineDeleteEntry(alias: String?) {
        if (alias != null) TestAndroidKeyStore.keys.remove(alias)
    }

    override fun engineAliases() = Collections.enumeration(TestAndroidKeyStore.keys.keys)

    override fun engineContainsAlias(alias: String?): Boolean =
        alias != null && TestAndroidKeyStore.keys.containsKey(alias)

    override fun engineSize(): Int = TestAndroidKeyStore.keys.size

    override fun engineIsKeyEntry(alias: String?): Boolean = engineContainsAlias(alias)

    override fun engineIsCertificateEntry(alias: String?): Boolean = false

    override fun engineGetCertificateAlias(cert: Certificate?): String? = null

    override fun engineStore(stream: OutputStream?, password: CharArray?) = Unit

    override fun engineLoad(stream: InputStream?, password: CharArray?) = Unit
}

class TestAndroidAesKeyGeneratorSpi : KeyGeneratorSpi() {
    private var alias: String? = null
    private var keySizeBits: Int = 256
    private var random: SecureRandom = SecureRandom()

    override fun engineInit(random: SecureRandom?) {
        if (random != null) this.random = random
    }

    override fun engineInit(params: AlgorithmParameterSpec?, random: SecureRandom?) {
        val spec = params as? KeyGenParameterSpec
            ?: throw InvalidAlgorithmParameterException("Expected KeyGenParameterSpec")
        alias = spec.keystoreAlias
        keySizeBits = spec.keySize.takeIf { it > 0 } ?: 256
        if (random != null) this.random = random
    }

    override fun engineInit(keysize: Int, random: SecureRandom?) {
        keySizeBits = keysize
        if (random != null) this.random = random
    }

    override fun engineGenerateKey(): SecretKey {
        val targetAlias = requireNotNull(alias) { "Android Keystore key alias was not initialized" }
        val bytes = ByteArray(keySizeBits / 8).also(random::nextBytes)
        return SecretKeySpec(bytes, "AES").also { TestAndroidKeyStore.keys[targetAlias] = it }
    }
}
