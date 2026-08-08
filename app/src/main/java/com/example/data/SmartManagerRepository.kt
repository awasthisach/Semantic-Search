// SmartManagerRepository - Phase 6 Step 7 Complete
package com.example.data

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.ai.SemanticEmbeddingProvider
import com.example.ai.FallbackSemanticEmbeddingProvider
import com.example.ai.TFLiteSemanticEmbeddingProvider
import com.example.security.KeystoreVaultManager
import com.example.storage.PhysicalStorageManager
import com.example.storage.StorageScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

open class SmartManagerRepository(
    private val context: Context,
    private val dao: FileDao = AppDatabase.getDatabase(context).fileDao(),
    private val ocrEngine: OcrEngine? = null
) {
    val keystoreVaultManager = KeystoreVaultManager()
    val storageScanner = StorageScanner(context)

    val fileRepository by lazy { FileRepository(context, dao) }
    val vaultRepository by lazy { VaultRepository(context, dao, keystoreVaultManager, vaultManagerEngine) }
    val pluginRepository by lazy { PluginRepository(dao) }

    // Extracted Single-Responsibility Engine Components
    private val activeOcrEngine: OcrEngine by lazy { ocrEngine ?: MLKitOcrEngine(context) }
    private val vaultManagerEngine = VaultManagerEngine(context, keystoreVaultManager)

    private fun isAssetExists(context: Context, fileName: String): Boolean {
        return try {
            context.assets.open(fileName).use { }
            true
        } catch (e: Exception) {
            false
        }
    }

    val tfliteProvider: SemanticEmbeddingProvider by lazy {
        if (isAssetExists(context, "mobile_clip_embedding.tflite") && isAssetExists(context, "mobile_clip_vocab.txt")) {
            try {
                TFLiteSemanticEmbeddingProvider().apply { loadModelFromAssets(context) }
            } catch (e: Throwable) {
                FallbackSemanticEmbeddingProvider()
            }
        } else {
            FallbackSemanticEmbeddingProvider()
        }
    }

    val isSemanticSearchAvailable: Boolean
        get() = tfliteProvider is TFLiteSemanticEmbeddingProvider && tfliteProvider.isModelLoaded()

    private val duplicateDetectionEngine by lazy {
        DuplicateDetectionEngine(storageScanner, tfliteProvider)
    }

    private val repositoryExceptionHandler = CoroutineScope(Dispatchers.IO + Job() + kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
        Log.e("SmartManagerRepository", "Unhandled exception in background repositoryScope", throwable)
    })
    private val repositoryScope = repositoryExceptionHandler
    private var activeScanJob: Job? = null

    private val _scanProgress = MutableStateFlow(1.0f)
    val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val activeFiles: Flow<List<FileItemEntity>> = fileRepository.getAllActiveFiles()
    val recentFiles: Flow<List<FileItemEntity>> = dao.getRecentFiles()
    val categoryStats: Flow<List<CategoryStat>> = dao.getCategoryStats()
    val ocrScannedFiles: Flow<List<FileItemEntity>> = dao.getOcrScannedFiles()

    suspend fun getFileById(id: Long) = fileRepository.getFileById(id)
    suspend fun getFileByName(name: String) = dao.getFileByName(name)

    fun searchSemanticFiles(query: String): Flow<List<FileItemEntity>> {
        if (!isSemanticSearchAvailable) return kotlinx.coroutines.flow.flowOf(emptyList())
        if (query.isBlank()) return dao.getAllActiveFiles()
        return dao.getAllActiveFiles().map { files ->
            val queryVec = tfliteProvider.generateTextEmbedding(query)
            if (queryVec == null) {
                files.filter { file ->
                    file.name.contains(query, ignoreCase = true) ||
                    file.ocrText.contains(query, ignoreCase = true) ||
                    file.tags.contains(query, ignoreCase = true)
                }
            } else {
                files.mapNotNull { file ->
                    val fileVec = tfliteProvider.stringToFloatArray(file.semanticEmbeddingString)
                        ?: tfliteProvider.generateTextEmbedding("${file.name} ${file.ocrText} ${file.tags}")
                    if (fileVec != null) {
                        val sim = tfliteProvider.calculateCosineSimilarity(queryVec, fileVec)
                        val isTextMatch = file.name.contains(query, ignoreCase = true) ||
                                         file.ocrText.contains(query, ignoreCase = true) ||
                                         file.tags.contains(query, ignoreCase = true)
                        if (sim > 0.10f || isTextMatch) {
                            file to sim
                        } else null
                    } else if (file.name.contains(query, ignoreCase = true) ||
                               file.ocrText.contains(query, ignoreCase = true) ||
                               file.tags.contains(query, ignoreCase = true)) {
                        file to 0.5f
                    } else null
                }.sortedByDescending { it.second }.map { it.first }
            }
        }
    }

    val recycleBinFiles: Flow<List<FileItemEntity>> = dao.getRecycleBinFiles()
    val vaultItems: Flow<List<VaultItemEntity>> = dao.getAllVaultItems()
    val cloudSyncItems: Flow<List<CloudSyncItemEntity>> = dao.getCloudSyncItems()
    val plugins: Flow<List<PluginEntity>> = pluginRepository.getAllPlugins()

    // Real SHA-256 Exact Hash Duplicates computed via Room SQL subquery
    val exactDuplicates: Flow<List<DuplicateGroup>> = dao.getDuplicateFilesByHash().map { duplicateFiles ->
        duplicateFiles.groupBy { it.md5Hash }
            .filter { it.value.size > 1 && it.key.isNotBlank() }
            .map { (hash, duplicateList) ->
                DuplicateGroup(
                    title = "Exact SHA-256 Hash Match: ${duplicateList.first().name}",
                    level = 1,
                    similarityScore = 100,
                    files = duplicateList
                )
            }
    }.flowOn(Dispatchers.Default)

    /**
     * Optimized Background Batch Processing Engine for 100k+ files duplicate detection.
     * Never blocks UI thread. Incremental: skips previously hashed files.
     * Supports job cancellation and progress percentage tracking.
     */
    fun startIncrementalDuplicateScan() {
        activeScanJob?.cancel() // Cancellation support: new scan cancels previous scan
        activeScanJob = repositoryScope.launch {
            _isScanning.value = true
            _scanProgress.value = 0.0f
            try {
                val unhashed = dao.getUnhashedFiles()
                if (unhashed.isEmpty()) {
                    _scanProgress.value = 1.0f
                    _isScanning.value = false
                    return@launch
                }

                val isOcrEnabled = dao.getAllPlugins().first().find { it.pluginId == "ocr_engine" }?.isEnabled ?: true
                val totalCount = unhashed.size
                var processedCount = 0
                val batchSize = 50

                unhashed.chunked(batchSize).forEach { chunk ->
                    ensureActive()
                    val updatedChunk = mutableListOf<FileItemEntity>()
                    chunk.forEach { file ->
                        ensureActive()
                        var updated = file
                        // On-device ML Kit OCR text extraction when OCR Engine Plugin is enabled
                        if (isOcrEnabled && updated.ocrText.isBlank() && 
                            (updated.category == FileCategory.IMAGES.name || updated.category == FileCategory.DOCUMENTS.name)) {
                            val realOcr = activeOcrEngine.extractRealOcrText(updated.path)
                            if (realOcr.isNotBlank()) {
                                updated = updated.copy(ocrText = realOcr)
                            }
                        }
                        // Incremental SHA-256 calculation
                        if (updated.md5Hash.isBlank()) {
                            val javaFile = File(updated.path)
                            if (javaFile.exists() && javaFile.canRead()) {
                                ensureActive()
                                val hash = withContext(Dispatchers.IO) { storageScanner.computeFileHash(javaFile) }
                                if (hash.isNotBlank()) {
                                    updated = updated.copy(md5Hash = hash)
                                }
                            }
                        }
                        // Incremental Perceptual dHash calculation for images
                        if (updated.category == FileCategory.IMAGES.name && updated.visualSimilarityHash.isBlank()) {
                            val javaFile = File(updated.path)
                            if (javaFile.exists() && javaFile.canRead()) {
                                ensureActive()
                                val dHash = withContext(Dispatchers.IO) { storageScanner.computeDHash(javaFile) }
                                if (dHash.isNotBlank()) {
                                    updated = updated.copy(visualSimilarityHash = dHash)
                                }
                            }
                        }
                        // Step 7: Incremental Perceptual Keyframe dHash calculation for videos
                        if (updated.category == FileCategory.VIDEO.name && updated.visualSimilarityHash.isBlank()) {
                            val javaFile = File(updated.path)
                            if (javaFile.exists() && javaFile.canRead()) {
                                ensureActive()
                                val vHash = withContext(Dispatchers.IO) { storageScanner.computeVideoDHash(javaFile) }
                                if (vHash.isNotBlank()) {
                                    updated = updated.copy(visualSimilarityHash = vHash)
                                }
                            }
                        }
                        // Phase 7 Step 1: Incremental Document Fingerprinting for PDFs/Documents
                        if (updated.category == FileCategory.DOCUMENTS.name && updated.visualSimilarityHash.isBlank()) {
                            val javaFile = File(updated.path)
                            if (javaFile.exists() && javaFile.canRead()) {
                                ensureActive()
                                val docFp = withContext(Dispatchers.IO) { storageScanner.computeDocumentFingerprint(javaFile) }
                                if (docFp.isNotBlank()) {
                                    updated = updated.copy(visualSimilarityHash = docFp)
                                }
                            }
                        }
                        // Step 6: Incremental On-Device AI Semantic Embedding inference
                        if (!updated.semanticIndexed) {
                            val javaFile = File(updated.path)
                            val textContent = "${updated.name} ${updated.ocrText} ${updated.tags}".trim()
                            val embedding = if (javaFile.exists() && javaFile.canRead()) {
                                tfliteProvider.generateImageEmbedding(javaFile) ?: tfliteProvider.generateTextEmbedding(textContent)
                            } else {
                                tfliteProvider.generateTextEmbedding(textContent)
                            }

                            if (embedding != null) {
                                val embStr = tfliteProvider.floatArrayToString(embedding)
                                updated = updated.copy(
                                    semanticEmbeddingVersion = tfliteProvider.embeddingVersion,
                                    semanticIndexed = true,
                                    semanticEmbeddingString = embStr
                                )
                            } else {
                                updated = updated.copy(semanticIndexed = true)
                            }
                        }
                        if (updated != file) {
                            updatedChunk.add(updated)
                        }
                        processedCount++
                    }

                    if (updatedChunk.isNotEmpty()) {
                        dao.updateFiles(updatedChunk)
                    }

                    _scanProgress.value = processedCount.toFloat() / totalCount.toFloat()
                }
            } catch (e: Exception) {
                // Handle coroutine cancellation cleanly
            } finally {
                _scanProgress.value = 1.0f
                _isScanning.value = false
            }
        }
    }

    fun getVisualDuplicates(similarityThresholdFlow: Flow<Float>): Flow<List<DuplicateGroup>> {
        return duplicateDetectionEngine.getVisualDuplicates(dao.getAllActiveFiles(), similarityThresholdFlow)
    }

    fun getVideoDuplicates(similarityThresholdFlow: Flow<Float>): Flow<List<DuplicateGroup>> {
        return duplicateDetectionEngine.getVideoDuplicates(dao.getAllActiveFiles(), similarityThresholdFlow)
    }

    fun getDocumentDuplicates(): Flow<List<DuplicateGroup>> {
        return duplicateDetectionEngine.getDocumentDuplicates(dao.getAllActiveFiles())
    }

    val documentStats: Flow<Triple<Int, Int, Float>> = dao.getAllActiveFiles().map { files ->
        val docs = files.filter { it.category == FileCategory.DOCUMENTS.name && !it.isVault && !it.isRecycleBin }
        val total = docs.size
        val indexed = docs.count { it.visualSimilarityHash.isNotBlank() || it.md5Hash.isNotBlank() }
        val pending = total - indexed
        val progress = if (total > 0) indexed.toFloat() / total.toFloat() else 1.0f
        Triple(indexed, pending, progress)
    }.flowOn(Dispatchers.Default)

    fun getSemanticDuplicates(similarityThresholdFlow: Flow<Float>): Flow<List<DuplicateGroup>> {
        return duplicateDetectionEngine.getSemanticDuplicates(dao.getAllActiveFiles(), similarityThresholdFlow)
    }

    init {
        CoroutineScope(Dispatchers.IO).launch {
            seedInitialDataIfNeeded()
        }
    }

    private suspend fun seedInitialDataIfNeeded() {
        val existing = dao.getAllActiveFiles().first()
        if (existing.isEmpty()) {
            val sampleFiles = listOf(
                FileItemEntity(
                    name = "Tax_Invoice_2026.pdf",
                    path = "/storage/emulated/0/Documents/Tax_Invoice_2026.pdf",
                    category = FileCategory.DOCUMENTS.name,
                    sizeBytes = 2_450_000L,
                    md5Hash = "a3f5c9e12084b1198402c842e",
                    ocrText = "Tax Invoice GSTIN 27AAACV1234F1Z1 Amount 14,250 INR Date 15 March 2026",
                    tags = "Tax, Invoice, Finance",
                    visualSimilarityHash = "sim_doc_tax_90"
                ),
                FileItemEntity(
                    name = "Tax_Invoice_2026_copy.pdf",
                    path = "/storage/emulated/0/Downloads/Tax_Invoice_2026_copy.pdf",
                    category = FileCategory.DOCUMENTS.name,
                    sizeBytes = 2_450_000L,
                    md5Hash = "a3f5c9e12084b1198402c842e",
                    ocrText = "Tax Invoice GSTIN 27AAACV1234F1Z1 Amount 14,250 INR Date 15 March 2026",
                    tags = "Tax, Invoice, Backup",
                    visualSimilarityHash = "sim_doc_tax_90"
                ),
                FileItemEntity(
                    name = "Electricity_Bill_July.jpg",
                    path = "/storage/emulated/0/Pictures/Electricity_Bill_July.jpg",
                    category = FileCategory.IMAGES.name,
                    sizeBytes = 3_120_000L,
                    md5Hash = "b84e19f201014890c2a",
                    ocrText = "Electricity Department State Power Bill Consumer No 8840291 Due Date 22 July 2026 Total 3,420 INR",
                    tags = "Bill, Utility, Electricity",
                    visualSimilarityHash = "a1b2c3d4e5f60718"
                ),
                FileItemEntity(
                    name = "Electricity_Bill_July_Scan.jpg",
                    path = "/storage/emulated/0/DCIM/Camera/Electricity_Bill_July_Scan.jpg",
                    category = FileCategory.IMAGES.name,
                    sizeBytes = 3_150_000L,
                    md5Hash = "c91a082348512f491c",
                    ocrText = "Electricity Department Power Bill Consumer 8840291 Due 22 July 2026 Total 3,420 INR",
                    tags = "Bill, Utility, Scan",
                    visualSimilarityHash = "a1b2c3d4e5f60719"
                ),
                FileItemEntity(
                    name = "Project_Blueprint_v2.png",
                    path = "/storage/emulated/0/Pictures/Project_Blueprint_v2.png",
                    category = FileCategory.IMAGES.name,
                    sizeBytes = 5_800_000L,
                    md5Hash = "d110948b83912a",
                    ocrText = "VVF Smart Manager Architecture Diagram Version 2.0 Module Specs Core vs Plugin",
                    tags = "Architecture, Blueprint, Design",
                    visualSimilarityHash = "f0e1d2c3b4a59876"
                )
            )
            dao.insertFiles(sampleFiles)
        }

        val existingPlugins = dao.getAllPlugins().first()
        if (existingPlugins.isEmpty()) {
            val samplePlugins = listOf(
                PluginEntity("ocr_engine", "ML Kit OCR Engine", "OCR", "Text extraction from images & scanned documents", isEnabled = true, isCore = true),
                PluginEntity("tflite_semantic", "TFLite Semantic Search", "AI_SEMANTIC", "On-device natural language vector search", isEnabled = true, isCore = false),
                PluginEntity("gdrive_sync", "Google Drive Core Sync", "CLOUD_PROVIDER", "REST API & Credential Manager Google Drive sync", isEnabled = true, isCore = true),
                PluginEntity("onedrive_sync", "Microsoft OneDrive Plugin", "CLOUD_PROVIDER", "OneDrive Cloud storage synchronization", isEnabled = true, isCore = false),
                PluginEntity("dropbox_sync", "Dropbox Cloud Plugin", "CLOUD_PROVIDER", "Dropbox Cloud storage integration", isEnabled = false, isCore = false),
                PluginEntity("nextcloud_sync", "NextCloud Private Sync", "CLOUD_PROVIDER", "Private self-hosted NextCloud instance sync", isEnabled = false, isCore = false),
                PluginEntity("s3_storage", "Amazon S3 Compatible Plugin", "CLOUD_PROVIDER", "S3 Bucket storage integration", isEnabled = false, isCore = false),
                PluginEntity("nas_smb", "Local NAS & SMB Plugin", "CLOUD_PROVIDER", "Local network storage & WebDAV connector", isEnabled = true, isCore = false)
            )
            dao.insertPlugins(samplePlugins)
        }

        val existingSync = dao.getCloudSyncItems().first()
        if (existingSync.isEmpty()) {
            val sampleSync = listOf(
                CloudSyncItemEntity(provider = "GOOGLE_DRIVE", fileName = "Tax_Invoice_2026.pdf", fileSize = 2_450_000L, status = "SYNCED", isCore = true),
                CloudSyncItemEntity(provider = "GOOGLE_DRIVE", fileName = "Electricity_Bill_July.jpg", fileSize = 3_120_000L, status = "SYNCED", isCore = true),
                CloudSyncItemEntity(provider = "ONEDRIVE", fileName = "Project_Blueprint_v2.png", fileSize = 5_800_000L, status = "PENDING", isCore = false),
                CloudSyncItemEntity(provider = "NAS_SMB", fileName = "Backup_Vault_Archive.zip", fileSize = 45_000_000L, status = "FAILED", isCore = false)
            )
            sampleSync.forEach { dao.insertCloudSyncItem(it) }
        }
    }

    /**
     * Reusable suspend function for retrying database/IO operations with Exponential Backoff
     */
    suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 100,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastException: Throwable? = null
        for (attempt in 1..maxAttempts) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                Log.w("SmartManagerRepository", "Operation failed on attempt $attempt of $maxAttempts: ${e.message}")
                if (attempt < maxAttempts) {
                    kotlinx.coroutines.delay(currentDelay)
                    currentDelay = (currentDelay * factor).toLong()
                }
            }
        }
        throw lastException ?: RuntimeException("Operation failed after $maxAttempts attempts")
    }

    open suspend fun insertFiles(files: List<FileItemEntity>) = withContext(Dispatchers.IO) {
        withRetry {
            dao.insertFiles(files)
        }
    }

    /**
     * Rescan physical device storage and sync with Room database
     */
    suspend fun rescanPhysicalStorage(): Int = withContext(Dispatchers.IO) {
        withRetry {
            var totalCount = 0
            storageScanner.scanDeviceStorageFlow(computeHashes = false).collect { batch ->
                if (batch.isNotEmpty()) {
                    dao.insertFiles(batch)
                    totalCount += batch.size
                }
            }
            startIncrementalDuplicateScan()
            totalCount
        }
    }

    suspend fun cleanSelectedDuplicates(selectedIds: Set<Long>) = withContext(Dispatchers.IO) {
        val duplicateManager = DuplicateManager(dao, context)
        duplicateManager.cleanSelectedDuplicates(selectedIds)
    }

    suspend fun moveToRecycleBin(file: FileItemEntity) = withContext(Dispatchers.IO) {
        val currentFile = dao.getFileById(file.id)
        if (currentFile == null || currentFile.isRecycleBin) {
            return@withContext
        }
        withRetry {
            val trashResult = PhysicalStorageManager.moveToTrash(context, currentFile.path)
            if (trashResult.isFailure) {
                throw trashResult.exceptionOrNull() ?: java.io.IOException("Failed to move file to trash")
            }
            val newPath = trashResult.getOrThrow()
            val originalPathToKeep = if (currentFile.originalPath.isNotBlank()) currentFile.originalPath else currentFile.path
            dao.updateFile(
                currentFile.copy(
                    path = newPath,
                    originalPath = originalPathToKeep,
                    isRecycleBin = true,
                    deletedTimestampMs = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun restoreFromRecycleBin(file: FileItemEntity) = withContext(Dispatchers.IO) {
        val currentFile = dao.getFileById(file.id)
        if (currentFile == null || !currentFile.isRecycleBin) {
            return@withContext
        }
        withRetry {
            val targetPath = if (currentFile.originalPath.isNotBlank()) currentFile.originalPath else currentFile.path
            val restoreResult = PhysicalStorageManager.restoreFromTrash(context, currentFile.path, targetPath)
            if (restoreResult.isFailure) {
                throw restoreResult.exceptionOrNull() ?: java.io.IOException("Failed to restore file from trash")
            }
            val restoredPath = restoreResult.getOrThrow()
            dao.updateFile(
                currentFile.copy(
                    path = restoredPath,
                    originalPath = "",
                    isRecycleBin = false,
                    deletedTimestampMs = 0L
                )
            )
        }
    }

    suspend fun deletePermanently(file: FileItemEntity) = withContext(Dispatchers.IO) {
        val currentFile = dao.getFileById(file.id)
        if (currentFile == null) {
            return@withContext
        }
        withRetry {
            val deleted = PhysicalStorageManager.deleteFile(context, currentFile.path)
            if (!deleted) {
                throw java.io.IOException("Failed to physically delete file at ${currentFile.path}")
            }
            dao.deleteFileById(currentFile.id)
        }
    }

    suspend fun emptyRecycleBin() = withContext(Dispatchers.IO) {
        withRetry {
            val trashFiles = dao.getRecycleBinFiles().first()
            var failedCount = 0
            trashFiles.forEach { file ->
                val deleted = PhysicalStorageManager.deleteFile(context, file.path)
                if (!deleted) {
                    failedCount++
                }
            }
            if (failedCount > 0) {
                throw java.io.IOException("Failed to physically delete $failedCount trash files")
            }
            dao.emptyRecycleBin()
        }
    }

    /**
     * Real Keystore AES-256-GCM Encryption for Secure Vault + Physical Source Wipe
     */
    suspend fun encryptToVault(file: FileItemEntity) = vaultRepository.encryptToVault(file)

    /**
     * Real Keystore AES-256-GCM Decryption for Vault Unlock
     */
    suspend fun unlockFromVault(vaultItem: VaultItemEntity, file: FileItemEntity?): Boolean = vaultRepository.unlockFromVault(vaultItem, file)

    fun getStoredVaultPinHash(): String {
        return vaultRepository.getStoredVaultPinHash()
    }

    open fun verifyVaultPin(inputPin: String, storedHash: String = ""): Boolean {
        return vaultRepository.verifyVaultPin(inputPin, storedHash)
    }

    open fun changeVaultPin(oldPin: String, newPin: String): Boolean {
        return vaultRepository.changeVaultPin(oldPin, newPin)
    }

    suspend fun getFilteredFilesPaged(category: String?, query: String, limit: Int, offset: Int): List<FileItemEntity> = withContext(Dispatchers.IO) {
        withRetry {
            fileRepository.getFilteredFilesPaged(category, query, limit, offset)
        }
    }

    suspend fun renameFile(file: FileItemEntity, newName: String) = withContext(Dispatchers.IO) {
        withRetry {
            fileRepository.renameFile(file, newName)
        }
    }

    suspend fun addTagToFile(file: FileItemEntity, tag: String) = withContext(Dispatchers.IO) {
        withRetry {
            fileRepository.addTagToFile(file, tag)
        }
    }

    suspend fun togglePlugin(pluginId: String, currentEnabled: Boolean) = withContext(Dispatchers.IO) {
        withRetry {
            pluginRepository.togglePlugin(pluginId, currentEnabled)
        }
    }

    suspend fun addSyncItem(provider: String, fileName: String, size: Long, filePath: String = "") = withContext(Dispatchers.IO) {
        withRetry {
            dao.insertCloudSyncItem(
                CloudSyncItemEntity(
                    provider = provider,
                    fileName = fileName,
                    filePath = filePath,
                    fileSize = size,
                    status = "QUEUED",
                    lastSyncedMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun trimMemory() {
        try {
            if (tfliteProvider is com.example.ai.TFLiteSemanticEmbeddingProvider) {
                (tfliteProvider as com.example.ai.TFLiteSemanticEmbeddingProvider).close()
                Log.i("SmartManagerRepository", "TFLite interpreter closed successfully via trimMemory")
            }
        } catch (e: Exception) {
            Log.e("SmartManagerRepository", "Failed to trim memory: ${e.message}")
        }
    }

    fun enqueueDuplicateCleanupWork() {
        try {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()
            val request = androidx.work.OneTimeWorkRequestBuilder<com.example.worker.DuplicateCleanupWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    10,
                    java.util.concurrent.TimeUnit.SECONDS
                )
                .build()
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "DuplicateCleanupWork",
                androidx.work.ExistingWorkPolicy.KEEP,
                request
            )
            Log.i("SmartManagerRepository", "One-time DuplicateCleanupWorker enqueued uniquely.")
        } catch (e: Exception) {
            Log.e("SmartManagerRepository", "Failed to enqueue DuplicateCleanupWorker: ${e.message}")
        }
    }

    fun enqueueCloudSyncWork() {
        try {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = androidx.work.OneTimeWorkRequestBuilder<com.example.worker.CloudSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    10,
                    java.util.concurrent.TimeUnit.SECONDS
                )
                .build()
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "CloudSyncWork",
                androidx.work.ExistingWorkPolicy.KEEP,
                request
            )
            Log.i("SmartManagerRepository", "One-time CloudSyncWorker enqueued uniquely.")
        } catch (e: Exception) {
            Log.e("SmartManagerRepository", "Failed to enqueue CloudSyncWorker: ${e.message}")
        }
    }

    fun enqueueCacheCleanupWork() {
        try {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()
            val request = androidx.work.OneTimeWorkRequestBuilder<com.example.worker.CacheCleanupWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    10,
                    java.util.concurrent.TimeUnit.SECONDS
                )
                .build()
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "CacheCleanupWork",
                androidx.work.ExistingWorkPolicy.KEEP,
                request
            )
            Log.i("SmartManagerRepository", "One-time CacheCleanupWorker enqueued uniquely.")
        } catch (e: Exception) {
            Log.e("SmartManagerRepository", "Failed to enqueue CacheCleanupWorker: ${e.message}")
        }
    }

    fun enqueueBackgroundIndexWork() {
        try {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()
            val request = androidx.work.OneTimeWorkRequestBuilder<com.example.worker.BackgroundIndexWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    10,
                    java.util.concurrent.TimeUnit.SECONDS
                )
                .build()
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "BackgroundIndexWork",
                androidx.work.ExistingWorkPolicy.KEEP,
                request
            )
            Log.i("SmartManagerRepository", "One-time BackgroundIndexWorker enqueued uniquely.")
        } catch (e: Exception) {
            Log.e("SmartManagerRepository", "Failed to enqueue BackgroundIndexWorker: ${e.message}")
        }
    }
}
