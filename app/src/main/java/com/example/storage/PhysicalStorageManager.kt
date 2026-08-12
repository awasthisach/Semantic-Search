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

    fun getRestoredDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "Restored")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getFileNameFromContentUri(context: Context, uri: Uri): String {
        try {
            val docName = DocumentFile.fromSingleUri(context, uri)?.name
            if (!docName.isNullOrBlank()) {
                return docName
            }
        } catch (_: Exception) {}

        try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) {
                            return name
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val lastSegment = uri.lastPathSegment
        if (!lastSegment.isNullOrBlank()) {
            return lastSegment
        }

        return "file_${System.currentTimeMillis()}"
    }

    fun getFileSizeFromContentUri(context: Context, uri: Uri): Long {
        try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        return cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {}
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            val statSize = pfd?.statSize ?: -1L
            pfd?.close()
            if (statSize >= 0) return statSize
        } catch (_: Exception) {}
        return -1L
    }

    fun getFileNameFromVaultPathOrUri(context: Context, vaultFilePath: String, uri: Uri): String {
        val vaultFile = File(vaultFilePath)
        val name = vaultFile.name
        if (name.startsWith("ENC_") && name.endsWith(".vvf")) {
            val withoutPrefix = name.removePrefix("ENC_").removeSuffix(".vvf")
            val extractedName = withoutPrefix.substringAfter("_")
            if (extractedName.isNotBlank() && extractedName != withoutPrefix) {
                return extractedName
            }
        }
        return getFileNameFromContentUri(context, uri)
    }

    fun getFileNameFromTrashPathOrUri(context: Context, trashFileName: String, uri: Uri): String {
        if (trashFileName.contains("_")) {
            val extractedName = trashFileName.substringAfter("_")
            if (extractedName.isNotBlank() && extractedName != trashFileName) {
                return extractedName
            }
        }
        return getFileNameFromContentUri(context, uri)
    }

    /**
     * Physical File Rename via File API, MediaStore ContentResolver, or SAF DocumentFile fallback.
     */
    fun renameFile(context: Context, oldPath: String, newName: String): Result<String> {
        val sanitizedName = File(newName).name
        if (sanitizedName.isBlank() || sanitizedName != newName) {
            return Result.failure(IllegalArgumentException("Invalid file name. Path traversal or directory changes are not allowed."))
        }

        if (oldPath.startsWith("content://")) {
            return try {
                val uri = Uri.parse(oldPath)
                val doc = DocumentFile.fromSingleUri(context, uri) ?: DocumentFile.fromTreeUri(context, uri)
                if (doc != null && doc.exists()) {
                    val renamed = doc.renameTo(newName)
                    if (renamed) {
                        Result.success(doc.uri.toString())
                    } else {
                        Result.failure(java.io.IOException("SAF DocumentFile rename failed for $oldPath"))
                    }
                } else {
                    Result.failure(java.io.FileNotFoundException("Document not found for URI $oldPath"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error renaming content URI $oldPath: ${e.message}", e)
                Result.failure(e)
            }
        }

        return try {
            val oldFile = File(oldPath)
            val parentDir = oldFile.parentFile
            val newFile = if (parentDir != null) File(parentDir, newName) else File(newName)

            var success = false
            if (oldFile.exists()) {
                // 1. Try direct Java File API for app-private/accessible filesystem paths
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
        if (path.startsWith("content://")) {
            return try {
                val uri = Uri.parse(path)
                val doc = DocumentFile.fromSingleUri(context, uri) ?: DocumentFile.fromTreeUri(context, uri)
                if (doc != null && !doc.exists()) {
                    return true
                }
                val deleted = doc?.delete() ?: false
                if (deleted) {
                    return true
                }
                val rows = try {
                    context.contentResolver.delete(uri, null, null)
                } catch (e: SecurityException) {
                    throw e
                } catch (_: Exception) {
                    0
                }
                if (rows > 0) {
                    return true
                }

                val stillExists = try {
                    if (doc != null) {
                        doc.exists()
                    } else {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
                    }
                } catch (_: Exception) {
                    false
                }

                !stillExists
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException deleting content URI $path: ${e.message}")
                false
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete content URI $path: ${e.message}")
                false
            }
        }

        val file = File(path)
        var deleted = false

        if (file.exists()) {
            try {
                deleted = file.delete()
            } catch (e: Exception) {
                Log.w(TAG, "Direct File.delete failed: ${e.message}")
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
        if (path.startsWith("content://")) {
            val trashDir = getRecycleBinDir(context)
            val uri = Uri.parse(path)
            val docName = try {
                DocumentFile.fromSingleUri(context, uri)?.name ?: "content_${System.currentTimeMillis()}.bin"
            } catch (_: Exception) {
                "content_${System.currentTimeMillis()}.bin"
            }
            val targetName = "${System.currentTimeMillis()}_${docName}"
            val trashFile = File(trashDir, targetName)

            try {
                val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                    trashFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                    true
                } ?: false

                if (!copied) {
                    try { if (trashFile.exists()) trashFile.delete() } catch (_: Exception) {}
                    return Result.failure(java.io.IOException("Failed to copy content URI to trash: $path"))
                }

                val doc = DocumentFile.fromSingleUri(context, uri) ?: DocumentFile.fromTreeUri(context, uri)
                val originalDeleted = doc?.delete() ?: (context.contentResolver.delete(uri, null, null) > 0)

                if (originalDeleted) {
                    notifyMediaStoreFileDeleted(context, path)
                    return Result.success(trashFile.absolutePath)
                } else {
                    try { if (trashFile.exists()) trashFile.delete() } catch (_: Exception) {}
                    return Result.failure(java.io.IOException("Failed to delete original content URI after trash copy: $path"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error moving content URI to trash $path: ${e.message}", e)
                try { if (trashFile.exists()) trashFile.delete() } catch (_: Exception) {}
                return Result.failure(e)
            }
        }

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
        if (originalPath.startsWith("content://")) {
            val trashFile = File(trashPath)
            if (!trashFile.exists()) {
                return Result.failure(java.io.FileNotFoundException("Trash file not found at $trashPath"))
            }

            return try {
                val uri = Uri.parse(originalPath)
                var writtenToOriginal = false
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        trashFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                        writtenToOriginal = true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not write to original content URI $originalPath: ${e.message}")
                    writtenToOriginal = false
                }

                if (writtenToOriginal) {
                    if (trashFile.delete()) {
                        notifyMediaStoreFileChanged(context, "", originalPath)
                        return Result.success(originalPath)
                    } else {
                        Log.w(TAG, "Restored content URI $originalPath but failed to delete trash file $trashPath")
                        return Result.success(originalPath)
                    }
                }

                // Original URI is deleted or unwritable. Restore to app-managed Restored directory.
                val restoredDir = getRestoredDir(context)
                val docName = getFileNameFromTrashPathOrUri(context, trashFile.name, uri)
                val restoredFile = File(restoredDir, docName)

                trashFile.copyTo(restoredFile, overwrite = true)

                if (!restoredFile.exists() || restoredFile.length() == 0L) {
                    try { if (restoredFile.exists()) restoredFile.delete() } catch (_: Exception) {}
                    return Result.failure(java.io.IOException("Failed to write restored file from trash to ${restoredFile.absolutePath}"))
                }

                if (!trashFile.delete() && trashFile.exists()) {
                    try { if (restoredFile.exists()) restoredFile.delete() } catch (_: Exception) {}
                    return Result.failure(IllegalStateException("Failed to delete trash file after restoration."))
                }

                notifyMediaStoreFileChanged(context, "", restoredFile.absolutePath)
                Result.success(restoredFile.absolutePath)
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring trash file $trashPath to content URI $originalPath: ${e.message}", e)
                Result.failure(e)
            }
        }

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
        if (originalPath.startsWith("content://")) {
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

                val uri = Uri.parse(originalPath)
                var writtenToOriginal = false
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(decryptedBytes)
                        writtenToOriginal = true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not write to original content URI $originalPath: ${e.message}")
                    writtenToOriginal = false
                }

                if (writtenToOriginal) {
                    val deletedVault = vaultFile.delete()
                    if (!deletedVault && vaultFile.exists()) {
                        Log.w(TAG, "Restored content URI $originalPath but failed to delete vault file $vaultFilePath")
                    }
                    notifyMediaStoreFileChanged(context, "", originalPath)
                    return Result.success(originalPath)
                }

                // Original URI deleted/unwritable. Restore to app-managed Restored directory.
                val restoredDir = getRestoredDir(context)
                val docName = getFileNameFromVaultPathOrUri(context, vaultFilePath, uri)
                val restoredFile = File(restoredDir, docName)

                FileOutputStream(restoredFile).use { fos ->
                    fos.write(decryptedBytes)
                }

                if (!restoredFile.exists() || restoredFile.length() == 0L) {
                    try { if (restoredFile.exists()) restoredFile.delete() } catch (_: Exception) {}
                    return Result.failure(java.io.IOException("Failed to write restored file at ${restoredFile.absolutePath}"))
                }

                val deletedVault = vaultFile.delete()
                if (!deletedVault && vaultFile.exists()) {
                    try { if (restoredFile.exists()) restoredFile.delete() } catch (_: Exception) {}
                    return Result.failure(IllegalStateException("Failed to delete encrypted vault source file."))
                }

                notifyMediaStoreFileChanged(context, "", restoredFile.absolutePath)
                Result.success(restoredFile.absolutePath)
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
        if (originalPath.startsWith("content://")) {
            return try {
                val vaultFile = File(vaultFilePath)
                if (!vaultFile.exists()) {
                    return Result.failure(java.io.FileNotFoundException("Vault file not found at $vaultFilePath"))
                }

                val uri = Uri.parse(originalPath)
                val cipher = keystoreVaultManager.getDecryptionCipher(iv)

                var writtenToOriginal = false
                try {
                    java.io.FileInputStream(vaultFile).use { fis ->
                        val cipherInputStream = javax.crypto.CipherInputStream(fis, cipher)
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            val buffer = ByteArray(65536)
                            var bytesRead: Int
                            while (cipherInputStream.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                            }
                            writtenToOriginal = true
                        }
                        cipherInputStream.close()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not write to original content URI $originalPath: ${e.message}")
                    writtenToOriginal = false
                }

                if (writtenToOriginal) {
                    val deletedVault = vaultFile.delete()
                    if (!deletedVault && vaultFile.exists()) {
                        Log.w(TAG, "Restored content URI $originalPath but failed to delete vault file $vaultFilePath")
                    }
                    notifyMediaStoreFileChanged(context, "", originalPath)
                    return Result.success(originalPath)
                }

                // Original URI deleted/unwritable. Restore to app-managed Restored directory.
                val restoredDir = getRestoredDir(context)
                val docName = getFileNameFromVaultPathOrUri(context, vaultFilePath, uri)
                val restoredFile = File(restoredDir, docName)

                val cipher2 = keystoreVaultManager.getDecryptionCipher(iv)
                java.io.FileInputStream(vaultFile).use { fis ->
                    val cipherInputStream = javax.crypto.CipherInputStream(fis, cipher2)
                    FileOutputStream(restoredFile).use { fos ->
                        val buffer = ByteArray(65536)
                        var bytesRead: Int
                        while (cipherInputStream.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                        }
                    }
                    cipherInputStream.close()
                }

                if (!restoredFile.exists() || restoredFile.length() == 0L) {
                    try { if (restoredFile.exists()) restoredFile.delete() } catch (_: Exception) {}
                    return Result.failure(java.io.IOException("Failed to write restored file at ${restoredFile.absolutePath}"))
                }

                val deletedVault = vaultFile.delete()
                if (!deletedVault && vaultFile.exists()) {
                    try { if (restoredFile.exists()) restoredFile.delete() } catch (_: Exception) {}
                    return Result.failure(IllegalStateException("Failed to delete encrypted vault source file."))
                }

                notifyMediaStoreFileChanged(context, "", restoredFile.absolutePath)
                Result.success(restoredFile.absolutePath)
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
        if (srcPath.startsWith("content://")) {
            return try {
                val uri = Uri.parse(srcPath)
                val fileBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return Result.failure(java.io.FileNotFoundException("Unable to open stream for content URI: $srcPath"))

                if (fileBytes.size > 50 * 1024 * 1024) {
                    return Result.failure(IllegalArgumentException("File size (${fileBytes.size / (1024 * 1024)}MB) exceeds the maximum secure vault limit of 50MB."))
                }

                val docName = getFileNameFromContentUri(context, uri)

                val (encryptedBytes, iv) = encryptAction(fileBytes)
                val vaultDir = getVaultDir(context)
                val encFileName = "ENC_${System.currentTimeMillis()}_${docName}.vvf"
                val vaultFile = File(vaultDir, encFileName)

                try {
                    FileOutputStream(vaultFile).use { fos ->
                        fos.write(encryptedBytes)
                    }
                } catch (writeEx: Exception) {
                    try { if (vaultFile.exists()) vaultFile.delete() } catch (_: Exception) {}
                    throw writeEx
                }

                if (!vaultFile.exists() || vaultFile.length() == 0L) {
                    try { if (vaultFile.exists()) vaultFile.delete() } catch (_: Exception) {}
                    return Result.failure(java.io.IOException("Vault file creation failed."))
                }

                val doc = DocumentFile.fromSingleUri(context, uri) ?: DocumentFile.fromTreeUri(context, uri)
                val originalDeleted = doc?.delete() ?: (context.contentResolver.delete(uri, null, null) > 0)

                if (originalDeleted) {
                    notifyMediaStoreFileDeleted(context, srcPath)
                    Result.success(
                        VaultStorageResult(
                            vaultFilePath = vaultFile.absolutePath,
                            encryptedFileName = encFileName,
                            iv = iv
                        )
                    )
                } else {
                    try { if (vaultFile.exists()) vaultFile.delete() } catch (_: Exception) {}
                    Result.failure(java.io.IOException("Failed to delete original content URI after vault encryption: $srcPath"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error encrypting content URI $srcPath: ${e.message}", e)
                Result.failure(e)
            }
        }

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
        if (srcPath.startsWith("content://")) {
            return try {
                val uri = Uri.parse(srcPath)
                val docName = getFileNameFromContentUri(context, uri)

                val vaultDir = getVaultDir(context)
                val encFileName = "ENC_${System.currentTimeMillis()}_${docName}.vvf"
                val vaultFile = File(vaultDir, encFileName)

                val cipher = keystoreVaultManager.getEncryptionCipher()
                val iv = cipher.iv

                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return Result.failure(java.io.FileNotFoundException("Unable to open stream for content URI: $srcPath"))

                var written = false
                try {
                    inputStream.use { fis ->
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
                    written = true
                } catch (writeEx: Exception) {
                    try { if (vaultFile.exists()) vaultFile.delete() } catch (_: Exception) {}
                    throw writeEx
                }

                if (!written || !vaultFile.exists() || vaultFile.length() == 0L) {
                    try { if (vaultFile.exists()) vaultFile.delete() } catch (_: Exception) {}
                    return Result.failure(java.io.IOException("Vault file creation failed or empty."))
                }

                val doc = DocumentFile.fromSingleUri(context, uri) ?: DocumentFile.fromTreeUri(context, uri)
                val originalDeleted = doc?.delete() ?: (context.contentResolver.delete(uri, null, null) > 0)

                if (originalDeleted) {
                    notifyMediaStoreFileDeleted(context, srcPath)
                    Result.success(
                        VaultStorageResult(
                            vaultFilePath = vaultFile.absolutePath,
                            encryptedFileName = encFileName,
                            iv = iv
                        )
                    )
                } else {
                    try { if (vaultFile.exists()) vaultFile.delete() } catch (_: Exception) {}
                    Result.failure(java.io.IOException("Failed to delete original content URI after vault encryption: $srcPath"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error encrypting content URI $srcPath: ${e.message}", e)
                Result.failure(e)
            }
        }

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
        if (newPath.startsWith("content://")) return
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
