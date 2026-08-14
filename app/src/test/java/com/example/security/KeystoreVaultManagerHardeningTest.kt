package com.example.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeystoreVaultManagerHardeningTest {

    @Test
    fun `empty payload round trips`() {
        val manager = KeystoreVaultManager()
        val encrypted = manager.encryptBytes(ByteArray(0))
        assertTrue(encrypted.iv.isNotEmpty())
        assertArrayEquals(ByteArray(0), manager.decryptBytes(encrypted.ciphertext, encrypted.iv))
    }

    @Test
    fun `encryption uses a fresh IV for every operation`() {
        val manager = KeystoreVaultManager()
        val payload = "same plaintext".toByteArray()
        val first = manager.encryptBytes(payload)
        val second = manager.encryptBytes(payload)
        assertNotEquals(first.iv.toList(), second.iv.toList())
        assertNotEquals(first.ciphertext.toList(), second.ciphertext.toList())
    }

    @Test
    fun `tampered IV is rejected`() {
        val manager = KeystoreVaultManager()
        val encrypted = manager.encryptBytes("secret".toByteArray())
        val tamperedIv = encrypted.iv.copyOf()
        tamperedIv[0] = (tamperedIv[0].toInt() xor 1).toByte()

        var rejected = false
        try {
            manager.decryptBytes(encrypted.ciphertext, tamperedIv)
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun `direct cipher APIs decrypt the matching ciphertext`() {
        val manager = KeystoreVaultManager()
        val plaintext = "cipher-api".toByteArray()
        val encryptionCipher = manager.getEncryptionCipher()
        val ciphertext = encryptionCipher.doFinal(plaintext)
        val decryptionCipher = manager.getDecryptionCipher(encryptionCipher.iv)
        assertArrayEquals(plaintext, decryptionCipher.doFinal(ciphertext))
    }

    @Test
    fun `malformed PBKDF2 encodings are rejected`() {
        val manager = KeystoreVaultManager()
        assertFalse(manager.verifyPin("1234", "not:hex:zz"))
        assertFalse(manager.verifyPin("1234", "999999999:00:00"))
        assertFalse(manager.verifyPin("1234", "9:00:00"))
        assertFalse(manager.verifyPin("1234", "10000:0:00"))
    }

    @Test
    fun `legacy hash comparison is case insensitive`() {
        val manager = KeystoreVaultManager()
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val legacy = digest.digest("VVF_SMART_MANAGER_SALT:1234".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertTrue(manager.verifyPin("1234", legacy.uppercase()))
        assertFalse(manager.verifyPin("12345", legacy.uppercase()))
    }
}
