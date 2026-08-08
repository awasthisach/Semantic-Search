package com.example.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.data.CategoryStat
import com.example.data.CloudApiService
import com.example.data.CloudSyncItemEntity
import com.example.data.FileDao
import com.example.data.FileItemEntity
import com.example.data.PluginEntity
import com.example.data.VaultItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.File
import java.io.IOException

class FakeFileDao : FileDao {
    val cloudSyncItems = mutableListOf<CloudSyncItemEntity>()

    override fun getCloudSyncItems(): Flow<List<CloudSyncItemEntity>> {
        return flowOf(cloudSyncItems)
    }

    override suspend fun insertCloudSyncItem(item: CloudSyncItemEntity): Long {
        val index = cloudSyncItems.indexOfFirst { it.id == item.id }
        if (index != -1) {
            cloudSyncItems[index] = item
        } else {
            cloudSyncItems.add(item)
        }
        return item.id
    }

    // Dummy overrides for FileDao interface
    override suspend fun getFileById(id: Long): FileItemEntity? = null
    override suspend fun getFileByName(name: String): FileItemEntity? = null
    override fun getOcrScannedFiles(): Flow<List<FileItemEntity>> = flowOf(emptyList())
    override fun searchSemanticFiles(query: String): Flow<List<FileItemEntity>> = flowOf(emptyList())
    override fun getAllActiveFiles(): Flow<List<FileItemEntity>> = flowOf(emptyList())
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
    override suspend fun deleteFileById(id: Long) {}
    override suspend fun emptyRecycleBin() {}
    override suspend fun getVaultFileByName(name: String): FileItemEntity? = null
    override fun getAllVaultItems(): Flow<List<VaultItemEntity>> = flowOf(emptyList())
    override suspend fun insertVaultItem(item: VaultItemEntity): Long = 0L
    override suspend fun deleteVaultItemById(id: Long) {}
    override fun getAllPlugins(): Flow<List<PluginEntity>> = flowOf(emptyList())
    override suspend fun setPluginEnabled(id: String, enabled: Boolean) {}
    override suspend fun insertPlugins(plugins: List<PluginEntity>) {}
}

class FakeCloudApiService : CloudApiService {
    var shouldFail: Boolean = false
    var exceptionToThrow: Exception? = null

    override suspend fun uploadFile(
        file: MultipartBody.Part,
        provider: RequestBody
    ): Response<ResponseBody> {
        exceptionToThrow?.let { throw it }
        if (shouldFail) {
            return Response.error(
                500,
                "{\"error\":\"Server error\"}".toResponseBody("application/json".toMediaTypeOrNull())
            )
        }
        return Response.success(
            "{\"status\":\"ok\"}".toResponseBody("application/json".toMediaTypeOrNull())
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudSyncWorkerTest {

    private lateinit var context: Context
    private lateinit var fakeDao: FakeFileDao
    private lateinit var fakeApiService: FakeCloudApiService

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        fakeDao = FakeFileDao()
        fakeApiService = FakeCloudApiService()
    }

    @After
    fun tearDown() {
    }

    private fun createWorker(runAttemptCount: Int = 0): CloudSyncWorker {
        val workerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker {
                return CloudSyncWorker(appContext, workerParameters, fakeDao, fakeApiService)
            }
        }

        return TestListenableWorkerBuilder<CloudSyncWorker>(context)
            .setWorkerFactory(workerFactory)
            .setRunAttemptCount(runAttemptCount)
            .build()
    }

    @Test
    fun testSuccessfulUpload_updatesStatusToSyncedAndReturnsSuccess() = runBlocking {
        val tempFile = File.createTempFile("sync_test_success", ".txt")
        tempFile.writeText("sample data for upload")
        tempFile.deleteOnExit()

        val syncItem = CloudSyncItemEntity(
            id = 101L,
            provider = "GOOGLE_DRIVE",
            fileName = tempFile.name,
            filePath = tempFile.absolutePath,
            fileSize = tempFile.length(),
            status = "PENDING",
            lastSyncedMs = 0L,
            isCore = false
        )
        fakeDao.insertCloudSyncItem(syncItem)

        fakeApiService.shouldFail = false
        fakeApiService.exceptionToThrow = null

        val worker = createWorker(runAttemptCount = 0)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        val itemsInDb = fakeDao.getCloudSyncItems().first()
        val updatedItem = itemsInDb.find { it.id == 101L }
        assertEquals("SYNCED", updatedItem?.status)
    }

    @Test
    fun testNetworkFailure_updatesStatusToFailedAndReturnsRetry() = runBlocking {
        val tempFile = File.createTempFile("sync_test_failure", ".txt")
        tempFile.writeText("sample data for upload")
        tempFile.deleteOnExit()

        val syncItem = CloudSyncItemEntity(
            id = 102L,
            provider = "GOOGLE_DRIVE",
            fileName = tempFile.name,
            filePath = tempFile.absolutePath,
            fileSize = tempFile.length(),
            status = "PENDING",
            lastSyncedMs = 0L,
            isCore = false
        )
        fakeDao.insertCloudSyncItem(syncItem)

        fakeApiService.exceptionToThrow = IOException("Network connection dropped")

        val worker = createWorker(runAttemptCount = 0)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)

        val itemsInDb = fakeDao.getCloudSyncItems().first()
        val updatedItem = itemsInDb.find { it.id == 102L }
        assertEquals("FAILED", updatedItem?.status)
    }

    @Test
    fun testNetworkFailureWithMaxRunAttempts_returnsFailure() = runBlocking {
        val tempFile = File.createTempFile("sync_test_max_retry", ".txt")
        tempFile.writeText("sample data for upload")
        tempFile.deleteOnExit()

        val syncItem = CloudSyncItemEntity(
            id = 103L,
            provider = "GOOGLE_DRIVE",
            fileName = tempFile.name,
            filePath = tempFile.absolutePath,
            fileSize = tempFile.length(),
            status = "PENDING",
            lastSyncedMs = 0L,
            isCore = false
        )
        fakeDao.insertCloudSyncItem(syncItem)

        fakeApiService.exceptionToThrow = IOException("Network connection dropped")

        val worker = createWorker(runAttemptCount = 3)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }
}
