package com.dailybeat.app.backup

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupEnvelopeTest {
    private val secret get() = "harbour comet velvet cedar orbit lantern".toCharArray()
    @Test fun roundTripDoesNotExposeDiaryOrPassphrase() {
        val plain = """{"diaries":[{"text":"Private hospital visit"}]}"""
        val sealed = BackupEnvelope.seal(plain, secret)
        assertFalse(sealed.contains("hospital"))
        assertFalse(sealed.contains("harbour"))
        assertEquals(plain, BackupEnvelope.open(sealed, secret))
    }
    @Test fun identicalBackupsHaveFreshSaltNonceAndCiphertext() {
        val a = JSONObject(BackupEnvelope.seal("same", secret))
        val b = JSONObject(BackupEnvelope.seal("same", secret))
        listOf("salt", "nonce", "ciphertext").forEach { assertNotEquals(a.getString(it), b.getString(it)) }
    }
    @Test fun incorrectPassphraseCannotDecrypt() {
        val sealed = BackupEnvelope.seal("private", secret)
        assertThrows(IllegalArgumentException::class.java) {
            BackupEnvelope.open(sealed, "different random recovery words here today".toCharArray())
        }
    }
    @Test fun changedCiphertextIsRejected() {
        val obj = JSONObject(BackupEnvelope.seal("private", secret))
        val data = java.util.Base64.getDecoder().decode(obj.getString("ciphertext"))
        data[0] = (data[0].toInt() xor 1).toByte()
        obj.put("ciphertext", java.util.Base64.getEncoder().encodeToString(data))
        assertThrows(IllegalArgumentException::class.java) { BackupEnvelope.open(obj.toString(), secret) }
    }
    @Test fun unknownFormatOrWorkFactorIsRejectedWithoutTryingIt() {
        val obj = JSONObject(BackupEnvelope.seal("private", secret)).put("iterations", Int.MAX_VALUE)
        assertThrows(IllegalArgumentException::class.java) { BackupEnvelope.open(obj.toString(), secret) }
        obj.put("iterations", 600000).put("envelopeVersion", 2)
        assertThrows(IllegalArgumentException::class.java) { BackupEnvelope.open(obj.toString(), secret) }
    }
    @Test fun shortPassphrasesAndReadableSnapshotsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { BackupEnvelope.seal("private", "short".toCharArray()) }
        assertThrows(IllegalArgumentException::class.java) { BackupEnvelope.open("""{"schemaVersion":2}""", secret) }
    }

    @Test fun deeplyNestedEnvelopeFailsBeforeParsingOrPasswordDerivation() {
        val nested = "{\"unexpected\":" + "[".repeat(5_000) + "0" + "]".repeat(5_000) + "}"
        val error = assertThrows(IllegalArgumentException::class.java) { BackupEnvelope.open(nested, secret) }
        assertEquals("Invalid encrypted backup.", error.message)
    }
}
