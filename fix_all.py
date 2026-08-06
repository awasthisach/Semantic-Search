import os
import re

# 1. Update MainActivity to FragmentActivity
main_activity = "app/src/main/java/com/example/MainActivity.kt"
with open(main_activity, "r") as f:
    content = f.read()

content = content.replace("androidx.activity.ComponentActivity", "androidx.fragment.app.FragmentActivity")
content = content.replace("class MainActivity : ComponentActivity()", "class MainActivity : FragmentActivity()")
with open(main_activity, "w") as f:
    f.write(content)

# 2. Add unlockVaultWithBiometrics to MainViewModel
view_model = "app/src/main/java/com/example/ui/MainViewModel.kt"
with open(view_model, "r") as f:
    content = f.read()

biometric_unlock = """
    fun unlockVaultWithBiometrics() {
        _isVaultUnlocked.value = true
    }
"""
if "unlockVaultWithBiometrics" not in content:
    content = content.replace("fun unlockVault() {", biometric_unlock + "\n    fun unlockVault() {")
    with open(view_model, "w") as f:
        f.write(content)

# 3. Create CacheCleanupWorker
os.makedirs("app/src/main/java/com/example/worker", exist_ok=True)
worker_code = """package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File

class CacheCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting CacheCleanupWorker...")
        return try {
            val cacheDir = applicationContext.cacheDir
            val externalCacheDir = applicationContext.externalCacheDir
            
            var deletedFilesCount = 0
            var deletedSize = 0L

            val dirsToClean = listOfNotNull(cacheDir, externalCacheDir)
            for (dir in dirsToClean) {
                if (dir.exists()) {
                    dir.walkBottomUp().forEach { file ->
                        if (file.isFile) {
                            val size = file.length()
                            if (file.delete()) {
                                deletedFilesCount++
                                deletedSize += size
                            }
                        }
                    }
                }
            }

            Log.i(TAG, "Cache cleanup complete. Deleted $deletedFilesCount files, freeing ${deletedSize / 1024} KB.")
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in CacheCleanupWorker: ${e.message}", e)
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "CacheCleanupWorker"
        private const val TAG = "CacheCleanupWorker"
    }
}
"""
with open("app/src/main/java/com/example/worker/CacheCleanupWorker.kt", "w") as f:
    f.write(worker_code)

# 4. Enqueue worker in MainActivity (since VVFApplication is missing or failed)
if "WorkManager" not in open(main_activity).read():
    with open(main_activity, "r") as f:
        content = f.read()
    
    imports = """
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import com.example.worker.CacheCleanupWorker
import java.util.concurrent.TimeUnit
"""
    content = content.replace("import android.os.Bundle", "import android.os.Bundle" + imports)
    
    enqueue_code = """
        val cacheCleanupRequest = PeriodicWorkRequestBuilder<CacheCleanupWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(CacheCleanupWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, cacheCleanupRequest)
"""
    content = content.replace("enableEdgeToEdge()", "enableEdgeToEdge()" + enqueue_code)
    
    with open(main_activity, "w") as f:
        f.write(content)

