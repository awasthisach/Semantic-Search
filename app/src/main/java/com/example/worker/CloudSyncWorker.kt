package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.CloudSyncItemEntity
import com.example.data.CloudApiService
import com.example.data.FileDao
import com.example.data.CloudProviderAdapter
import com.example.data.CloudSyncResult
import com.example.data.RestCloudProviderAdapter
import kotlinx.coroutines.flow.first
import java.io.File
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class CloudSyncWorker @JvmOverloads constructor(
    appContext: Context,
    workerParams: WorkerParameters,
    private val daoOverride: FileDao? = null,
    private val apiServiceOverride: CloudApiService? = null,
    private val providerAdapterOverride: CloudProviderAdapter? = null
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting background CloudSyncWorker with provider adapter contract...")
        return try {
            val dao = daoOverride ?: AppDatabase.getDatabase(applicationContext).fileDao()

            val syncItems = dao.getCloudSyncItems().first()
            val plugins = dao.getAllPlugins().first()
            val disabledProviders = plugins
                .filter { !it.isEnabled }
                .mapNotNull { plugin ->
                    when (plugin.pluginId) {
                        "gdrive_sync" -> "GOOGLE_DRIVE"
                        "onedrive_sync" -> "ONEDRIVE"
                        "dropbox_sync" -> "DROPBOX"
                        else -> null
                    }
                }.toSet()

            val pendingOrQueued = syncItems
                .filter { it.status == "PENDING" || it.status == "QUEUED" || it.status == "FAILED" || it.status == "UPLOADING" }
                .filter { it.provider !in disabledProviders }

            if (pendingOrQueued.isEmpty()) {
                Log.i(TAG, "No pending cloud sync items for enabled plugins.")
                return Result.success()
            }

            val apiService = if (providerAdapterOverride == null && apiServiceOverride == null) {
                val configUrl = try {
                    com.example.BuildConfig.API_BASE_URL
                } catch (e: Throwable) {
                    null
                }

                if (isPlaceholderUrl(configUrl)) {
                    Log.w(TAG, "Cloud API base URL is missing or set to a placeholder ($configUrl). Aborting cloud sync.")
                    for (item in pendingOrQueued) {
                        dao.insertCloudSyncItem(item.copy(status = "FAILED", lastSyncedMs = System.currentTimeMillis()))
                    }
                    return Result.failure()
                }

                val formattedUrl = if (configUrl!!.endsWith("/")) configUrl else "$configUrl/"
                val retrofit = Retrofit.Builder()
                    .baseUrl(formattedUrl)
                    .addConverterFactory(MoshiConverterFactory.create())
                    .build()
                retrofit.create(CloudApiService::class.java)
            } else {
                apiServiceOverride
            }

            var syncedCount = 0
            var failedCount = 0
            var retryableFailedCount = 0

            for (item in pendingOrQueued) {
                // Update state to UPLOADING to reflect progress
                val uploadingItem = item.copy(status = "UPLOADING")
                dao.insertCloudSyncItem(uploadingItem)

                val file = File(item.filePath)
                val adapter = providerAdapterOverride
                    ?: RestCloudProviderAdapter(item.provider, apiService!!)

                val syncResult = adapter.uploadFile(file, item.fileName)
                when (syncResult) {
                    is CloudSyncResult.Success -> {
                        val updatedItem = item.copy(
                            status = "SYNCED",
                            lastSyncedMs = System.currentTimeMillis()
                        )
                        dao.insertCloudSyncItem(updatedItem)
                        syncedCount++
                    }
                    is CloudSyncResult.Error -> {
                        val updatedItem = item.copy(
                            status = "FAILED",
                            lastSyncedMs = System.currentTimeMillis()
                        )
                        dao.insertCloudSyncItem(updatedItem)
                        failedCount++
                        if (syncResult.isRetryable) {
                            retryableFailedCount++
                        }
                    }
                    is CloudSyncResult.NotSupported -> {
                        val updatedItem = item.copy(
                            status = "NOT_SUPPORTED",
                            lastSyncedMs = System.currentTimeMillis()
                        )
                        dao.insertCloudSyncItem(updatedItem)
                        failedCount++
                    }
                }
            }

            Log.i(TAG, "CloudSyncWorker finished. Synced: $syncedCount, Failed: $failedCount (Retryable: $retryableFailedCount)")
            if (retryableFailedCount > 0) {
                if (runAttemptCount >= 3) {
                    Log.e(TAG, "CloudSyncWorker failed after $runAttemptCount attempts. Abandoning retry.")
                    Result.failure()
                } else {
                    Result.retry()
                }
            } else if (failedCount > 0) {
                // Permanent failure (e.g. HTTP 4xx or missing file) - do not retry
                Result.failure()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in CloudSyncWorker: ${e.message}", e)
            Result.failure()
        }
    }

    private fun isPlaceholderUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        val lower = url.lowercase()
        return lower.contains("example.com") ||
                lower.contains("localhost") ||
                lower.contains("127.0.0.1") ||
                !lower.startsWith("http")
    }

    companion object {
        const val WORK_NAME = "VVF_CLOUD_SYNC_WORK"
        private const val TAG = "CloudSyncWorker"
    }
}

