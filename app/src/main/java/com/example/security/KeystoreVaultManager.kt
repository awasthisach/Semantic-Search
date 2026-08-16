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

/**
 * Provides the non-exportable Android Keystore key used for Vault encryption
 * and PBKDF2-based PIN verification.
 *
 * Production security rule: if Android Keystore is unavailable or unusable,
 * Vault operations fail closed. An in-memory fallback key would make encrypted
 * files permanently undecryptable after process death and is therefore unsafe.
 */
class KeystoreVaultManager {
    companion object {
        private const val TAG = "KeystoreVaultManager"
        private const val KEY_ALIAS = "VVF_SMART_MANAGER_VAULT_KEY"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
        private const val PBKDF2_ITERATIONS = 210_000
        private const val PBKDF2_MIN_ITERATIONS = 100_000
        private const val PBKDF2_MAX_ITERATIONS = 2_000_000
        private const val PBKDF2_SALT_BYTES = 16
        private const val PBKDF2_KEY_BYTES = 32
    }

    private val keyStore: KeyStore by lazy {
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        } catch (e: Exception) {
            Log.e(TAG, "Android Keystore is unavailable; refusing insecure Vault fallback", e)
            throw IllegalStateException("Android Keystore is required for secure Vault operations", e)
        }
    }

    private fun ensureSecretKeyExists() {
        try {
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE,
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Android Keystore Vault key", e)
            throw IllegalStateException("Secure Vault key initialization failed", e)
        }
    }

    private fun getSecretKey(): SecretKey {
        ensureSecretKeyExists()
        return try {
            (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
                ?: throw IllegalStateException("Android Keystore Vault key is unavailable")
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to access Android Keystore Vault key", e)
            throw IllegalStateException("Secure Vault key access failed", e)
        }
    }

    data class EncryptedResult(val ciphertext: ByteArray, val iv: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is EncryptedResult &&
                ciphertext.contentEquals(other.ciphertext) &&
                iv.contentEquals(other.iv)

        override fun hashCode(): Int = 31 * ciphertext.contentHashCode() + iv.contentHashCode()
    }

    fun encryptBytes(data: ByteArray): EncryptedResult {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        require(iv.size == GCM_IV_LENGTH) { "Unexpected GCM IV length: ${iv.size}" }
        return EncryptedResult(cipher.doFinal(data), iv)
    }

    fun getEncryptionCipher(): Cipher = Cipher.getInstance(TRANSFORMATION).apply {
        init(Cipher.ENCRYPT_MODE, getSecretKey())
        require(iv.size == GCM_IV_LENGTH) { "Unexpected GCM IV length: ${iv.size}" }
    }

    fun getDecryptionCipher(iv: ByteArray): Cipher {
        require(iv.size == GCM_IV_LENGTH) { "Invalid GCM IV length: ${iv.size}" }
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                getSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH, iv),
            )
        }
    }

    fun decryptBytes(ciphertext: ByteArray, iv: ByteArray): ByteArray =
        getDecryptionCipher(iv).doFinal(ciphertext)

    fun hashPin(pin: String): String {
        val salt = ByteArray(PBKDF2_SALT_BYTES).also { java.security.SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt, PBKDF2_ITERATIONS)
            ?: throw IllegalStateException("PBKDF2 derivation failed")
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        val hashHex = hash.joinToString("") { "%02x".format(it) }
        return "$PBKDF2_ITERATIONS:$saltHex:$hashHex"
    }

    fun verifyPin(inputPin: String, storedHash: String): Boolean {
        if (storedHash.isBlank()) return false
        val parts = storedHash.split(":")
        if (parts.size != 3) {
            val legacyHash = hashLegacySha256(inputPin, "VVF_SMART_MANAGER_SALT")
            return MessageDigest.isEqual(
                legacyHash.lowercase().toByteArray(Charsets.UTF_8),
                storedHash.lowercase().toByteArray(Charsets.UTF_8),
            )
        }

        val iterations = parts[0].toIntOrNull() ?: return false
        if (iterations !in PBKDF2_MIN_ITERATIONS..PBKDF2_MAX_ITERATIONS) return false
        val salt = hexToByteArray(parts[1]) ?: return false
        val expectedHash = hexToByteArray(parts[2]) ?: return false
        if (salt.size != PBKDF2_SALT_BYTES || expectedHash.size != PBKDF2_KEY_BYTES) return false

        val computedHash = pbkdf2(inputPin, salt, iterations) ?: return false
        return MessageDigest.isEqual(computedHash, expectedHash)
    }

    private fun pbkdf2(pin: String, salt: ByteArray, iterations: Int): ByteArray? = try {
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, iterations, PBKDF2_KEY_BYTES * 8)
        try {
            javax.crypto.SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    } catch (e: Exception) {
        Log.e(TAG, "PBKDF2 failed", e)
        null
    }

    private fun hashLegacySha256(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest("$salt:$pin".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun hexToByteArray(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return ByteArray(hex.length / 2).also { result ->
            for (i in result.indices) {
                val value = hex.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return null
                result[i] = value.toByte()
            }
        }
    }
}
