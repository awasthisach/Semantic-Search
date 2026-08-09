package com.example.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream

data class VaultStorageResult(
    val vaultFilePath: String,
    val encryptedFileName: String,
    val iv: ByteArray
)

object PhysicalStorageManager {
    private const val TAG = "PhysicalStorageManager"

    fun getRecycleBinDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, ".recycle_bin")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getVaultDir(context: Context): File {
        val dir = File(context.filesDir, ".vault")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Physical File Rename via File API, MediaStore ContentResolver, or SAF DocumentFile fallback.
     */
    fun renameFile(context: Context, oldPath: String, newName: String): Result<String> {
        val sanitizedName = File(newName).name
        if (sanitizedName.isBlank() || sanitizedName != newName) {
            return Result.failure(IllegalArgumentException("Invalid file name. Path traversal or directory changes are not allowed."))
        }
        return try {
            val oldFile = File(oldPath)
            val parentDir = oldFile.parentFile
            val newFile = if (parentDir != null) File(parentDir, newName) else File(newName)

            var success = false
            if (oldFile.exists()) {
                // 1. Try direct Java File API
                try {
                    success = oldFile.renameTo(newFile)
                } catch (e: Exception) {
                    Log.w(TAG, "Direct File.renameTo failed: ${e.message}")
                }

                // 2. Fallback to copy and delete if rename fails
                if (!success) {
                    try {
                        oldFile.copyTo(newFile, overwrite = true)
                        if (oldFile.delete()) {
                            success = true
                        } else {
                            try { if (newFile.exists()) newFile.delete() } catch (_: Exception) {}
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Fallback copy-and-delete failed: ${e.message}")
                        try { if (newFile.exists()) newFile.delete() } catch (_: Exception) {}
                    }
                }

                // 3. Fallback to SAF DocumentFile
                if (!success) {
                    try {
                        val doc = DocumentFile.fromFile(oldFile)
                        if (doc.exists()) {
                            success = doc.renameTo(newName)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "SAF DocumentFile rename failed: ${e.message}")
                    }
                }
            } else {
                return Result.failure(java.io.FileNotFoundException("Source file not found at $oldPath"))
            }

            // Also attempt MediaStore update for system index
            updateMediaStoreDisplayName(context, oldPath, newName)

            val finalPath = if (newFile.exists()) newFile.absolutePath else if (success) newFile.absolutePath else oldPath
            if (success || newFile.exists()) {
                notifyMediaStoreFileChanged(context, oldPath, finalPath)
                Result.success(finalPath)
            } else {
                Result.failure(java.io.IOException("Failed to physically rename file $oldPath to $newName"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error renaming file physically: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Physical File Delete via File API, MediaStore ContentResolver, and SAF fallback.
     */
    fun deleteFile(context: Context, path: String): Boolean {
        val file = File(path)
        var deleted = false

        if (file.exists()) {
            try {
                deleted = file.delete()
            } catch (e: Exception) {
                Log.w(TAG, "Direct File.delete failed: ${e.message}")
            }

            if (!deleted) {
                try {
                    val doc = DocumentFile.fromFile(file)
                    deleted = doc.delete()
                } catch (e: Exception) {
                    Log.w(TAG, "SAF DocumentFile delete failed: ${e.message}")
                }
            }
        }

        // Try MediaStore ContentResolver deletion
        val mediaStoreDeleted = deleteFromMediaStore(context, path)
        if (mediaStoreDeleted) deleted = true

        if (deleted) {
            notifyMediaStoreFileDeleted(context, path)
        }
        return deleted || !file.exists()
    }

    /**
     * Real Physical Move to App-Private Trash Directory
     */
    fun moveToTrash(context: Context, path: String): Result<String> {
        val srcFile = File(path)
        val trashDir = getRecycleBinDir(context)
        val targetName = "${System.currentTimeMillis()}_${srcFile.name}"
        val trashFile = File(trashDir, targetName)

        var moved = false
        if (srcFile.exists()) {
            try {
                moved = srcFile.renameTo(trashFile)
            } catch (e: Exception) {
                Log.w(TAG, "Direct rename to trash failed, attempting copy: ${e.message}")
            }

            if (!moved) {
                try {
                    srcFile.copyTo(trashFile, overwrite = true)
                    if (srcFile.delete() || deleteFromMediaStore(context, path)) {
                        moved = true
                    } else {
                        try { if (trashFile.exists()) trashFile.delete() } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Copy to trash failed: ${e.message}", e)
                    try { if (trashFile.exists()) trashFile.delete() } catch (_: Exception) {}
                }
            }
        } else {
            return Result.failure(java.io.FileNotFoundException("Source file not found at $path"))
        }

        if (moved && trashFile.exists()) {
            notifyMediaStoreFileDeleted(context, path)
            return Result.success(trashFile.absolutePath)
        } else {
            return Result.failure(java.io.IOException("Failed to move file to trash: $path"))
        }
    }

    /**
     * Restore Physical File from App-Private Trash Directory to Original Path
     */
    fun restoreFromTrash(context: Context, trashPath: String, originalPath: String): Result<String> {
        val trashFile = File(trashPath)
        val targetFile = File(originalPath)
        targetFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

        var restored = false
        if (trashFile.exists()) {
            try {
                restored = trashFile.renameTo(targetFile)
            } catch (e: Exception) {
                Log.w(TAG, "Direct rename from trash failed: ${e.message}")
            }

            if (!restored) {
                try {
                    trashFile.copyTo(targetFile, overwrite = true)
                    if (trashFile.delete()) {
                        restored = true
                    } else {
                        try { if (targetFile.exists()) targetFile.delete() } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Restore copy from trash failed: ${e.message}", e)
                    try { if (targetFile.exists()) targetFile.delete() } catch (_: Exception) {}
                }
            }
        } else {
            return Result.failure(java.io.FileNotFoundException("Trash file not found at $trashPath"))
        }

        if (restored && targetFile.exists()) {
            notifyMediaStoreFileChanged(context, "", targetFile.absolutePath)
            return Result.success(targetFile.absolutePath)
        } else {
            return Result.failure(java.io.IOException("Failed to restore file from trash: $trashPath"))
        }
    }

    /**
     * Physically decrypt file from Secure Vault and restore it to original path
     */
    fun decryptAndRestore(
        context: Context,
        vaultFilePath: String,
        originalPath: String,
        decryptAction: (ByteArray) -> ByteArray
    ): Result<String> {
        return try {
            val vaultFile = File(vaultFilePath)
            if (!vaultFile.exists()) {
                return Result.failure(java.io.FileNotFoundException("Vault file not found at $vaultFilePath"))
            }
            if (vaultFile.length() > 50 * 1024 * 1024L) {
                return Result.failure(IllegalArgumentException("Vault file size (${vaultFile.length() / (1024 * 1024)}MB) exceeds the maximum secure vault limit of 50MB to prevent OutOfMemoryError."))
            }
            val encryptedBytes = vaultFile.readBytes()
            val decryptedBytes = decryptAction(encryptedBytes)

            val targetFile = File(originalPath)
            targetFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

            try {
                FileOutputStream(targetFile).use { fos ->
                    fos.write(decryptedBytes)
                }
            } catch (writeEx: Exception) {
                try { if (targetFile.exists()) targetFile.delete() } catch (_: Exception) {}
                throw writeEx
            }

            // Delete the encrypted file from vault
            val deletedVault = vaultFile.delete()
            if (!deletedVault && vaultFile.exists()) {
                try { if (targetFile.exists()) targetFile.delete() } catch (_: Exception) {}
                return Result.failure(IllegalStateException("Failed to delete encrypted vault source file."))
            }

            notifyMediaStoreFileChanged(context, "", targetFile.absolutePath)
            Result.success(targetFile.absolutePath)
        } catch (e: javax.crypto.AEADBadTagException) {
            Log.e(TAG, "AEADBadTagException in decryptAndRestore: Incorrect PIN or tampered vault data", e)
            val msg = "Decryption failed: Incorrect PIN or tampered vault data."
            Result.failure(java.security.GeneralSecurityException(msg, e))
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError in decryptAndRestore: ${e.message}")
            System.gc()
            Result.failure(e)
        } catch (e: java.io.IOException) {
            Log.e(TAG, "IOException in decryptAndRestore: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt and restore: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Stream-based AES-GCM Decryption from Secure Vault restoring to original path
     */
    fun decryptAndRestore(
        context: Context,
        vaultFilePath: String,
        originalPath: String,
        iv: ByteArray,
        keystoreVaultManager: com.example.security.KeystoreVaultManager
    ): Result<String> {
        return try {
            val vaultFile = File(vaultFilePath)
            if (!vaultFile.exists()) {
                return Result.failure(java.io.FileNotFoundException("Vault file not found at $vaultFilePath"))
            }

            val targetFile = File(originalPath)
            targetFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

            val cipher = keystoreVaultManager.getDecryptionCipher(iv)

            java.io.FileInputStream(vaultFile).use { fis ->
                val cipherInputStream = javax.crypto.CipherInputStream(fis, cipher)
                FileOutputStream(targetFile).use { fos ->
                    val buffer = ByteArray(65536)
                    var bytesRead: Int
                    while (cipherInputStream.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                    }
                }
                cipherInputStream.close()
            }

            // Delete the encrypted file from vault
            val deletedVault = vaultFile.delete()
            if (!deletedVault && vaultFile.exists()) {
                try { if (targetFile.exists()) targetFile.delete() } catch (_: Exception) {}
                return Result.failure(IllegalStateException("Failed to delete encrypted vault source file."))
            }

            notifyMediaStoreFileChanged(context, "", targetFile.absolutePath)
            Result.success(targetFile.absolutePath)
        } catch (e: javax.crypto.AEADBadTagException) {
            Log.e(TAG, "AEADBadTagException in decryptAndRestore Stream: Incorrect PIN or tampered vault data", e)
            val msg = "Decryption failed: Incorrect PIN or tampered vault data."
            Result.failure(java.security.GeneralSecurityException(msg, e))
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError in decryptAndRestore Stream: ${e.message}")
            System.gc()
            Result.failure(e)
        } catch (e: java.io.IOException) {
            Log.e(TAG, "IOException in decryptAndRestore Stream: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt and restore Stream: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Physically encrypt file into Secure Vault and physically DELETE/WIPE source file
     */
    fun encryptAndWipeSource(
        context: Context,
        srcPath: String,
        encryptAction: (ByteArray) -> Pair<ByteArray, ByteArray>
    ): Result<VaultStorageResult> {
        val srcFile = File(srcPath)
        return try {
            if (srcFile.exists() && srcFile.length() > 50 * 1024 * 1024L) {
                return Result.failure(IllegalArgumentException("File size (${srcFile.length() / (1024 * 1024)}MB) exceeds the maximum secure vault limit of 50MB to prevent OutOfMemoryError."))
            }
            val fileBytes = if (srcFile.exists() && srcFile.canRead()) {
                srcFile.readBytes()
            } else {
                srcPath.toByteArray(Charsets.UTF_8)
            }

            val (encryptedBytes, iv) = encryptAction(fileBytes)
            val vaultDir = getVaultDir(context)
            val encFileName = "ENC_${System.currentTimeMillis()}_${srcFile.name}.vvf"
            val vaultFile = File(vaultDir, encFileName)

            try {
                FileOutputStream(vaultFile).use { fos ->
                    fos.write(encryptedBytes)
                }
            } catch (writeEx: Exception) {
                try { if (vaultFile.exists()) vaultFile.delete() } catch (_: Exception) {}
                throw writeEx
            }

            // Secure physical wipe of original source file if it exists
            if (srcFile.exists()) {
                secureWipeFile(context, srcFile)
            }

            Result.success(
                VaultStorageResult(
                    vaultFilePath = vaultFile.absolutePath,
                    encryptedFileName = encFileName,
                    iv = iv
                )
            )
        } catch (e: javax.crypto.AEADBadTagException) {
            Log.e(TAG, "AEADBadTagException in encryptAndWipeSource: Tampered key or data", e)
            val msg = "Encryption failed: Incorrect key or tampered data."
            Result.failure(java.security.GeneralSecurityException(msg, e))
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError in encryptAndWipeSource: ${e.message}")
            System.gc()
            Result.failure(e)
        } catch (e: java.io.IOException) {
            Log.e(TAG, "IOException in encryptAndWipeSource: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt and wipe source: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Stream-based AES-GCM Encryption into Secure Vault and physical deletion/wipe of source file
     */
    fun encryptAndWipeSource(
        context: Context,
        srcPath: String,
        keystoreVaultManager: com.example.security.KeystoreVaultManager
    ): Result<VaultStorageResult> {
        val srcFile = File(srcPath)
        return try {
            val vaultDir = getVaultDir(context)
            val encFileName = "ENC_${System.currentTimeMillis()}_${srcFile.name}.vvf"
            val vaultFile = File(vaultDir, encFileName)

            val cipher = keystoreVaultManager.getEncryptionCipher()
            val iv = cipher.iv

            if (srcFile.exists() && srcFile.canRead()) {
                java.io.FileInputStream(srcFile).use { fis ->
                    FileOutputStream(vaultFile).use { fos ->
                        val cipherOutputStream = javax.crypto.CipherOutputStream(fos, cipher)
                        val buffer = ByteArray(65536)
                        var bytesRead: Int
                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            cipherOutputStream.write(buffer, 0, bytesRead)
                        }
                        cipherOutputStream.close()
                    }
                }
            } else {
                val fileBytes = srcPath.toByteArray(Charsets.UTF_8)
                FileOutputStream(vaultFile).use { fos ->
                    val cipherOutputStream = javax.crypto.CipherOutputStream(fos, cipher)
                    cipherOutputStream.write(fileBytes)
                    cipherOutputStream.close()
                }
            }

            // Secure physical wipe of original source file if it exists
            if (srcFile.exists()) {
                secureWipeFile(context, srcFile)
            }

            Result.success(
                VaultStorageResult(
                    vaultFilePath = vaultFile.absolutePath,
                    encryptedFileName = encFileName,
                    iv = iv
                )
            )
        } catch (e: javax.crypto.AEADBadTagException) {
            Log.e(TAG, "AEADBadTagException in encryptAndWipeSource Stream: Tampered key or data", e)
            val msg = "Encryption failed: Incorrect key or tampered data."
            Result.failure(java.security.GeneralSecurityException(msg, e))
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError in encryptAndWipeSource Stream: ${e.message}")
            System.gc()
            Result.failure(e)
        } catch (e: java.io.IOException) {
            Log.e(TAG, "IOException in encryptAndWipeSource Stream: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt and wipe source Stream: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun secureWipeFile(context: Context, file: File) {
        if (!file.exists() || !file.canWrite()) return
        try {
            val length = file.length()
            if (length > 0) {
                val secureRandom = java.security.SecureRandom()
                val bufferSize = 65536
                val buffer = ByteArray(bufferSize)

                // 3 Passes to ensure thorough erasure matching the actual file size
                // Pass 1: Secure Random
                // Pass 2: Zeros (0x00)
                // Pass 3: Secure Random
                val passes = listOf("random", "zeros", "random")

                for (pass in passes) {
                    java.io.RandomAccessFile(file, "rws").use { raf ->
                        raf.seek(0)
                        var remaining = length
                        while (remaining > 0) {
                            val toWrite = remaining.coerceAtMost(bufferSize.toLong()).toInt()
                            when (pass) {
                                "zeros" -> buffer.fill(0)
                                "random" -> secureRandom.nextBytes(buffer)
                            }
                            raf.write(buffer, 0, toWrite)
                            remaining -= toWrite
                        }
                        try {
                            raf.fd.sync()
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to securely overwrite file contents: ${e.message}", e)
        } finally {
            try {
                deleteFile(context, file.absolutePath)
            } catch (_: Exception) {}
        }
    }

    private fun updateMediaStoreDisplayName(context: Context, oldPath: String, newName: String): Boolean {
        return try {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Files.getContentUri("external")
            }
            val where = "${MediaStore.Files.FileColumns.DATA} = ?"
            val args = arrayOf(oldPath)

            val values = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, newName)
            }
            val rows = context.contentResolver.update(collection, values, where, args)
            rows > 0
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore update failed: ${e.message}")
            false
        }
    }

    private fun deleteFromMediaStore(context: Context, path: String): Boolean {
        return try {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Files.getContentUri("external")
            }
            val where = "${MediaStore.Files.FileColumns.DATA} = ?"
            val args = arrayOf(path)
            val count = context.contentResolver.delete(collection, where, args)
            count > 0
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore delete failed: ${e.message}")
            false
        }
    }

    private fun notifyMediaStoreFileChanged(context: Context, oldPath: String, newPath: String) {
        try {
            val file = File(newPath)
            if (file.exists()) {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    null
                ) { path, uri ->
                    Log.d(TAG, "Scanned $path -> $uri")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to notify media scanner: ${e.message}")
        }
    }

    private fun notifyMediaStoreFileDeleted(context: Context, path: String) {
        deleteFromMediaStore(context, path)
    }
}
