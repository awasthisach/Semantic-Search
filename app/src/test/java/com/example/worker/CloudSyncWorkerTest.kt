package com.example.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.data.CategoryStat
import com.example.data.CloudSyncItemEntity
import com.example.data.FileDao
import com.example.data.FileItemEntity
import com.example.data.PluginEntity
import com.example.data.VaultItemEntity
import com.example.data.CloudProviderAdapter
import com.example.data.CloudSyncResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

class FakeFileDao : FileDao {
    val cloudSyncItems = mutableListOf<CloudSyncItemEntity>()
    private var autoSyncId = 1000L
    override fun getCloudSyncItems(): Flow<List<CloudSyncItemEntity>> = flowOf(cloudSyncItems)
    override suspend fun insertCloudSyncItem(item: CloudSyncItemEntity): Long { val assignedId = if (item.id == 0L) autoSyncId++ else item.id; val newItem = item.copy(id = assignedId); val index = cloudSyncItems.indexOfFirst { it.id == assignedId }; if (index != -1) cloudSyncItems[index] = newItem else cloudSyncItems.add(newItem); return assignedId }
    override suspend fun deleteCloudSyncItem(id: Long) { cloudSyncItems.removeAll { it.id == id } }
    override suspend fun getFileById(id: Long): FileItemEntity? = null
    override suspend fun getFileByName(name: String): FileItemEntity? = null
    override fun getOcrScannedFiles(): Flow<List<FileItemEntity>> = flowOf(emptyList())
    override fun searchSemanticFiles(query: String): Flow<List<FileItemEntity>> = flowOf(emptyList())
    override fun getAllActiveFiles(): Flow<List<FileItemEntity>> = flowOf(emptyList())
    override fun getSemanticIndexedActiveFiles(): Flow<List<FileItemEntity>> = flowOf(emptyList())
    override fun getRecentFiles(): Flow<List<FileItemEntity>> = flowOf(emptyList())
    override fun getCategoryStats(): Flow<List<CategoryStat>> = flowOf(emptyList())
    override suspend fun getFilteredFilesPaged(category: String?, query: String, limit: Int, offset: Int): List<FileItemEntity> = emptyList()
    override fun getFilesByCategory(category: String): Flow<List<FileItemEntity>> = flowOf(emptyList())
    override fun getRecycleBinFiles(): Flow<List<FileItemEntity>> = flowOf(emptyList())
    override fun getVaultFiles(): Flow<List<FileItemEntity>> = flowOf(emptyList())
    override fun searchFiles(query: String): Flow<List<FileItemEntity>> = flowOf(emptyList())
    override suspend fun getUnhashedFiles(): List<FileItemEntity> = emptyList()
    override suspend fun updateFiles(files: List<FileItemEntity>) {}
    override suspend fun findInRecycleBinByHash(hash: String): FileItemEntity? = null
    override suspend fun moveFilesToRecycleBinAtomic(files: List<FileItemEntity>) {}
    override fun getDuplicateFilesByHash(): Flow<List<FileItemEntity>> = flowOf(emptyList())
    override suspend fun insertFile(file: FileItemEntity): Long = 0L
    override suspend fun insertFiles(files: List<FileItemEntity>) {}
    override suspend fun updateFile(file: FileItemEntity) {}
    override suspend fun getFileByPath(path: String): FileItemEntity? = null
    override suspend fun insertFileDirect(file: FileItemEntity): Long = 0L
    override suspend fun getAllOrdinaryFilesDirect(): List<FileItemEntity> = emptyList()
    override suspend fun deleteFilesByIds(ids: List<Long>) {}
    override suspend fun deleteFileById(id: Long) {}
    override suspend fun emptyRecycleBin() {}
    override suspend fun getVaultFileByName(name: String): FileItemEntity? = null
    override fun getAllVaultItems(): Flow<List<VaultItemEntity>> = flowOf(emptyList())
    override suspend fun insertVaultItem(item: VaultItemEntity): Long = 0L
    override suspend fun deleteVaultItemById(id: Long) {}
    val pluginsList = mutableListOf<PluginEntity>()
    override fun getAllPlugins(): Flow<List<PluginEntity>> = flowOf(pluginsList)
    override suspend fun setPluginEnabled(id: String, enabled: Boolean) { val idx = pluginsList.indexOfFirst { it.pluginId == id }; if (idx != -1) pluginsList[idx] = pluginsList[idx].copy(isEnabled = enabled) }
    override suspend fun insertPlugins(plugins: List<PluginEntity>) { plugins.forEach { plugin -> val idx = pluginsList.indexOfFirst { it.pluginId == plugin.pluginId }; if (idx != -1) pluginsList[idx] = plugin else pluginsList.add(plugin) } }
}

class FakeCloudProviderAdapter : CloudProviderAdapter {
    override val providerId: String = "GOOGLE_DRIVE"
    var shouldFail = false
    var isRetryable = true
    var exceptionToThrow: Exception? = null
    override suspend fun uploadFile(file: File, remotePath: String): CloudSyncResult { exceptionToThrow?.let { throw it }; if (shouldFail) return CloudSyncResult.Error("Upload failed", isRetryable); return CloudSyncResult.Success(file.length()) }
    override suspend fun downloadFile(remotePath: String, destinationFile: File): CloudSyncResult = CloudSyncResult.NotSupported
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudSyncWorkerTest {
    private lateinit var context: Context
    private lateinit var fakeDao: FakeFileDao
    private lateinit var fakeAdapter: FakeCloudProviderAdapter
    @Before fun setUp() { context = RuntimeEnvironment.getApplication(); fakeDao = FakeFileDao(); fakeAdapter = FakeCloudProviderAdapter() }
    @After fun tearDown() {}
    private fun createWorker(runAttemptCount: Int = 0): CloudSyncWorker {
        val workerFactory = object : WorkerFactory() { override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker = CloudSyncWorker(appContext, workerParameters, daoOverride = fakeDao, providerAdapterOverride = fakeAdapter) }
        return TestListenableWorkerBuilder<CloudSyncWorker>(context).setWorkerFactory(workerFactory).setRunAttemptCount(runAttemptCount).build()
    }
    private fun syncItem(id: Long, file: File, provider: String = "GOOGLE_DRIVE") = CloudSyncItemEntity(id = id, provider = provider, fileName = file.name, filePath = file.absolutePath, fileSize = file.length(), status = "PENDING", lastSyncedMs = 0L, isCore = false)

    @Test fun testSuccessfulUpload_updatesStatusToSyncedAndReturnsSuccess() = runBlocking { val file = File.createTempFile("sync_test_success", ".txt"); file.writeText("sample data for upload"); file.deleteOnExit(); fakeDao.insertCloudSyncItem(syncItem(101L, file)); val result = createWorker().doWork(); assertEquals(ListenableWorker.Result.success(), result); assertEquals("SYNCED", fakeDao.getCloudSyncItems().first().find { it.id == 101L }?.status) }
    @Test fun testNetworkFailure_updatesStatusToFailedAndReturnsRetry() = runBlocking { val file = File.createTempFile("sync_test_failure", ".txt"); file.writeText("sample data for upload"); file.deleteOnExit(); fakeDao.insertCloudSyncItem(syncItem(102L, file)); fakeAdapter.exceptionToThrow = IOException("Network connection dropped"); val result = createWorker().doWork(); assertEquals(ListenableWorker.Result.retry(), result); assertEquals("FAILED", fakeDao.getCloudSyncItems().first().find { it.id == 102L }?.status) }
    @Test fun testNetworkFailureWithMaxRunAttempts_returnsFailure() = runBlocking { val file = File.createTempFile("sync_test_max_retry", ".txt"); file.writeText("sample data for upload"); file.deleteOnExit(); fakeDao.insertCloudSyncItem(syncItem(103L, file)); fakeAdapter.exceptionToThrow = IOException("Network connection dropped"); assertEquals(ListenableWorker.Result.failure(), createWorker(3).doWork()) }
    @Test fun testDisabledPluginProvider_skipsSyncAndReturnsSuccess() = runBlocking { val file = File.createTempFile("sync_test_disabled_plugin", ".txt"); file.writeText("sample data for upload"); file.deleteOnExit(); fakeDao.insertPlugins(listOf(PluginEntity("dropbox_sync", "Dropbox Cloud Plugin", "CLOUD_PROVIDER", "Dropbox Cloud integration", false, false))); fakeDao.insertCloudSyncItem(syncItem(104L, file, "DROPBOX")); assertEquals(ListenableWorker.Result.success(), createWorker().doWork()); assertEquals("PENDING", fakeDao.getCloudSyncItems().first().find { it.id == 104L }?.status) }
    @Test fun testHttp4xxFailure_returnsFailureWithoutRetry() = runBlocking { val file = File.createTempFile("sync_test_404", ".txt"); file.writeText("sample data for upload"); file.deleteOnExit(); fakeDao.insertCloudSyncItem(syncItem(105L, file)); fakeAdapter.shouldFail = true; fakeAdapter.isRetryable = false; assertEquals(ListenableWorker.Result.failure(), createWorker().doWork()); assertEquals("FAILED", fakeDao.getCloudSyncItems().first().find { it.id == 105L }?.status) }
    @Test fun testMissingLocalFile_returnsFailureWithoutRetry() = runBlocking { val file = File(context.cacheDir, "non_existent_file_${System.currentTimeMillis()}.txt"); fakeDao.insertCloudSyncItem(syncItem(106L, file)); assertEquals(ListenableWorker.Result.failure(), createWorker().doWork()); assertEquals("FAILED", fakeDao.getCloudSyncItems().first().find { it.id == 106L }?.status) }
}
