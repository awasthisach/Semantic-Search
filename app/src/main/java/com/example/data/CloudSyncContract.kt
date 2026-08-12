package com.example.data

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException

/**
 * Provider-agnostic outcome of a cloud sync operation (upload/download).
 */
sealed class CloudSyncResult {
    data class Success(val bytesTransferred: Long = 0L) : CloudSyncResult()
    data class Error(
        val message: String,
        val isRetryable: Boolean,
        val cause: Throwable? = null
    ) : CloudSyncResult()
    object NotSupported : CloudSyncResult()
}

/**
 * Abstraction for cloud storage providers (REST, Drive, OneDrive, Dropbox, etc.).
 */
interface CloudProviderAdapter {
    val providerId: String
    suspend fun uploadFile(file: File, remotePath: String): CloudSyncResult
    suspend fun downloadFile(remotePath: String, destinationFile: File): CloudSyncResult
}

/**
 * Default REST implementation using [CloudApiService].
 */
class RestCloudProviderAdapter(
    override val providerId: String,
    private val apiService: CloudApiService
) : CloudProviderAdapter {

    override suspend fun uploadFile(file: File, remotePath: String): CloudSyncResult {
        if (!file.exists() || !file.isFile) {
            return CloudSyncResult.Error(
                message = "File does not exist or is invalid: ${file.absolutePath}",
                isRetryable = false
            )
        }

        return try {
            val mediaType = "application/octet-stream".toMediaTypeOrNull()
            val requestFile = file.asRequestBody(mediaType)
            val multipartBody = MultipartBody.Part.createFormData("file", file.name, requestFile)

            val providerMediaType = "text/plain".toMediaTypeOrNull()
            val providerBody = providerId.toRequestBody(providerMediaType)

            val response = apiService.uploadFile(multipartBody, providerBody)
            if (response.isSuccessful) {
                CloudSyncResult.Success(bytesTransferred = file.length())
            } else {
                val code = response.code()
                val isRetryable = code >= 500
                CloudSyncResult.Error(
                    message = "Server returned HTTP error status $code",
                    isRetryable = isRetryable
                )
            }
        } catch (e: Exception) {
            val isRetryable = e is UnknownHostException ||
                    e is ConnectException ||
                    e is IOException ||
                    e.message?.contains("Unable to resolve host") == true
            CloudSyncResult.Error(
                message = e.message ?: "Upload failed due to network exception",
                isRetryable = isRetryable,
                cause = e
            )
        }
    }

    override suspend fun downloadFile(remotePath: String, destinationFile: File): CloudSyncResult {
        // Current backend REST API does not expose a download/restore endpoint
        return CloudSyncResult.NotSupported
    }
}
