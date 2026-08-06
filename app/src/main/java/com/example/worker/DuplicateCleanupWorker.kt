package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.FileItemEntity
import com.example.storage.StorageScanner
import kotlinx.coroutines.flow.first

class DuplicateCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting background DuplicateCleanupWorker...")
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val dao = db.fileDao()

            val activeFiles = dao.getAllActiveFiles().first()
            var duplicatesFound = 0
            var bytesCleaned = 0L

            // Group by MD5 hash for exact duplicates
            val exactDuplicateGroups = activeFiles
                .filter { it.md5Hash.isNotBlank() }
                .groupBy { it.md5Hash }
                .filter { it.value.size > 1 }

            val filesToMoveToRecycleBin = mutableListOf<FileItemEntity>()

            for ((_, duplicateList) in exactDuplicateGroups) {
                // Keep the oldest/first file, mark redundant ones for cleanup/recycle bin
                val sorted = duplicateList.sortedBy { it.dateModifiedMs }
                val redundant = sorted.drop(1)
                for (file in redundant) {
                    duplicatesFound++
                    bytesCleaned += file.sizeBytes
                    filesToMoveToRecycleBin.add(
                        file.copy(
                            isRecycleBin = true,
                            deletedTimestampMs = System.currentTimeMillis()
                        )
                    )
                }
            }

            if (filesToMoveToRecycleBin.isNotEmpty()) {
                dao.updateFiles(filesToMoveToRecycleBin)
                Log.i(
                    TAG,
                    "DuplicateCleanupWorker moved $duplicatesFound duplicate files (${bytesCleaned / 1024} KB) to Recycle Bin."
                )
            } else {
                Log.i(TAG, "DuplicateCleanupWorker completed. No exact duplicate clutter found.")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in DuplicateCleanupWorker: ${e.message}", e)
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "VVF_DUPLICATE_CLEANUP_WORK"
        private const val TAG = "DuplicateCleanupWorker"
    }
}
