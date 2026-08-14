package com.example.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
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
 * Stages a user-owned MediaStore item in the private recycle bin and requests
 * Android's explicit user-consent deletion flow. DB state is not changed until
 * the platform reports a successful deletion.
 */
fun MainViewModel.requestMoveToRecycleBin(file: FileItemEntity) {
    val state = mediaStoreMutationState()
    val context = getApplication<android.app.Application>().applicationContext

    if (!MediaStoreMutationManager.isMediaStoreUri(Uri.parse(file.path))) {
        viewModelScope.launch { repository.moveToRecycleBin(file) }
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

            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    val request = MediaStoreMutationManager.createDeleteRequest(context, listOf(uri))
                    state.pendingRequest.value = request.intentSender
                    request.send(context, 0, null, object : PendingIntent.OnFinished {
                        override fun onSendFinished(
                            pendingIntent: PendingIntent?,
                            intent: android.content.Intent?,
                            resultCode: Int,
                            resultData: String?,
                            resultExtras: android.os.Bundle?
                        ) {
                            completePendingMediaStoreDelete(resultCode == Activity.RESULT_OK)
                        }
                    }, null)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    deleteMediaStoreOnAndroid10(context, uri, state)
                }
                else -> {
                    val deletedRows = context.contentResolver.delete(uri, null, null)
                    if (deletedRows > 0) finalizeApprovedMediaStoreMove()
                    else throw IOException("MediaStore refused deletion")
                }
            }
        } catch (t: Throwable) {
            state.pendingMove?.let { try { File(it.trashPath).delete() } catch (_: Exception) {} }
            state.pendingMove = null
            state.pendingRequest.value = null
            android.util.Log.e("MediaStoreMutationCompat", "Unable to prepare MediaStore recycle operation", t)
        }
    }
}

/**
 * Android 10 (API 29) uses RecoverableSecurityException for user-owned
 * MediaStore items; API 30+ uses MediaStore.createDeleteRequest().
 */
@RequiresApi(Build.VERSION_CODES.Q)
@SuppressLint("NewApi")
private suspend fun MainViewModel.deleteMediaStoreOnAndroid10(
    context: android.content.Context,
    uri: Uri,
    state: MediaStoreMutationState
) {
    try {
        val deletedRows = context.contentResolver.delete(uri, null, null)
        if (deletedRows > 0) finalizeApprovedMediaStoreMove()
        else throw IOException("MediaStore refused deletion")
    } catch (e: RecoverableSecurityException) {
        state.pendingRequest.value = e.userAction.actionIntent.intentSender
        android.util.Log.i(
            "MediaStoreMutationCompat",
            "Android 10 requires user consent before deleting MediaStore item"
        )
    }
}

/** Completes the pending mutation only after Android reports the user's decision. */
fun MainViewModel.completePendingMediaStoreDelete(userApproved: Boolean) {
    val state = mediaStoreMutationState()
    if (!userApproved) {
        state.pendingMove?.let { try { File(it.trashPath).delete() } catch (_: Exception) {} }
        state.pendingMove = null
        state.pendingRequest.value = null
        return
    }

    viewModelScope.launch {
        try {
            finalizeApprovedMediaStoreMove()
        } catch (t: Throwable) {
            android.util.Log.e("MediaStoreMutationCompat", "Failed to finalize approved MediaStore deletion", t)
        }
    }
}

private suspend fun MainViewModel.finalizeApprovedMediaStoreMove() {
    val state = mediaStoreMutationState()
    val pending = state.pendingMove ?: return
    try {
        val context = getApplication<android.app.Application>().applicationContext
        val uri = Uri.parse(pending.originalPath)
        val stillPresent = try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
        if (stillPresent) throw IOException("MediaStore item still exists after user-approved deletion")

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
    } finally {
        state.pendingMove = null
        state.pendingRequest.value = null
    }
}
