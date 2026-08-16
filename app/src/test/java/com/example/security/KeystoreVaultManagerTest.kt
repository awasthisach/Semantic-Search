package com.example.security

import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeystoreVaultManagerTest {
    private fun manager(): KeystoreVaultManager = KeystoreVaultManager { TEST_KEY }

    @Test
    fun `test hash pin verification`() {
        val manager = manager()
        val pin = "1234"
        val hash = manager.hashPin(pin)
        assertTrue(manager.verifyPin(pin, hash))
        assertFalse(manager.verifyPin("4321", hash))
    }

    @Test
    fun `test randomized salt generates distinct hashes for same pin`() {
        val manager = manager()
        val pin = "1234"
        val hash1 = manager.hashPin(pin)
        val hash2 = manager.hashPin(pin)
        assertNotEquals(hash1, hash2)
        assertTrue(manager.verifyPin(pin, hash1))
        assertTrue(manager.verifyPin(pin, hash2))
    }

    @Test
    fun `test legacy SHA-256 fallback compatibility`() {
        val manager = manager()
        val pin = "1234"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val combined = "VVF_SMART_MANAGER_SALT:1234".toByteArray(Charsets.UTF_8)
        val legacyHash = digest.digest(combined).joinToString("") { "%02x".format(it) }
        assertTrue(manager.verifyPin(pin, legacyHash))
        assertFalse(manager.verifyPin("4321", legacyHash))
    }

    @Test
    fun `test encrypt and decrypt bytes`() {
        val manager = manager()
        val originalData = "Hello, secret vault!".toByteArray(Charsets.UTF_8)
        val encryptedResult = manager.encryptBytes(originalData)
        assertFalse(originalData.contentEquals(encryptedResult.ciphertext))
        assertFalse(encryptedResult.iv.isEmpty())
        val decryptedData = manager.decryptBytes(encryptedResult.ciphertext, encryptedResult.iv)
        assertArrayEquals(originalData, decryptedData)
    }

    @Test
    fun `test tampered ciphertext is rejected`() {
        val manager = manager()
        val encrypted = manager.encryptBytes("secret".toByteArray())
        val tampered = encrypted.ciphertext.copyOf()
        tampered[tampered.lastIndex] = (tampered[tampered.lastIndex].toInt() xor 1).toByte()
        var rejected = false
        try {
            manager.decryptBytes(tampered, encrypted.iv)
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun `test two different instances give distinct hashes for same pin`() {
        val manager1 = manager()
        val manager2 = manager()
        val pin = "1234"
        val hash1 = manager1.hashPin(pin)
        val hash2 = manager2.hashPin(pin)
        assertNotEquals(hash1, hash2)
        assertTrue(manager1.verifyPin(pin, hash1))
        assertTrue(manager2.verifyPin(pin, hash2))
    }

    @Test
    fun `test generated salt verification works across instances persistence`() {
        val manager1 = manager()
        val pin = "1234"
        val hashFromInstance1 = manager1.hashPin(pin)
        val manager2 = manager()
        assertTrue(manager2.verifyPin(pin, hashFromInstance1))
        assertFalse(manager2.verifyPin("wrong_pin", hashFromInstance1))
    }

    @Test
    fun `test invalid stored hashes are rejected`() {
        val manager = manager()
        assertFalse(manager.verifyPin("1234", ""))
        assertFalse(manager.verifyPin("1234", "not-a-valid-hash"))
        assertFalse(manager.verifyPin("1234", "999:zz:aa"))
        assertFalse(manager.verifyPin("1234", "1:00:00"))
    }

    @Test
    fun `test pbkdf2 iteration count is at least 10000`() {
        val manager = manager()
        val hash = manager.hashPin("1234")
        val parts = hash.split(":")
        assertEquals(3, parts.size)
        val iterations = parts[0].toIntOrNull()
        assertNotNull(iterations)
        assertTrue("PBKDF2 iteration count must be at least 10000", iterations!! >= 10000)
    }

    companion object {
        private val TEST_KEY: SecretKey = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    }
}
