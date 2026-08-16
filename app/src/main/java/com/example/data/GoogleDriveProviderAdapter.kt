package com.example.data

import android.net.Uri
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Google Drive implementation of [CloudProviderAdapter].
 *
 * Network responses are always closed, request metadata is encoded as JSON rather than
 * interpolated strings, and downloads are committed atomically so an interrupted transfer
 * cannot replace a valid local file with a partial file.
 */
class GoogleDriveProviderAdapter(
    private val authManager: GoogleAuthManager,
    private val httpClient: OkHttpClient = OkHttpClient()
) : CloudProviderAdapter {

    override val providerId: String = "GOOGLE_DRIVE"

    override suspend fun uploadFile(file: File, remotePath: String): CloudSyncResult {
        if (!file.exists() || !file.isFile || !file.canRead()) {
            return CloudSyncResult.Error(
                message = "File does not exist or is not readable: ${file.absolutePath}",
                isRetryable = false
            )
        }

        val token = authManager.getAccessToken() ?: return CloudSyncResult.Error(
            message = "Upload failed: user is not authenticated with Google Drive.",
            isRetryable = false
        )

        return try {
            val mimeType = determineMimeType(file)
            val metadataJson = JSONObject()
                .put("name", remotePath)
                .put("description", "Uploaded via VVF Smart Manager")
                .toString()

            val initRequest = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable")
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("X-Upload-Content-Type", mimeType)
                .header("X-Upload-Content-Length", file.length().toString())
                .post(
                    metadataJson.toRequestBody(
                        "application/json; charset=UTF-8".toMediaTypeOrNull()
                    )
                )
                .build()

            httpClient.newCall(initRequest).execute().use { initResponse ->
                if (!initResponse.isSuccessful) {
                    return classifyHttpError(
                        "Resumable upload preparation failed",
                        initResponse.code,
                        initResponse.body?.string()
                    )
                }

                val uploadUrl = initResponse.header("Location")
                    ?: return CloudSyncResult.Error(
                        message = "Resumable upload preparation failed: missing upload location",
                        isRetryable = false
                    )

                val uploadRequest = Request.Builder()
                    .url(uploadUrl)
                    .put(file.asRequestBody(mimeType.toMediaTypeOrNull()))
                    .build()

                httpClient.newCall(uploadRequest).execute().use { uploadResponse ->
                    if (!uploadResponse.isSuccessful) {
                        return classifyHttpError(
                            "File upload failed",
                            uploadResponse.code,
                            uploadResponse.body?.string()
                        )
                    }

                    val responseBody = uploadResponse.body?.string().orEmpty()
                    val fileId = extractFileIdFromJson(responseBody)
                    if (fileId == null) {
                        return CloudSyncResult.Error(
                            message = "Google Drive upload succeeded but returned no file ID",
                            isRetryable = false
                        )
                    }

                    CloudSyncResult.Success(bytesTransferred = file.length())
                }
            }
        } catch (e: Exception) {
            classifyException(e)
        }
    }

    override suspend fun downloadFile(remotePath: String, destinationFile: File): CloudSyncResult {
        val token = authManager.getAccessToken() ?: return CloudSyncResult.Error(
            message = "Download failed: user is not authenticated with Google Drive.",
            isRetryable = false
        )

        return try {
            val escapedName = remotePath.replace("\\", "\\\\").replace("'", "\\'")
            val query = Uri.encode("name='$escapedName' and trashed=false")
            val searchUrl = "https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id,name)"
            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            val fileId = httpClient.newCall(searchRequest).execute().use { searchResponse ->
                if (!searchResponse.isSuccessful) {
                    return classifyHttpError(
                        "File search failed",
                        searchResponse.code,
                        searchResponse.body?.string()
                    )
                }
                extractFirstFileId(searchResponse.body?.string().orEmpty())
            } ?: return CloudSyncResult.Error(
                message = "File not found on Google Drive: $remotePath",
                isRetryable = false
            )

            val parent = destinationFile.parentFile
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return CloudSyncResult.Error(
                    message = "Unable to create destination directory: ${parent.absolutePath}",
                    isRetryable = false
                )
            }

            val tempFile = File(
                parent ?: destinationFile.parentFile ?: destinationFile.absoluteFile.parentFile,
                ".${destinationFile.name}.part"
            )

            try {
                val downloadRequest = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()

                httpClient.newCall(downloadRequest).execute().use { downloadResponse ->
                    if (!downloadResponse.isSuccessful) {
                        return classifyHttpError(
                            "Media download failed",
                            downloadResponse.code,
                            downloadResponse.body?.string()
                        )
                    }

                    val responseBody = downloadResponse.body
                        ?: return CloudSyncResult.Error(
                            message = "Media download response body was empty.",
                            isRetryable = true
                        )

                    responseBody.byteStream().use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                            output.fd.sync()
                        }
                    }
                }

                if (!tempFile.renameTo(destinationFile)) {
                    return CloudSyncResult.Error(
                        message = "Downloaded file could not be committed atomically.",
                        isRetryable = true
                    )
                }

                CloudSyncResult.Success(bytesTransferred = destinationFile.length())
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        } catch (e: Exception) {
            classifyException(e)
        }
    }

    private fun determineMimeType(file: File): String = when (file.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        "txt" -> "text/plain"
        "json" -> "application/json"
        else -> "application/octet-stream"
    }

    private fun extractFileIdFromJson(json: String): String? = runCatching {
        JSONObject(json).optString("id").takeIf(String::isNotBlank)
    }.getOrNull()

    private fun extractFirstFileId(json: String): String? = runCatching {
        val files = JSONObject(json).optJSONArray("files") ?: return@runCatching null
        if (files.length() == 0) null else files.optJSONObject(0)?.optString("id")
            ?.takeIf(String::isNotBlank)
    }.getOrNull()

    private fun classifyHttpError(
        context: String,
        code: Int,
        errorBody: String?
    ): CloudSyncResult.Error {
        val message = "$context: HTTP $code - ${errorBody ?: "No details provided"}"
        return CloudSyncResult.Error(
            message = message,
            isRetryable = code == 408 || code == 429 || code >= 500
        )
    }

    private fun classifyException(e: Exception): CloudSyncResult.Error {
        val isRetryable = e is java.net.UnknownHostException ||
            e is java.net.ConnectException ||
            e is IOException ||
            e.message?.contains("Unable to resolve host") == true
        return CloudSyncResult.Error(
            message = e.message ?: "Google Drive operation failed",
            isRetryable = isRetryable,
            cause = e
        )
    }
}
