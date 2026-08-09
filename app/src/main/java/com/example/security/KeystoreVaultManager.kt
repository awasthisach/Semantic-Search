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
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val secretKey = getSecretKey()
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(data)
            EncryptedResult(ciphertext = ciphertext, iv = iv)
        } catch (e: javax.crypto.AEADBadTagException) {
            Log.e(TAG, "AEADBadTagException during encryption: Tampered or invalid key/tag", e)
            throw java.security.GeneralSecurityException("Encryption failed: Tampered data or invalid security key tag.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed: ${e.message}", e)
            throw e
        }
    }

    fun getEncryptionCipher(): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val secretKey = getSecretKey()
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher
    }

    fun getDecryptionCipher(iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val secretKey = getSecretKey()
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        return cipher
    }

    fun decryptBytes(ciphertext: ByteArray, iv: ByteArray): ByteArray {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val secretKey = getSecretKey()
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            cipher.doFinal(ciphertext)
        } catch (e: javax.crypto.AEADBadTagException) {
            Log.e(TAG, "AEADBadTagException during decryption: Incorrect PIN or tampered vault data", e)
            throw java.security.GeneralSecurityException("Decryption failed: Incorrect PIN or tampered vault data.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed: ${e.message}", e)
            throw e
        }
    }

    /**
     * Secure PIN Hashing with Random Salt using PBKDF2WithHmacSHA256
     */
    fun hashPin(pin: String): String {
        val salt = ByteArray(16).apply { java.security.SecureRandom().nextBytes(this) }
        val iterations = 10000
        val hash = pbkdf2(pin, salt, iterations) ?: throw IllegalStateException("PBKDF2 derivation failed")
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        val hashHex = hash.joinToString("") { "%02x".format(it) }
        return "$iterations:$saltHex:$hashHex"
    }

    fun verifyPin(inputPin: String, storedHash: String): Boolean {
        if (storedHash.isBlank()) return false
        val parts = storedHash.split(":")
        if (parts.size != 3) {
            // Legacy / fallback hash verification for backwards compatibility
            val legacyHash = hashLegacySha256(inputPin, "VVF_SMART_MANAGER_SALT")
            return MessageDigest.isEqual(
                legacyHash.lowercase().toByteArray(Charsets.UTF_8),
                storedHash.lowercase().toByteArray(Charsets.UTF_8)
            )
        }
        val iterations = parts[0].toIntOrNull() ?: return false
        val salt = hexToByteArray(parts[1]) ?: return false
        val expectedHash = hexToByteArray(parts[2]) ?: return false
        
        val computedHash = pbkdf2(inputPin, salt, iterations) ?: return false
        return MessageDigest.isEqual(computedHash, expectedHash)
    }

    private fun pbkdf2(pin: String, salt: ByteArray, iterations: Int): ByteArray? {
        return try {
            val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
            val skf = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            skf.generateSecret(spec).encoded
        } catch (e: Exception) {
            Log.e(TAG, "PBKDF2 failed", e)
            null
        }
    }

    private fun hashLegacySha256(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val combined = "$salt:$pin".toByteArray(Charsets.UTF_8)
        val hash = digest.digest(combined)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun hexToByteArray(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            val index = i * 2
            val j = hex.substring(index, index + 2).toIntOrNull(16) ?: return null
            result[i] = j.toByte()
        }
        return result
    }
}
