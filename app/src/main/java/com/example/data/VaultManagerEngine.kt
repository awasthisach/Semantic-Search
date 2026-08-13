package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.security.KeystoreVaultManager

/**
 * Security boundary for Vault PIN metadata.
 *
 * Production must fail closed: a Vault PIN hash is never stored in ordinary
 * SharedPreferences and there is no built-in/default PIN.
 */
class VaultManagerEngine(
    private val context: Context,
    private val keystoreVaultManager: KeystoreVaultManager
) {
    private val vaultPrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "vvf_vault_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Throwable) {
            Log.e("VaultManagerEngine", "Secure Vault preferences initialization failed", e)
            throw IllegalStateException("Secure Vault storage is unavailable; refusing insecure fallback", e)
        }
    }

    /** Returns the configured PIN hash, or an empty value when setup has not occurred. */
    fun getStoredVaultPinHash(): String = vaultPrefs.getString("vault_pin_hash", "").orEmpty()

    fun hasVaultPin(): Boolean = getStoredVaultPinHash().isNotBlank()

    fun verifyVaultPin(inputPin: String, storedHash: String = ""): Boolean {
        if (inputPin.length != 4) return false
        val expectedHash = if (storedHash.isNotBlank()) storedHash else getStoredVaultPinHash()
        if (expectedHash.isBlank()) return false
        return keystoreVaultManager.verifyPin(inputPin, expectedHash)
    }

    /**
     * Changes an existing PIN only after validating the old PIN.
     * PIN creation is intentionally separate so a missing PIN can never
     * silently become a known default credential.
     */
    fun changeVaultPin(oldPin: String, newPin: String): Boolean {
        if (newPin.length != 4 || !hasVaultPin()) return false
        if (!verifyVaultPin(oldPin)) return false
        val newHash = keystoreVaultManager.hashPin(newPin)
        return vaultPrefs.edit().putString("vault_pin_hash", newHash).commit()
    }

    /** Creates the first Vault PIN; refuses to overwrite an existing PIN. */
    fun createVaultPin(newPin: String): Boolean {
        if (newPin.length != 4 || hasVaultPin()) return false
        val newHash = keystoreVaultManager.hashPin(newPin)
        return vaultPrefs.edit().putString("vault_pin_hash", newHash).commit()
    }
}
