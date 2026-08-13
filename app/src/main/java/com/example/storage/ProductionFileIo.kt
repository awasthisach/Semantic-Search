package com.example.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Single I/O boundary for real file mutations.
 *
 * Shared-storage items must be represented by content:// URIs. Direct File I/O is
 * intentionally limited to app-owned paths. This prevents accidental reliance on
 * the legacy DATA column under scoped storage.
 */
object ProductionFileIo {
    private const val MAX_NAME_LENGTH = 255
    private const val COPY_BUFFER_SIZE = 64 * 1024

    fun validateFileName(name: String): Result<String> {
        if (name.isBlank()) return Result.failure(IllegalArgumentException("File name cannot be blank."))
        if (name == "." || name == "..") return Result.failure(IllegalArgumentException("Invalid file name."))
        if (name.length > MAX_NAME_LENGTH) return Result.failure(IllegalArgumentException("File name is too long."))
        if (name.any { it == '/' || it == '\\' || it.code < 0x20 || it == '\u007f' }) {
            return Result.failure(IllegalArgumentException("File name contains an invalid character."))
        }
        return Result.success(name)
    }

    fun rename(context: Context, source: String, newName: String): Result<String> {
        val validName = validateFileName(newName).getOrElse { return Result.failure(it) }
        return if (source.startsWith("content://")) {
            renameDocument(context, Uri.parse(source), validName)
        } else {
            renameLocal(source, validName)
        }
    }

    private fun renameDocument(context: Context, source: Uri, newName: String): Result<String> {
        return try {
            val document = DocumentFile.fromSingleUri(context, source)
                ?: return Result.failure(IOException("Document provider is unavailable."))
            if (!document.exists() || !document.isFile) {
                return Result.failure(IOException("Source document is unavailable."))
            }
            val flags = documentFlags(context, source)
            if (flags != null && flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME == 0) {
                return Result.failure(IOException("This document provider does not support rename."))
            }
            val renamed = DocumentsContract.renameDocument(context.contentResolver, source, newName)
                ?: return Result.failure(IOException("Document provider refused the rename."))
            Result.success(renamed.toString())
        } catch (e: SecurityException) {
            Result.failure(IOException("Write permission is required to rename this document.", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun renameLocal(sourcePath: String, newName: String): Result<String> {
        val source = File(sourcePath)
        if (!source.exists() || !source.isFile) return Result.failure(IOException("Source file does not exist."))
        val parent = source.parentFile ?: return Result.failure(IOException("Source has no parent directory."))
        val destination = File(parent, newName)
        if (destination.exists()) return Result.failure(IOException("A file with that name already exists."))

        if (source.renameTo(destination)) return Result.success(destination.absolutePath)

        // Cross-volume/filesystem fallback: copy completely, verify size, then delete source.
        return try {
            copyLocalToLocal(source, destination)
            if (source.delete()) Result.success(destination.absolutePath)
            else {
                destination.delete()
                Result.failure(IOException("Rename fallback copied the file but could not remove the source."))
            }
        } catch (e: Exception) {
            destination.delete()
            Result.failure(e)
        }
    }

    fun copy(context: Context, source: String, destination: String): Result<String> {
        if (source.startsWith("content://") && destination.startsWith("content://")) {
            return copyDocumentToDocument(context, Uri.parse(source), Uri.parse(destination))
        }
        if (!source.startsWith("content://") && !destination.startsWith("content://")) {
            return try {
                val src = File(source)
                val dst = File(destination)
                if (!src.exists() || !src.isFile) return Result.failure(IOException("Source file does not exist."))
                if (dst.exists()) return Result.failure(IOException("Destination already exists."))
                dst.parentFile?.mkdirs()
                copyLocalToLocal(src, dst)
                Result.success(dst.absolutePath)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        return if (source.startsWith("content://")) {
            copySourceToContent(context, Uri.parse(source), Uri.parse(destination))
        } else {
            copyLocalToContent(context, File(source), Uri.parse(destination))
        }
    }

    private fun copyDocumentToDocument(context: Context, source: Uri, destination: Uri): Result<String> {
        return try {
            val copied = DocumentsContract.copyDocument(context.contentResolver, source, destination)
                ?: return Result.failure(IOException("Document provider refused the copy."))
            Result.success(copied.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun copySourceToContent(context: Context, source: Uri, destination: Uri): Result<String> {
        return try {
            val input = context.contentResolver.openInputStream(source)
                ?: return Result.failure(IOException("Unable to read source document."))
            val output = context.contentResolver.openOutputStream(destination, "w")
                ?: return Result.failure(IOException("Unable to open destination document."))
            input.use { i -> output.use { o -> copyStream(i, o) } }
            Result.success(destination.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun copyLocalToContent(context: Context, source: File, destination: Uri): Result<String> {
        return try {
            if (!source.exists() || !source.isFile) return Result.failure(IOException("Source file does not exist."))
            val output = context.contentResolver.openOutputStream(destination, "w")
                ?: return Result.failure(IOException("Unable to open destination document."))
            FileInputStream(source).use { i -> output.use { o -> copyStream(i, o) } }
            Result.success(destination.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun delete(context: Context, path: String): Result<Unit> {
        return if (path.startsWith("content://")) {
            deleteDocument(context, Uri.parse(path))
        } else {
            val file = File(path)
            if (!file.exists()) Result.success(Unit)
            else if (file.delete()) Result.success(Unit)
            else Result.failure(IOException("Unable to delete source file."))
        }
    }

    private fun deleteDocument(context: Context, uri: Uri): Result<Unit> {
        return try {
            val deleted = if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } else {
                context.contentResolver.delete(uri, null, null) > 0
            }
            if (deleted) Result.success(Unit) else Result.failure(IOException("Document provider refused deletion."))
        } catch (e: SecurityException) {
            // The caller must surface a system consent request for MediaStore/SAF items.
            Result.failure(IOException("User authorization is required to delete this document.", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Moves a real file into the app-owned recycle bin. For a content URI the source
     * is copied first and deleted only after the copy succeeds; the database must not
     * be changed by the caller unless this method returns success.
     */
    fun moveToRecycleBin(context: Context, source: String): Result<String> {
        val sourceName = if (source.startsWith("content://")) {
            PhysicalStorageManager.getFileNameFromContentUri(context, Uri.parse(source))
        } else {
            File(source).name
        }
        validateFileName(sourceName).getOrElse { return Result.failure(it) }
        val trashDir = PhysicalStorageManager.getRecycleBinDir(context)
        if (!trashDir.exists() && !trashDir.mkdirs()) return Result.failure(IOException("Unable to create recycle bin."))
        val trashFile = uniqueDestination(trashDir, sourceName)

        return try {
            val copyResult = if (source.startsWith("content://")) {
                copyContentToLocal(context, Uri.parse(source), trashFile)
            } else {
                copyLocalToLocal(File(source), trashFile)
            }
            if (copyResult.isFailure) return Result.failure(copyResult.exceptionOrNull()!!)
            val deleteResult = delete(context, source)
            if (deleteResult.isFailure) {
                trashFile.delete()
                return Result.failure(deleteResult.exceptionOrNull()!!)
            }
            Result.success(trashFile.absolutePath)
        } catch (e: Exception) {
            trashFile.delete()
            Result.failure(e)
        }
    }

    fun restoreFromRecycleBin(context: Context, trashPath: String, originalPath: String): Result<String> {
        val trashFile = File(trashPath)
        if (!trashFile.exists() || !trashFile.isFile) return Result.failure(IOException("Recycle-bin source does not exist."))

        // A deleted content:// URI is not a stable restoration destination. Restore to
        // an app-owned external directory and return the new physical path instead.
        if (originalPath.startsWith("content://")) {
            val target = uniqueDestination(
                PhysicalStorageManager.getRestoredDir(context),
                trashFile.name.substringAfter('_', trashFile.name)
            )
            return try {
                copyLocalToLocal(trashFile, target)
                if (!trashFile.delete()) {
                    target.delete()
                    Result.failure(IOException("Restored copy created but recycle-bin source could not be removed."))
                } else Result.success(target.absolutePath)
            } catch (e: Exception) {
                target.delete()
                Result.failure(e)
            }
        }

        val target = File(originalPath)
        if (target.exists()) return Result.failure(IOException("Original destination already exists."))
        target.parentFile?.let { if (!it.exists() && !it.mkdirs()) return Result.failure(IOException("Unable to create restore directory.")) }
        return try {
            if (trashFile.renameTo(target)) return Result.success(target.absolutePath)
            copyLocalToLocal(trashFile, target)
            if (trashFile.delete()) Result.success(target.absolutePath)
            else {
                target.delete()
                Result.failure(IOException("Restore copy succeeded but trash source could not be removed."))
            }
        } catch (e: Exception) {
            target.delete()
            Result.failure(e)
        }
    }

    private fun copyContentToLocal(context: Context, source: Uri, destination: File): Result<String> {
        return try {
            val input = context.contentResolver.openInputStream(source)
                ?: return Result.failure(IOException("Unable to read source document."))
            destination.parentFile?.mkdirs()
            input.use { i -> FileOutputStream(destination).use { o -> copyStream(i, o) } }
            if (!destination.exists() || destination.length() == 0L) {
                destination.delete()
                Result.failure(IOException("Copied file is missing or empty."))
            } else Result.success(destination.absolutePath)
        } catch (e: Exception) {
            destination.delete()
            Result.failure(e)
        }
    }

    private fun copyLocalToLocal(source: File, destination: File) {
        if (!source.exists() || !source.isFile) throw IOException("Source file does not exist.")
        if (destination.exists()) throw IOException("Destination already exists.")
        destination.parentFile?.let { if (!it.exists() && !it.mkdirs()) throw IOException("Unable to create destination directory.") }
        FileInputStream(source).use { input -> FileOutputStream(destination).use { output -> copyStream(input, output) } }
        if (destination.length() != source.length()) throw IOException("Copy verification failed: size mismatch.")
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) output.write(buffer, 0, read)
        }
        output.flush()
    }

    private fun uniqueDestination(directory: File, requestedName: String): File {
        var candidate = File(directory, requestedName)
        if (!candidate.exists()) return candidate
        val base = requestedName.substringBeforeLast('.', requestedName)
        val extension = requestedName.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".${it}" }
        var index = 1
        while (candidate.exists()) {
            candidate = File(directory, "${base}_${index++}$extension")
        }
        return candidate
    }

    private fun documentFlags(context: Context, uri: Uri): Int? = try {
        context.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else null
        }
    } catch (_: Exception) {
        null
    }
}
