package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.storage.StorageScanner

class BackgroundIndexWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "BackgroundIndexWorker"
        const val WORK_NAME = "VVF_BACKGROUND_STORAGE_INDEX"
    }

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        Log.i(TAG, "Starting background file storage indexing...")
        return try {
            val scanner = StorageScanner(applicationContext)
            val files = scanner.scanDeviceStorage()

            if (files.isNotEmpty()) {
                val db = AppDatabase.getDatabase(applicationContext)
                db.fileDao().insertFiles(files)
                Log.i(TAG, "Successfully indexed and synced ${files.size} real storage files into database.")
            } else {
                Log.w(TAG, "Background scan finished with 0 files discovered.")
            }

            androidx.work.ListenableWorker.Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Background storage indexing failed: ${e.message}", e)
            androidx.work.ListenableWorker.Result.retry()
        }
    }
}
