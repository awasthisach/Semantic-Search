package com.example.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeystoreVaultManagerTest {

    @Test
    fun `test hash pin verification`() {
        val manager = KeystoreVaultManager()
        val pin = "1234"
        val hash = manager.hashPin(pin)
        
        assertTrue(manager.verifyPin(pin, hash))
        assertFalse(manager.verifyPin("4321", hash))
    }

    @Test
    fun `test encrypt and decrypt bytes`() {
        val manager = KeystoreVaultManager()
        val originalData = "Hello, secret vault!".toByteArray(Charsets.UTF_8)
        
        val encryptedResult = manager.encryptBytes(originalData)
        assertFalse(originalData.contentEquals(encryptedResult.ciphertext))
        
        val decryptedData = manager.decryptBytes(encryptedResult.ciphertext, encryptedResult.iv)
        assertArrayEquals(originalData, decryptedData)
    }
}
