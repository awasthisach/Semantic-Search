package com.example.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.viewModelScope
import com.example.data.FileItemEntity
import com.example.storage.MediaStoreMutationManager
import com.example.storage.PhysicalStorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.security.RecoverableSecurityException
import java.util.WeakHashMap

private data class PendingMediaStoreMove(
    val file: FileItemEntity,
    val trashPath: String,
    val originalPath: String
)

private class MediaStoreMutationState {
    val pendingRequest = MutableStateFlow<IntentSender?>(null)
    var pendingMove: PendingMediaStoreMove? = null
}

private val mediaStoreMutationStates = WeakHashMap<MainViewModel, MediaStoreMutationState>()

private fun MainViewModel.mediaStoreMutationState(): MediaStoreMutationState =
    synchronized(mediaStoreMutationStates) {
        mediaStoreMutationStates.getOrPut(this) { MediaStoreMutationState() }
    }

val MainViewModel.pendingMediaStoreDeleteIntentSender: StateFlow<IntentSender?>
    get() = mediaStoreMutationState().pendingRequest.asStateFlow()

/**
 * Starts a real MediaStore delete flow. User-owned MediaStore content is never
 * silently deleted: Android's consent UI is requested first, and local DB state
 * is changed only after RESULT_OK.
 */
fun MainViewModel.requestMoveToRecycleBin(file: FileItemEntity) {
    val state = mediaStoreMutationState()
    val context = getApplication<android.app.Application>().applicationContext

    if (!MediaStoreMutationManager.isMediaStoreUri(Uri.parse(file.path))) {
        moveToRecycleBin(file)
        return
    }

    viewModelScope.launch {
        try {
            val uri = Uri.parse(file.path)
            val trashDir = PhysicalStorageManager.getRecycleBinDir(context)
            val safeName = File(file.name).name.takeIf { it.isNotBlank() } ?: "file.bin"
            val trashFile = File(trashDir, "${System.currentTimeMillis()}_$safeName")
            context.contentResolver.openInputStream(uri)?.use { input ->
                trashFile.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("Unable to read MediaStore item for recycle-bin staging")

            state.pendingMove = PendingMediaStoreMove(file, trashFile.absolutePath, file.path)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                state.pendingRequest.value = MediaStoreMutationManager.createDeleteIntentSender(
                    context,
                    listOf(uri)
                )
            } else {
                try {
                    val deletedRows = context.contentResolver.delete(uri, null, null)
                    if (deletedRows > 0) {
                        finalizePendingMediaStoreMove(true)
                    } else {
                        throw IOException("MediaStore refused deletion")
                    }
                } catch (e: RecoverableSecurityException) {
                    state.pendingRequest.value = e.userAction.actionIntent.intentSender
                }
            }
        } catch (t: Throwable) {
            state.pendingMove?.let { try { File(it.trashPath).delete() } catch (_: Exception) {} }
            state.pendingMove = null
            state.pendingRequest.value = null
            clearGlobalError()
            android.util.Log.e("MediaStoreMutationCompat", "Unable to prepare MediaStore recycle operation", t)
        }
    }
}

/** Complete the pending MediaStore mutation only after Android reports the user's decision. */
fun MainViewModel.completePendingMediaStoreDelete(userApproved: Boolean) {
    val state = mediaStoreMutationState()
    if (!userApproved) {
        state.pendingMove?.let { try { File(it.trashPath).delete() } catch (_: Exception) {} }
        state.pendingMove = null
        state.pendingRequest.value = null
        return
    }

    viewModelScope.launch {
        val pending = state.pendingMove ?: return@launch
        try {
            val context = getApplication<android.app.Application>().applicationContext
            val uri = Uri.parse(pending.originalPath)
            val stillPresent = try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
            } catch (_: Exception) { false }

            if (stillPresent) {
                throw IOException("MediaStore item still exists after user-approved deletion")
            }

            val current = repository.getFileById(pending.file.id)
                ?: throw IOException("File metadata disappeared during MediaStore deletion")
            repository.insertFiles(
                listOf(
                    current.copy(
                        path = pending.trashPath,
                        originalPath = pending.originalPath,
                        isRecycleBin = true,
                        deletedTimestampMs = System.currentTimeMillis()
                    )
                )
            )
        } catch (t: Throwable) {
            android.util.Log.e("MediaStoreMutationCompat", "Failed to finalize approved MediaStore deletion", t)
            try { File(pending.trashPath).delete() } catch (_: Exception) {}
        } finally {
            state.pendingMove = null
            state.pendingRequest.value = null
        }
    }
}
