package com.example.storage

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi

/**
 * Android 10+ MediaStore mutation boundary.
 *
 * Direct resolver.delete() is not a valid general-purpose destructive path for
 * user-owned media under scoped storage. Callers must launch the returned
 * IntentSender and handle the user's decision before updating local metadata.
 */
object MediaStoreMutationManager {

    fun isMediaStoreUri(uri: Uri): Boolean =
        uri.scheme == ContentResolver.SCHEME_CONTENT &&
            (uri.authority == MediaStore.AUTHORITY ||
                uri.authority?.contains("media", ignoreCase = true) == true)

    /**
     * Returns a user-consent request for deleting the supplied MediaStore URIs.
     * The caller owns the ActivityResult launch and must only commit DB changes
     * after RESULT_OK.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun createDeleteRequest(context: Context, uris: List<Uri>): PendingIntent {
        require(uris.isNotEmpty()) { "At least one MediaStore URI is required" }
        return MediaStore.createDeleteRequest(context.contentResolver, uris)
    }

    /**
     * Returns the IntentSender needed by an ActivityResultLauncher.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun createDeleteIntentSender(context: Context, uris: List<Uri>): IntentSender =
        createDeleteRequest(context, uris).intentSender

    /**
     * For Android 10, callers should use the platform's RecoverableSecurityException
     * flow when resolver.delete() rejects a user-owned MediaStore item. This helper
     * deliberately does not swallow SecurityException or pretend deletion succeeded.
     */
    fun deleteWithoutConsent(context: Context, uri: Uri): Result<Boolean> {
        return try {
            val rows = context.contentResolver.delete(uri, null, null)
            Result.success(rows > 0)
        } catch (e: SecurityException) {
            Result.failure(e)
        }
    }

    /**
     * Safe mutation for an item already owned by the application. This is useful
     * for files inserted by this app and avoids incorrectly requesting user consent.
     */
    fun updateDisplayName(context: Context, uri: Uri, newName: String): Result<Boolean> {
        require(newName.isNotBlank()) { "File name must not be blank" }
        return try {
            val values = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
            }
            Result.success(context.contentResolver.update(uri, values, null, null) > 0)
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
