package com.example.data

import android.content.Context
import android.util.Base64
import com.example.security.KeystoreVaultManager
import com.example.storage.PhysicalStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VaultRepository(
    private val context: Context,
    private val dao: FileDao,
    private val keystoreVaultManager: KeystoreVaultManager,
    private val vaultManagerEngine: VaultManagerEngine = VaultManagerEngine(context, keystoreVaultManager)
) {
    fun getStoredVaultPinHash(): String = vaultManagerEngine.getStoredVaultPinHash()
    fun hasVaultPin(): Boolean = vaultManagerEngine.hasVaultPin()
    fun createVaultPin(newPin: String): Boolean = vaultManagerEngine.createVaultPin(newPin)
    fun verifyVaultPin(inputPin: String, storedHash: String = ""): Boolean = vaultManagerEngine.verifyVaultPin(inputPin, storedHash)
    fun changeVaultPin(oldPin: String, newPin: String): Boolean = vaultManagerEngine.changeVaultPin(oldPin, newPin)

    suspend fun encryptToVault(file: FileItemEntity) = withContext(Dispatchers.IO) {
        val vaultStorageResult = PhysicalStorageManager.encryptAndWipeSource(
            context,
            file.path,
            keystoreVaultManager,
        )

        if (vaultStorageResult.isSuccess) {
            val res = vaultStorageResult.getOrThrow()
            val ivBase64 = Base64.encodeToString(res.iv, Base64.NO_WRAP)
            val vaultItem = VaultItemEntity(
                originalName = file.name,
                encryptedName = res.encryptedFileName,
                encryptedFilePath = res.vaultFilePath,
                ivBase64 = ivBase64,
                category = file.category,
                sizeBytes = file.sizeBytes,
            )

            // The physical source has already been encrypted and removed. Commit
            // both metadata records atomically so the database cannot expose a
            // half-vaulted state when one write fails.
            dao.commitVaultEncryption(file.copy(isVault = true), vaultItem)
        } else {
            throw vaultStorageResult.exceptionOrNull()
                ?: java.io.IOException("Failed to encrypt and wipe source file")
        }
    }

    suspend fun unlockFromVault(vaultItem: VaultItemEntity, file: FileItemEntity?): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val targetFile = file ?: dao.getVaultFileByName(vaultItem.originalName)

                // Never discard the only encrypted copy merely because the original
                // metadata entry is missing. The encrypted vault item remains recoverable.
                if (targetFile == null) {
                    throw java.io.FileNotFoundException(
                        "Original file metadata is missing; encrypted vault data was preserved.",
                    )
                }

                val encryptedVaultFile = java.io.File(vaultItem.encryptedFilePath)
                if (!encryptedVaultFile.exists() || !encryptedVaultFile.isFile) {
                    throw java.io.FileNotFoundException(
                        "Encrypted vault file is missing: ${vaultItem.encryptedFilePath}",
                    )
                }

                val iv = Base64.decode(vaultItem.ivBase64, Base64.DEFAULT)
                require(iv.size == 12) { "Invalid vault IV." }

                val decryptResult = PhysicalStorageManager.decryptAndRestore(
                    context,
                    vaultItem.encryptedFilePath,
                    targetFile.path,
                    iv,
                    keystoreVaultManager,
                )
                if (decryptResult.isSuccess) {
                    dao.commitVaultRestoration(
                        targetFile.copy(isVault = false, path = decryptResult.getOrThrow()),
                        vaultItem.id,
                    )
                    true
                } else {
                    throw decryptResult.exceptionOrNull()
                        ?: java.io.IOException("Failed to physically decrypt vault file")
                }
            } catch (e: Exception) {
                android.util.Log.e(
                    "VaultRepository",
                    "Failed to unlock from vault: ${e.message}",
                    e,
                )
                throw e
            }
        }
}
