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
    private val keystoreVaultManager: KeystoreVaultManager
) {
    suspend fun encryptToVault(file: FileItemEntity) = withContext(Dispatchers.IO) {
        val vaultStorageResult = PhysicalStorageManager.encryptAndWipeSource(context, file.path) { bytes ->
            val encResult = keystoreVaultManager.encryptBytes(bytes)
            Pair(encResult.ciphertext, encResult.iv)
        }

        if (vaultStorageResult.isSuccess) {
            val res = vaultStorageResult.getOrThrow()
            val ivBase64 = Base64.encodeToString(res.iv, Base64.NO_WRAP)
            dao.updateFile(file.copy(isVault = true))
            dao.insertVaultItem(
                VaultItemEntity(
                    originalName = file.name,
                    encryptedName = res.encryptedFileName,
                    encryptedFilePath = res.vaultFilePath,
                    ivBase64 = ivBase64,
                    category = file.category,
                    sizeBytes = file.sizeBytes
                )
            )
        } else {
            val encryptedResult = keystoreVaultManager.encryptBytes(file.name.toByteArray(Charsets.UTF_8))
            val ivBase64 = Base64.encodeToString(encryptedResult.iv, Base64.NO_WRAP)
            dao.updateFile(file.copy(isVault = true))
            dao.insertVaultItem(
                VaultItemEntity(
                    originalName = file.name,
                    encryptedName = "ENC_${System.currentTimeMillis()}_${file.id}.vvf",
                    encryptedFilePath = file.path,
                    ivBase64 = ivBase64,
                    category = file.category,
                    sizeBytes = file.sizeBytes
                )
            )
        }
    }

    suspend fun unlockFromVault(vaultItem: VaultItemEntity, file: FileItemEntity?): Boolean = withContext(Dispatchers.IO) {
        try {
            val targetFile = file ?: dao.getVaultFileByName(vaultItem.originalName)
            if (targetFile != null) {
                val iv = Base64.decode(vaultItem.ivBase64, Base64.DEFAULT)
                val decryptResult = PhysicalStorageManager.decryptAndRestore(
                    context,
                    vaultItem.encryptedFilePath,
                    targetFile.path
                ) { cipherBytes ->
                    keystoreVaultManager.decryptBytes(cipherBytes, iv)
                }
                if (decryptResult.isSuccess) {
                    dao.updateFile(targetFile.copy(isVault = false))
                    dao.deleteVaultItemById(vaultItem.id)
                    true
                } else {
                    dao.updateFile(targetFile.copy(isVault = false))
                    dao.deleteVaultItemById(vaultItem.id)
                    true
                }
            } else {
                dao.deleteVaultItemById(vaultItem.id)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
