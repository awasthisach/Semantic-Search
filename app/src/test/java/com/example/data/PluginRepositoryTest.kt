package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PluginRepositoryTest {
    private lateinit var fakeDao: FakeFileDao
    private lateinit var repository: PluginRepository

    class FakeFileDao : FileDao {
        var pluginsList = mutableListOf<PluginEntity>()
        var setPluginEnabledCallCount = 0
        var lastToggledPluginId: String? = null
        var lastToggledEnabled: Boolean? = null
        override fun getAllPlugins(): Flow<List<PluginEntity>> = flowOf(pluginsList)
        override suspend fun setPluginEnabled(id: String, enabled: Boolean) { setPluginEnabledCallCount++; lastToggledPluginId = id; lastToggledEnabled = enabled; val idx = pluginsList.indexOfFirst { it.pluginId == id }; if (idx != -1) pluginsList[idx] = pluginsList[idx].copy(isEnabled = enabled) }
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
        override fun getCloudSyncItems(): Flow<List<CloudSyncItemEntity>> = flowOf(emptyList())
        override suspend fun insertCloudSyncItem(item: CloudSyncItemEntity): Long = 0L
        override suspend fun deleteCloudSyncItem(id: Long) {}
        override suspend fun insertPlugins(plugins: List<PluginEntity>) {}
    }

    @Before fun setUp() { fakeDao = FakeFileDao(); repository = PluginRepository(fakeDao) }

    @Test fun testPluginsFlow_passesDataFromDao() = runBlocking {
        fakeDao.pluginsList.addAll(listOf(PluginEntity("ocr", "OCR Plugin", "OCR", "Extracts text from images", true, true), PluginEntity("cloud", "S3 Sync", "CLOUD_PROVIDER", "Syncs files with S3", false, false)))
        val result = repository.getAllPlugins().first()
        assertEquals(2, result.size); assertEquals("ocr", result[0].pluginId); assertTrue(result[0].isEnabled); assertEquals("cloud", result[1].pluginId); assertFalse(result[1].isEnabled)
    }
    @Test fun testTogglePlugin_fromEnabledToDisabled() = runBlocking {
        fakeDao.pluginsList.add(PluginEntity("ocr", "OCR Plugin", "OCR", "Extracts text", true, true)); repository.togglePlugin("ocr", true)
        assertEquals("ocr", fakeDao.lastToggledPluginId); assertEquals(false, fakeDao.lastToggledEnabled); assertEquals(1, fakeDao.setPluginEnabledCallCount); assertFalse(fakeDao.pluginsList[0].isEnabled)
    }
    @Test fun testTogglePlugin_fromDisabledToEnabled() = runBlocking {
        fakeDao.pluginsList.add(PluginEntity("cloud", "S3 Sync", "CLOUD_PROVIDER", "Syncs files", false, false)); repository.togglePlugin("cloud", false)
        assertEquals("cloud", fakeDao.lastToggledPluginId); assertEquals(true, fakeDao.lastToggledEnabled); assertEquals(1, fakeDao.setPluginEnabledCallCount); assertTrue(fakeDao.pluginsList[0].isEnabled)
    }
}
