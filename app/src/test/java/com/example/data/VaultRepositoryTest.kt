package com.example.data

import android.content.Context
import com.example.security.KeystoreVaultManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VaultRepositoryTest {

    private lateinit var context: Context
    private lateinit var fakeDao: FakeFileDao
    private lateinit var keystoreVaultManager: KeystoreVaultManager
    private lateinit var repository: VaultRepository

    class FakeFileDao : FileDao {
        var updatedFile: FileItemEntity? = null
        var insertedVaultItem: VaultItemEntity? = null
        var deletedVaultItemId: Long? = null

        override suspend fun updateFile(file: FileItemEntity) {
            updatedFile = file
        }

        override suspend fun insertVaultItem(item: VaultItemEntity): Long {
            insertedVaultItem = item
            return 1L
        }

        override suspend fun deleteVaultItemById(id: Long) {
            deletedVaultItemId = id
        }

        override suspend fun getVaultFileByName(name: String): FileItemEntity? {
            return FileItemEntity(id = 5, name = "secret.png", path = "/secret.png", category = "IMAGES", sizeBytes = 1200, isVault = true)
        }

        // Dummy overrides
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
        override fun getDuplicateFilesByHash(): Flow<List<FileItemEntity>> = flowOf(emptyList())
        override suspend fun insertFile(file: FileItemEntity): Long = 0L
        override suspend fun insertFiles(files: List<FileItemEntity>) {}
        override suspend fun deleteFileById(id: Long) {}
        override suspend fun emptyRecycleBin() {}
        override fun getAllVaultItems(): Flow<List<VaultItemEntity>> = flowOf(emptyList())
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
        keystoreVaultManager = KeystoreVaultManager()
        repository = VaultRepository(context, fakeDao, keystoreVaultManager)
    }

    @Test
    fun testEncryptToVaultFallbackFlow() = runBlocking {
        val file = FileItemEntity(id = 5, name = "secret.png", path = "/secret.png", category = "IMAGES", sizeBytes = 1200)

        repository.encryptToVault(file)

        assertNotNull(fakeDao.updatedFile)
        assertTrue(fakeDao.updatedFile!!.isVault)
        assertNotNull(fakeDao.insertedVaultItem)
        assertEquals("secret.png", fakeDao.insertedVaultItem!!.originalName)
    }

    @Test
    fun testUnlockFromVaultDelegation() = runBlocking {
        val vaultItem = VaultItemEntity(
            id = 12,
            originalName = "secret.png",
            encryptedName = "ENC_123.vvf",
            encryptedFilePath = "/vault/ENC_123.vvf",
            ivBase64 = "YWJjZA==", // "abcd"
            category = "IMAGES",
            sizeBytes = 1200
        )
        val file = FileItemEntity(id = 5, name = "secret.png", path = "/secret.png", category = "IMAGES", sizeBytes = 1200, isVault = true)

        val success = repository.unlockFromVault(vaultItem, file)
        assertTrue(success)

        assertNotNull(fakeDao.updatedFile)
        assertFalse(fakeDao.updatedFile!!.isVault)
        assertEquals(12L, fakeDao.deletedVaultItemId)
    }
}
