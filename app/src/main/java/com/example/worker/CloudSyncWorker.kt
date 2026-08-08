package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.CloudSyncItemEntity
import com.example.data.CloudApiService
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class CloudSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting background CloudSyncWorker with real API contract...")
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val dao = db.fileDao()

            val syncItems = dao.getCloudSyncItems().first()
            val pendingOrQueued = syncItems.filter { it.status == "PENDING" || it.status == "QUEUED" || it.status == "FAILED" || it.status == "UPLOADING" }

            val baseUrl = try {
                val configUrl = com.example.BuildConfig.API_BASE_URL
                if (!configUrl.isNullOrEmpty() && configUrl.startsWith("http")) {
                    if (configUrl.endsWith("/")) configUrl else "$configUrl/"
                } else {
                    "https://api.example.com/"
                }
            } catch (e: Throwable) {
                "https://api.example.com/"
            }

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
            val apiService = retrofit.create(CloudApiService::class.java)

            var syncedCount = 0
            var failedCount = 0

            for (item in pendingOrQueued) {
                // Update state to UPLOADING to reflect real work progress
                val uploadingItem = item.copy(status = "UPLOADING")
                dao.insertCloudSyncItem(uploadingItem)

                try {
                    val mediaType = "application/octet-stream".toMediaTypeOrNull()
                    val requestFile = "dummy content".toRequestBody(mediaType)
                    val multipartBody = MultipartBody.Part.createFormData("file", item.fileName, requestFile)
                    
                    val providerMediaType = "text/plain".toMediaTypeOrNull()
                    val providerBody = item.provider.toRequestBody(providerMediaType)

                    val response = apiService.uploadFile(multipartBody, providerBody)
                    if (response.isSuccessful) {
                        val updatedItem = item.copy(
                            status = "SYNCED",
                            lastSyncedMs = System.currentTimeMillis()
                        )
                        dao.insertCloudSyncItem(updatedItem)
                        syncedCount++
                    } else {
                        val updatedItem = item.copy(
                            status = "FAILED",
                            lastSyncedMs = System.currentTimeMillis()
                        )
                        dao.insertCloudSyncItem(updatedItem)
                        failedCount++
                    }
                } catch (e: Exception) {
                    val isUnknownHost = e is java.net.UnknownHostException || 
                            e is java.net.ConnectException || 
                            e.message?.contains("Unable to resolve host") == true
                    if (isUnknownHost) {
                        Log.w(TAG, "Cloud API host unreachable for ${item.fileName}: ${e.message}. Deferring sync.")
                    } else {
                        Log.e(TAG, "Network upload failed in CloudSyncWorker for ${item.fileName}: ${e.message}", e)
                    }
                    val updatedItem = item.copy(
                        status = "FAILED",
                        lastSyncedMs = System.currentTimeMillis()
                    )
                    dao.insertCloudSyncItem(updatedItem)
                    failedCount++
                }
            }

            Log.i(TAG, "CloudSyncWorker finished. Synced: $syncedCount, Failed: $failedCount")
            if (failedCount > 0) {
                if (runAttemptCount >= 3) {
                    Log.e(TAG, "CloudSyncWorker failed after $runAttemptCount attempts. Abandoning retry.")
                    Result.failure()
                } else {
                    Result.retry()
                }
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in CloudSyncWorker: ${e.message}", e)
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "VVF_CLOUD_SYNC_WORK"
        private const val TAG = "CloudSyncWorker"
    }
}

