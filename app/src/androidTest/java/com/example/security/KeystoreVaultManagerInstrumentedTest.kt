package com.example.security

import android.security.keystore.KeyStoreException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreVaultManagerInstrumentedTest {

    @Test
    fun encryption_roundTrip_survivesSeparateManagerInstances() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.isNotBlank())

        val first = KeystoreVaultManager()
        val plaintext = "production-secret-${System.nanoTime()}".toByteArray(Charsets.UTF_8)
        val encrypted = first.encryptBytes(plaintext)

        val second = KeystoreVaultManager()
        val decrypted = second.decryptBytes(encrypted.ciphertext, encrypted.iv)

        assertArrayEquals(plaintext, decrypted)
        assertFalse(encrypted.ciphertext.contentEquals(plaintext))
    }

    @Test
    fun encryption_uses_authenticated_gcm_and_rejects_tampering() {
        val manager = KeystoreVaultManager()
        val encrypted = manager.encryptBytes("authenticated-data".toByteArray())
        val tampered = encrypted.ciphertext.copyOf()
        tampered[tampered.lastIndex] = (tampered[tampered.lastIndex].toInt() xor 0x01).toByte()

        var rejected = false
        try {
            manager.decryptBytes(tampered, encrypted.iv)
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun encryption_uses_non_empty_iv() {
        val manager = KeystoreVaultManager()
        val encrypted = manager.encryptBytes(ByteArray(0))
        assertTrue(encrypted.iv.isNotEmpty())
    }

    @Test
    fun pinHash_roundTrip_and_wrongPinRejection() {
        val manager = KeystoreVaultManager()
        val hash = manager.hashPin("7391")

        assertTrue(manager.verifyPin("7391", hash))
        assertFalse(manager.verifyPin("7392", hash))
    }

    @Test
    fun legacyPinHash_remains_verifiable_for_migration() {
        val manager = KeystoreVaultManager()
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val legacyHash = digest.digest("VVF_SMART_MANAGER_SALT:7391".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        assertTrue(manager.verifyPin("7391", legacyHash))
        assertFalse(manager.verifyPin("7392", legacyHash))
    }
}
