package com.example.security

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
    fun pinHash_roundTrip_and_wrongPinRejection() {
        val manager = KeystoreVaultManager()
        val hash = manager.hashPin("7391")

        assertTrue(manager.verifyPin("7391", hash))
        assertFalse(manager.verifyPin("7392", hash))
    }
}
