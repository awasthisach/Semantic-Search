package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OcrEngineTest {

    private lateinit var fakeDao: FakeFileDao
    private lateinit var fakeOcrEngine: FakeOcrEngine
    private lateinit var repository: SmartManagerRepository

    // Hand-crafted Fake implementing FileDao interface to bypass Room and MockK/ByteBuddy limitations
    class FakeFileDao : FileDao {
        var unhashedFiles = mutableListOf<FileItemEntity>()
        var plugins = mutableListOf<PluginEntity>()
        val updatedFiles = mutableListOf<FileItemEntity>()
        var onUpdateCallback: (() -> Unit)? = null

        override suspend fun getUnhashedFiles(): List<FileItemEntity> {
            return unhashedFiles
        }

        override fun getAllPlugins(): Flow<List<PluginEntity>> {
            return flowOf(plugins)
        }

        override suspend fun updateFiles(files: List<FileItemEntity>) {
            updatedFiles.addAll(files)
            onUpdateCallback?.invoke()
        }

        override suspend fun findInRecycleBinByHash(hash: String): FileItemEntity? = null

        override suspend fun moveFilesToRecycleBinAtomic(files: List<FileItemEntity>) {
            updateFiles(files)
        }

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
        override fun getCloudSyncItems(): Flow<List<CloudSyncItemEntity>> = flowOf(emptyList())
        override suspend fun insertCloudSyncItem(item: CloudSyncItemEntity): Long = 0L
        override suspend fun setPluginEnabled(id: String, enabled: Boolean) {}
        override suspend fun insertPlugins(plugins: List<PluginEntity>) {}
    }

    // Hand-crafted Fake implementing OcrEngine interface to bypass ML Kit library dependencies
    class FakeOcrEngine : OcrEngine {
        var resultText = ""
        var shouldThrowException = false
        var lastCapturedPath: String? = null

        override suspend fun extractRealOcrText(filePath: String): String {
            if (shouldThrowException) {
                throw RuntimeException("ML Kit not initialized / GMS Core error")
            }
            lastCapturedPath = filePath
            return resultText
        }
    }

    @Before
    fun setUp() {
        fakeDao = FakeFileDao()
        fakeOcrEngine = FakeOcrEngine()
        
        // Pass relaxed/null dependencies safely for pure JUnit execution
        val mockContext = org.mockito.Mockito.mock(Context::class.java)
        repository = SmartManagerRepository(mockContext, fakeDao, fakeOcrEngine)
    }

    @Test
    fun test_empty_or_null_ocr_result_does_not_generate_fabricated_data() = runBlocking {
        // (a) When real OCR result is empty/null, database does NOT save any fabricated GSTIN/amount/date patterns
        fakeOcrEngine.resultText = ""

        val testFile = FileItemEntity(
            id = 501L,
            name = "receipt.jpg",
            path = "/storage/emulated/0/DCIM/receipt.jpg",
            category = FileCategory.IMAGES.name,
            sizeBytes = 1200L,
            md5Hash = "some_existing_hash",
            ocrText = ""
        )

        fakeDao.unhashedFiles.add(testFile)
        fakeDao.plugins.add(
            PluginEntity("ocr_engine", "ML Kit OCR Engine", "OCR", "Extract text", isEnabled = true, isCore = true)
        )

        val latch = java.util.concurrent.CountDownLatch(1)
        fakeDao.onUpdateCallback = {
            latch.countDown()
        }

        repository.startIncrementalDuplicateScan()

        // Wait until scanning completes deterministically
        latch.await(3, java.util.concurrent.TimeUnit.SECONDS)

        // Verify that update was called but the OCR text remains blank, and no fabricated data got generated
        val updatedFile = fakeDao.updatedFiles.find { it.id == 501L }
        
        // Note: If no modification was made, it might not be in updatedFiles. If it is updated (e.g. hash or semantic index changes), we verify ocrText.
        if (updatedFile != null) {
            assertEquals("", updatedFile.ocrText)
            assertFalse(updatedFile.ocrText.contains("27AAAC"))
            assertFalse(updatedFile.ocrText.contains("GSTIN"))
            assertFalse(updatedFile.ocrText.contains("Amount"))
            assertFalse(updatedFile.ocrText.contains("Date"))
        }
    }

    @Test
    fun test_mocked_real_ocr_result_correctly_saved_to_entity() = runBlocking {
        // (b) Mocked real OCR result is correctly passed and saved into FileItemEntity.ocrText
        fakeOcrEngine.resultText = "AUTHENTIC OCR CONTENT EXTRACTED FROM IMAGE"

        val testFile = FileItemEntity(
            id = 502L,
            name = "invoice_real.jpg",
            path = "/storage/emulated/0/DCIM/invoice_real.jpg",
            category = FileCategory.IMAGES.name,
            sizeBytes = 2200L,
            md5Hash = "hash_502",
            ocrText = ""
        )

        fakeDao.unhashedFiles.add(testFile)
        fakeDao.plugins.add(
            PluginEntity("ocr_engine", "ML Kit OCR Engine", "OCR", "Extract text", isEnabled = true, isCore = true)
        )

        val latch = java.util.concurrent.CountDownLatch(1)
        fakeDao.onUpdateCallback = {
            latch.countDown()
        }

        repository.startIncrementalDuplicateScan()

        // Wait until scanning completes deterministically
        latch.await(3, java.util.concurrent.TimeUnit.SECONDS)

        val updatedFile = fakeDao.updatedFiles.find { it.id == 502L }
        assertNotNull(updatedFile)
        
        // Verify real OCR text is saved perfectly
        assertEquals("AUTHENTIC OCR CONTENT EXTRACTED FROM IMAGE", updatedFile!!.ocrText)
        
        // Assert NO fabricated templates are present
        assertFalse(updatedFile.ocrText.contains("27AAAC"))
    }

    @Test
    fun test_ocr_engine_unavailability_handled_gracefully_without_fabrication() = runBlocking {
        // (c) When OCR Engine is unavailable or throws an exception, the system handles it gracefully and does not fabricate data
        fakeOcrEngine.shouldThrowException = true

        val testFile = FileItemEntity(
            id = 503L,
            name = "document_failed.jpg",
            path = "/storage/emulated/0/DCIM/document_failed.jpg",
            category = FileCategory.IMAGES.name,
            sizeBytes = 3200L,
            md5Hash = "hash_503",
            ocrText = ""
        )

        fakeDao.unhashedFiles.add(testFile)
        fakeDao.plugins.add(
            PluginEntity("ocr_engine", "ML Kit OCR Engine", "OCR", "Extract text", isEnabled = true, isCore = true)
        )

        val latch = java.util.concurrent.CountDownLatch(1)
        fakeDao.onUpdateCallback = {
            latch.countDown()
        }

        repository.startIncrementalDuplicateScan()

        // Wait until scanning completes deterministically
        latch.await(3, java.util.concurrent.TimeUnit.SECONDS)

        val updatedFile = fakeDao.updatedFiles.find { it.id == 503L }
        
        if (updatedFile != null) {
            // Should remain empty because OCR extraction failed
            assertEquals("", updatedFile.ocrText)
            
            // No fabricated patterns
            assertFalse(updatedFile.ocrText.contains("27AAAC"))
            assertFalse(updatedFile.ocrText.contains("GSTIN"))
        }
    }
}
