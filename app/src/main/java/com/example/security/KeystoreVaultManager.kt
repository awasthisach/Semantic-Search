package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreVaultManager {

    companion object {
        private const val TAG = "KeystoreVaultManager"
        private const val KEY_ALIAS = "VVF_SMART_MANAGER_VAULT_KEY"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_SIZE_BYTES = 12
    }

    private var fallbackKey: SecretKey? = null

    private val keyStore: KeyStore? = try {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    } catch (e: Throwable) {
        null
    }

    init {
        ensureSecretKeyExists()
    }

    private fun ensureSecretKeyExists() {
        if (keyStore != null) {
            try {
                if (!keyStore.containsAlias(KEY_ALIAS)) {
                    val keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        ANDROID_KEYSTORE
                    )
                    val spec = KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()

                    keyGenerator.init(spec)
                    keyGenerator.generateKey()
                    Log.i(TAG, "Hardware-backed AES-256 Master Key generated successfully in Android Keystore")
                }
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate Keystore secret key: ${e.message}")
            }
        }

        if (fallbackKey == null) {
            try {
                val keyGen = KeyGenerator.getInstance("AES")
                keyGen.init(256)
                fallbackKey = keyGen.generateKey()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate fallback SecretKey: ${e.message}")
            }
        }
    }

    private fun getSecretKey(): SecretKey {
        ensureSecretKeyExists()
        if (keyStore != null) {
            try {
                if (keyStore.containsAlias(KEY_ALIAS)) {
                    val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                    if (entry != null) return entry.secretKey
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error accessing AndroidKeyStore entry: ${e.message}")
            }
        }
        return fallbackKey ?: throw IllegalStateException("No valid SecretKey available")
    }

    data class EncryptedResult(
        val ciphertext: ByteArray,
        val iv: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as EncryptedResult
            return ciphertext.contentEquals(other.ciphertext) && iv.contentEquals(other.iv)
        }

        override fun hashCode(): Int {
            var result = ciphertext.contentHashCode()
            result = 31 * result + iv.contentHashCode()
            return result
        }
    }

    fun encryptBytes(data: ByteArray): EncryptedResult {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val secretKey = getSecretKey()
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(data)
        return EncryptedResult(ciphertext = ciphertext, iv = iv)
    }

    fun decryptBytes(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val secretKey = getSecretKey()
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        return cipher.doFinal(ciphertext)
    }

    /**
     * Secure SHA-256 PIN Hashing with Salt
     */
    fun hashPin(pin: String, salt: String = "VVF_SMART_MANAGER_SALT"): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val combined = "$salt:$pin".toByteArray(Charsets.UTF_8)
        val hash = digest.digest(combined)
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(inputPin: String, storedHash: String, salt: String = "VVF_SMART_MANAGER_SALT"): Boolean {
        if (storedHash.isBlank()) return false
        val computedHash = hashPin(inputPin, salt)
        return MessageDigest.isEqual(
            computedHash.lowercase().toByteArray(Charsets.UTF_8),
            storedHash.lowercase().toByteArray(Charsets.UTF_8)
        )
    }
}
