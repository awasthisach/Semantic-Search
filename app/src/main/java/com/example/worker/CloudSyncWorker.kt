package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.CloudSyncItemEntity
import kotlinx.coroutines.flow.first

class CloudSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting background CloudSyncWorker...")
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val dao = db.fileDao()

            val syncItems = dao.getCloudSyncItems().first()
            val pendingOrQueued = syncItems.filter { it.status == "PENDING" || it.status == "QUEUED" || it.status == "FAILED" }

            var syncedCount = 0
            for (item in pendingOrQueued) {
                // Simulate cloud upload/sync operation for queued items
                val updatedItem = item.copy(
                    status = "SYNCED",
                    lastSyncedMs = System.currentTimeMillis()
                )
                dao.insertCloudSyncItem(updatedItem)
                syncedCount++
            }

            Log.i(TAG, "CloudSyncWorker synchronized $syncedCount queued cloud items.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in CloudSyncWorker: ${e.message}", e)
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "VVF_CLOUD_SYNC_WORK"
        private const val TAG = "CloudSyncWorker"
    }
}
