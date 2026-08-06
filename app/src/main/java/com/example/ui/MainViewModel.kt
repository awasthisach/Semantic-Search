package com.example.ui
import com.example.R
import androidx.compose.ui.res.stringResource
// MainViewModel - Phase 6 Step 7 Complete (Video Duplicate Engine Integration)
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CloudSyncItemEntity
import com.example.data.CategoryStat
import com.example.data.DuplicateGroup
import com.example.data.FileCategory
import com.example.data.FileItemEntity
import com.example.data.PluginEntity
import com.example.data.SmartManagerRepository
import com.example.data.VaultItemEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
typealias DuplicateGroup = com.example.data.DuplicateGroup
@kotlinx.coroutines.ExperimentalCoroutinesApi
class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository = (application as com.example.VVFApplication).repository
    private val _globalError = MutableStateFlow<String?>(null)
    val globalError: StateFlow<String?> = _globalError.asStateFlow()
    fun clearGlobalError() {
        _globalError.value = null
    }
    val coroutineExceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("MainViewModel", "Unhandled coroutine exception in background task", throwable)
        when (throwable) {
            is OutOfMemoryError -> {
                System.gc()
                _globalError.value = "File too large or system memory exhausted."
            }
            is java.io.IOException -> {
                _globalError.value = "File read or write error occurred."
            }
            else -> {
                _globalError.value = throwable.localizedMessage ?: "A background operation failed. Please try again."
            }
        }
    }
    // Current Navigation Tab Index (0: Dashboard, 1: File Manager, 2: Secure Vault, 3: AI & Duplicates, 4: Cloud & Plugins)
    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex.asStateFlow()
    fun selectTab(index: Int) {
        _selectedTabIndex.value = index
    }
    // Selected File Category Filter
    private val _selectedCategory = MutableStateFlow<FileCategory?>(null)
    val selectedCategory: StateFlow<FileCategory?> = _selectedCategory.asStateFlow()
    fun selectCategory(category: FileCategory?) {
        _selectedCategory.value = category
    }
    // Fast Search Query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
    // Natural Language / Semantic AI Search Query
    private val _semanticQuery = MutableStateFlow("")
    val semanticQuery: StateFlow<String> = _semanticQuery.asStateFlow()
    fun setSemanticQuery(query: String) {
        _semanticQuery.value = query
    }
    // Similarity Threshold Slider (70% - 95%)
    private val _similarityThreshold = MutableStateFlow(80.0f)
    val similarityThreshold: StateFlow<Float> = _similarityThreshold.asStateFlow()
    fun setSimilarityThreshold(value: Float) {
        _similarityThreshold.value = value
    }
    // Secure Vault Lock & PIN State
    private val _userPin = MutableStateFlow("1234") // Default master pin for demo
    val userPin: StateFlow<String> = _userPin.asStateFlow()
    private val _enteredPin = MutableStateFlow("")
    val enteredPin: StateFlow<String> = _enteredPin.asStateFlow()
    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()
    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()
    fun appendPinDigit(digit: String) {
        if (_enteredPin.value.length < 4) {
            _enteredPin.value += digit
            if (_enteredPin.value.length == 4) {
                verifyPin()
            }
        }
    }
    fun clearPinDigit() {
        if (_enteredPin.value.isNotEmpty()) {
            _enteredPin.value = _enteredPin.value.dropLast(1)
            _pinError.value = null
        }
    }
    private fun verifyPin() {
        if (repository.verifyVaultPin(_enteredPin.value)) {
            _isVaultUnlocked.value = true
            _pinError.value = null
        } else {
            _pinError.value = "Incorrect PIN. Try again."
            _enteredPin.value = ""
        }
    }
    fun authenticateBiometric() {
        _isVaultUnlocked.value = true
        _pinError.value = null
        _enteredPin.value = ""
    }
    fun changeVaultPin(oldPin: String, newPin: String): Boolean {
        val success = repository.changeVaultPin(oldPin, newPin)
        if (success) {
            _pinError.value = null
        } else {
            _pinError.value = "Failed to update PIN. Check current PIN."
        }
        return success
    }
    // Duplicate Scan Progress Flow (%) and Scanning status
    val duplicateScanProgress: StateFlow<Float> = repository.scanProgress
    val isDuplicateScanning: StateFlow<Boolean> = repository.isScanning
    fun startDuplicateScan() {
        repository.startIncrementalDuplicateScan()
    }
    fun rescanPhysicalStorage() {
        viewModelScope.launch(coroutineExceptionHandler) {
            repository.rescanPhysicalStorage()
        }
    }
    fun lockVault() {
        _isVaultUnlocked.value = false
        _enteredPin.value = ""
    }
    // Active Files State
    
    val recentFiles: StateFlow<List<FileItemEntity>> = repository.recentFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categoryStats: StateFlow<List<CategoryStat>> = repository.categoryStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val ocrScannedFiles: StateFlow<List<FileItemEntity>> = repository.ocrScannedFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val semanticSearchResults: StateFlow<List<FileItemEntity>> = _semanticQuery
        .flatMapLatest { query -> repository.searchSemanticFiles(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recycleBinFiles: StateFlow<List<FileItemEntity>> = repository.recycleBinFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val vaultItems: StateFlow<List<VaultItemEntity>> = repository.vaultItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val cloudSyncItems: StateFlow<List<CloudSyncItemEntity>> = repository.cloudSyncItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val plugins: StateFlow<List<PluginEntity>> = repository.plugins
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _filteredFiles = MutableStateFlow<List<FileItemEntity>>(emptyList())
    val filteredFiles: StateFlow<List<FileItemEntity>> = _filteredFiles.asStateFlow()
    private val _isPageLoading = MutableStateFlow(false)
    val isPageLoading: StateFlow<Boolean> = _isPageLoading.asStateFlow()
    private var currentOffset = 0
    private val PAGE_SIZE = 50
    private var isEndReached = false
    fun resetPagination() {
        currentOffset = 0
        isEndReached = false
        _filteredFiles.value = emptyList()
    }
    fun loadNextPage() {
        if (isEndReached || _isPageLoading.value) return
        _isPageLoading.value = true
        viewModelScope.launch(coroutineExceptionHandler) {
            val categoryStr = _selectedCategory.value?.name
            val queryStr = _searchQuery.value
            val nextBatch = repository.getFilteredFilesPaged(categoryStr, queryStr, PAGE_SIZE, currentOffset)
            if (nextBatch.size < PAGE_SIZE) {
                isEndReached = true
            }
            _filteredFiles.value = _filteredFiles.value + nextBatch
            currentOffset += nextBatch.size
            _isPageLoading.value = false
        }
    }
    init {
        viewModelScope.launch {
            combine(_selectedCategory, _searchQuery) { _, _ -> }.collect {
                resetPagination()
            }
        }
    }
    // Level 1-2 Exact Hash Duplicates (Real SHA-256 Hash Matching via SQL subquery in Repository)
    val level1ExactDuplicates: StateFlow<List<DuplicateGroup>> = repository.exactDuplicates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    // Level 3-4 Perceptual Hashing & Visual AI Duplicates engine (dHash + Hamming Distance)
    val level3VisualDuplicates: StateFlow<List<DuplicateGroup>> = repository.getVisualDuplicates(_similarityThreshold)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    // Step 7 Video Keyframe Duplicates engine (dHash + Hamming Distance)
    val videoDuplicates: StateFlow<List<DuplicateGroup>> = repository.getVideoDuplicates(_similarityThreshold)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    // Step 6 AI Semantic Embedding Vector Duplicates engine (Cosine Similarity)
    val semanticDuplicates: StateFlow<List<DuplicateGroup>> = repository.getSemanticDuplicates(_similarityThreshold)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    // Phase 7 Step 1 Document Duplicate Engine (Lightweight Fingerprinting)
    val documentDuplicates: StateFlow<List<DuplicateGroup>> = repository.getDocumentDuplicates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    // Phase 7 Step 1 Document Indexing Statistics (Indexed, Pending, Progress Ratio)
    val documentStats: StateFlow<Triple<Int, Int, Float>> = repository.documentStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Triple(0, 0, 1.0f))
    // Selected duplicate file IDs for cleaning
    private val _selectedDuplicateIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedDuplicateIds: StateFlow<Set<Long>> = _selectedDuplicateIds.asStateFlow()
    fun toggleDuplicateSelection(fileId: Long) {
        val current = _selectedDuplicateIds.value.toMutableSet()
        if (current.contains(fileId)) current.remove(fileId) else current.add(fileId)
        _selectedDuplicateIds.value = current
    }
    fun autoSelectExtraDuplicates() {
        val toSelect = mutableSetOf<Long>()
        level1ExactDuplicates.value.forEach { group ->
            // Keep first, select the rest
            group.files.drop(1).forEach { toSelect.add(it.id) }
        }
        level3VisualDuplicates.value.forEach { group ->
            group.files.drop(1).forEach { toSelect.add(it.id) }
        }
        videoDuplicates.value.forEach { group ->
            group.files.drop(1).forEach { toSelect.add(it.id) }
        }
        semanticDuplicates.value.forEach { group ->
            group.files.drop(1).forEach { toSelect.add(it.id) }
        }
        documentDuplicates.value.forEach { group ->
            group.files.drop(1).forEach { toSelect.add(it.id) }
        }
        _selectedDuplicateIds.value = toSelect
    }
    fun cleanSelectedDuplicates() {
        viewModelScope.launch(coroutineExceptionHandler) {
            val ids = _selectedDuplicateIds.value
            val toDelete = ids.mapNotNull { repository.getFileById(it) }
            toDelete.forEach { repository.moveToRecycleBin(it) }
            _selectedDuplicateIds.value = emptySet()
        }
    }
    // File Actions
    fun moveToRecycleBin(file: FileItemEntity) {
        viewModelScope.launch(coroutineExceptionHandler) { repository.moveToRecycleBin(file) }
    }
    fun restoreFromRecycleBin(file: FileItemEntity) {
        viewModelScope.launch(coroutineExceptionHandler) { repository.restoreFromRecycleBin(file) }
    }
    fun deletePermanently(file: FileItemEntity) {
        viewModelScope.launch(coroutineExceptionHandler) { repository.deletePermanently(file) }
    }
    fun emptyRecycleBin() {
        viewModelScope.launch(coroutineExceptionHandler) { repository.emptyRecycleBin() }
    }
    fun encryptToVault(file: FileItemEntity) {
        viewModelScope.launch(coroutineExceptionHandler) { repository.encryptToVault(file) }
    }
    fun unlockFromVault(vaultItem: VaultItemEntity) {
        viewModelScope.launch(coroutineExceptionHandler) {
            val originalFile = repository.getFileByName(vaultItem.originalName)
            repository.unlockFromVault(vaultItem, originalFile)
        }
    }
    fun renameFile(file: FileItemEntity, newName: String) {
        viewModelScope.launch(coroutineExceptionHandler) { repository.renameFile(file, newName) }
    }
    fun addTagToFile(file: FileItemEntity, tag: String) {
        viewModelScope.launch(coroutineExceptionHandler) { repository.addTagToFile(file, tag) }
    }
    fun togglePlugin(pluginId: String, currentEnabled: Boolean) {
        viewModelScope.launch(coroutineExceptionHandler) { repository.togglePlugin(pluginId, currentEnabled) }
    }
    fun syncCloudProvider(provider: String) {
        viewModelScope.launch(coroutineExceptionHandler) {
            repository.addSyncItem(provider, "Sync_Batch_${System.currentTimeMillis() / 1000}.zip", 12_500_000L)
            repository.enqueueCloudSyncWork()
        }
    }

    // WorkManager Task Triggers
    fun triggerDuplicateCleanupWorker() {
        repository.enqueueDuplicateCleanupWork()
    }

    fun triggerCloudSyncWorker() {
        repository.enqueueCloudSyncWork()
    }

    fun triggerCacheCleanupWorker() {
        repository.enqueueCacheCleanupWork()
    }

    // Process Files Selected from FilePickerUI
    fun processPickedLocalFiles(pickedFiles: List<com.example.ui.components.PickableLocalFile>) {
        viewModelScope.launch(coroutineExceptionHandler) {
            val entities = pickedFiles.map { picked ->
                FileItemEntity(
                    name = picked.name,
                    path = picked.path,
                    category = picked.category.name,
                    sizeBytes = picked.sizeBytes,
                    dateModifiedMs = picked.dateModifiedMs,
                    tags = "Imported"
                )
            }
            repository.insertFiles(entities)
            resetPagination()
            repository.enqueueBackgroundIndexWork()
        }
    }

    fun processPickedUris(uris: List<android.net.Uri>) {
        viewModelScope.launch(coroutineExceptionHandler) {
            val context = getApplication<Application>().applicationContext
            val entities = uris.mapIndexed { index, uri ->
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Picked_File_${System.currentTimeMillis()}_$index.bin"
                FileItemEntity(
                    name = fileName,
                    path = uri.toString(),
                    category = inferCategoryFromFilename(fileName),
                    sizeBytes = 1024L * (index + 1) * 256,
                    tags = "SAF_Import"
                )
            }
            repository.insertFiles(entities)
            resetPagination()
            repository.enqueueBackgroundIndexWork()
        }
    }

    private fun inferCategoryFromFilename(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "webp", "gif" -> FileCategory.IMAGES.name
            "pdf", "doc", "docx", "txt", "csv", "xlsx" -> FileCategory.DOCUMENTS.name
            "mp3", "wav", "m4a", "aac" -> FileCategory.AUDIO.name
            "mp4", "mkv", "webm", "mov" -> FileCategory.VIDEO.name
            else -> FileCategory.OTHER.name
        }
    }
}
