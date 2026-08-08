package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class FileRepositoryTest {

    private lateinit var context: Context
    private lateinit var fakeDao: FakeFileDao
    private lateinit var repository: FileRepository

    class FakeFileDao : FileDao {
        var filesMap = mutableMapOf<Long, FileItemEntity>()
        var updatedFile: FileItemEntity? = null

        override suspend fun getFileById(id: Long): FileItemEntity? {
            return filesMap[id]
        }

        override suspend fun getFilteredFilesPaged(category: String?, query: String, limit: Int, offset: Int): List<FileItemEntity> {
            return filesMap.values.filter { file ->
                (category == null || file.category == category) &&
                (query.isEmpty() || file.name.contains(query, ignoreCase = true) || file.tags.contains(query, ignoreCase = true))
            }.take(limit)
        }

        override suspend fun updateFile(file: FileItemEntity) {
            filesMap[file.id] = file
            updatedFile = file
        }

        // Dummy overrides
        override suspend fun getFileByName(name: String): FileItemEntity? = null
        override fun getOcrScannedFiles(): Flow<List<FileItemEntity>> = flowOf(emptyList())
        override fun searchSemanticFiles(query: String): Flow<List<FileItemEntity>> = flowOf(emptyList())
        override fun getAllActiveFiles(): Flow<List<FileItemEntity>> = flowOf(emptyList())
        override fun getRecentFiles(): Flow<List<FileItemEntity>> = flowOf(emptyList())
        override fun getCategoryStats(): Flow<List<CategoryStat>> = flowOf(emptyList())
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
        override suspend fun deleteFileById(id: Long) {}
        override suspend fun emptyRecycleBin() {}
        override suspend fun getVaultFileByName(name: String): FileItemEntity? = null
        override fun getAllVaultItems(): Flow<List<VaultItemEntity>> = flowOf(emptyList())
        override suspend fun insertVaultItem(item: VaultItemEntity): Long = 0L
        override suspend fun deleteVaultItemById(id: Long) {}
        override fun getCloudSyncItems(): Flow<List<CloudSyncItemEntity>> = flowOf(emptyList())
        override suspend fun insertCloudSyncItem(item: CloudSyncItemEntity): Long = 0L
        override suspend fun setPluginEnabled(id: String, enabled: Boolean) {}
        override suspend fun insertPlugins(plugins: List<PluginEntity>) {}
        override fun getAllPlugins(): Flow<List<PluginEntity>> = flowOf(emptyList())
    }

    @Before
    fun setUp() {
        context = Mockito.mock(Context::class.java)
        fakeDao = FakeFileDao()
        repository = FileRepository(context, fakeDao)
    }

    @Test
    fun testGetFileByIdDelegation() = runBlocking {
        val sampleFile = FileItemEntity(id = 42, name = "sample.txt", path = "/path/to/sample.txt", category = "DOCUMENTS", sizeBytes = 100)
        fakeDao.filesMap[42L] = sampleFile

        val result = repository.getFileById(42)
        assertEquals(sampleFile, result)
    }

    @Test
    fun testGetFilteredFilesPagedDelegation() = runBlocking {
        val file1 = FileItemEntity(id = 1, name = "a.jpg", path = "/a.jpg", category = "IMAGES", sizeBytes = 1024)
        val file2 = FileItemEntity(id = 2, name = "b.jpg", path = "/b.jpg", category = "IMAGES", sizeBytes = 2048)
        fakeDao.filesMap[1L] = file1
        fakeDao.filesMap[2L] = file2

        val result = repository.getFilteredFilesPaged("IMAGES", "jpg", 10, 0)
        assertEquals(2, result.size)
        assertEquals("a.jpg", result[0].name)
    }

    @Test
    fun testAddTagToFile() = runBlocking {
        val initialFile = FileItemEntity(id = 10, name = "doc.pdf", path = "/doc.pdf", category = "DOCUMENTS", sizeBytes = 500, tags = "")
        val updated = repository.addTagToFile(initialFile, "important")
        assertEquals("important", updated.tags)
        assertEquals("important", fakeDao.updatedFile?.tags)
    }
}
