package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
data class CategoryStat(val category: String, val count: Int, val totalSize: Long)

@Dao
interface FileDao {
    @Query("SELECT * FROM files WHERE id = :id LIMIT 1")
    suspend fun getFileById(id: Long): FileItemEntity?

    @Query("SELECT * FROM files WHERE name = :name LIMIT 1")
    suspend fun getFileByName(name: String): FileItemEntity?
    @Query("SELECT * FROM files WHERE isVault = 0 AND isRecycleBin = 0 AND ocrText != '' ORDER BY dateModifiedMs DESC LIMIT 100")
    fun getOcrScannedFiles(): Flow<List<FileItemEntity>>

    @Query("SELECT * FROM files WHERE isVault = 0 AND isRecycleBin = 0 AND (name LIKE '%' || :query || '%' OR ocrText LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%') ORDER BY dateModifiedMs DESC LIMIT 100")
    fun searchSemanticFiles(query: String): Flow<List<FileItemEntity>>
    @Query("SELECT * FROM files WHERE isVault = 0 AND isRecycleBin = 0 ORDER BY dateModifiedMs DESC")
    fun getAllActiveFiles(): Flow<List<FileItemEntity>>

    @Query("SELECT * FROM files WHERE isVault = 0 AND isRecycleBin = 0 ORDER BY dateModifiedMs DESC LIMIT 10")
    fun getRecentFiles(): Flow<List<FileItemEntity>>

    @Query("SELECT category, COUNT(*) as count, SUM(sizeBytes) as totalSize FROM files WHERE isVault = 0 AND isRecycleBin = 0 GROUP BY category")
    fun getCategoryStats(): Flow<List<CategoryStat>>

    @Query("""
        SELECT * FROM files 
        WHERE isVault = 0 AND isRecycleBin = 0 
          AND (:category IS NULL OR category = :category) 
          AND (:query = '' OR name LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' OR ocrText LIKE '%' || :query || '%') 
        ORDER BY dateModifiedMs DESC 
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getFilteredFilesPaged(category: String?, query: String, limit: Int, offset: Int): List<FileItemEntity>

    @Query("SELECT * FROM files WHERE category = :category AND isVault = 0 AND isRecycleBin = 0 ORDER BY dateModifiedMs DESC")
    fun getFilesByCategory(category: String): Flow<List<FileItemEntity>>

    @Query("SELECT * FROM files WHERE isRecycleBin = 1 ORDER BY deletedTimestampMs DESC")
    fun getRecycleBinFiles(): Flow<List<FileItemEntity>>

    @Query("SELECT * FROM files WHERE isVault = 1")
    fun getVaultFiles(): Flow<List<FileItemEntity>>

    @Query("SELECT * FROM files WHERE isVault = 0 AND isRecycleBin = 0 AND (name LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' OR ocrText LIKE '%' || :query || '%')")
    fun searchFiles(query: String): Flow<List<FileItemEntity>>

    @Query("SELECT * FROM files WHERE isVault = 0 AND isRecycleBin = 0 AND (md5Hash IS NULL OR md5Hash = '' OR ((category = 'IMAGES' OR category = 'VIDEO') AND (visualSimilarityHash IS NULL OR visualSimilarityHash = '')) OR semanticIndexed = 0)")
    suspend fun getUnhashedFiles(): List<FileItemEntity>

    @Update
    suspend fun updateFiles(files: List<FileItemEntity>)

    @Query("SELECT * FROM files WHERE md5Hash = :hash AND isRecycleBin = 1 LIMIT 1")
    suspend fun findInRecycleBinByHash(hash: String): FileItemEntity?

    @androidx.room.Transaction
    suspend fun moveFilesToRecycleBinAtomic(files: List<FileItemEntity>) {
        updateFiles(files)
    }

    @Query("SELECT * FROM files WHERE isVault = 0 AND isRecycleBin = 0 AND md5Hash IS NOT NULL AND md5Hash != '' AND md5Hash IN (SELECT md5Hash FROM files WHERE isVault = 0 AND isRecycleBin = 0 AND md5Hash IS NOT NULL AND md5Hash != '' GROUP BY md5Hash HAVING COUNT(*) > 1) ORDER BY md5Hash ASC, dateModifiedMs DESC")
    fun getDuplicateFilesByHash(): Flow<List<FileItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: FileItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<FileItemEntity>)

    @Update
    suspend fun updateFile(file: FileItemEntity)

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun deleteFileById(id: Long)

    @Query("DELETE FROM files WHERE isRecycleBin = 1")
    suspend fun emptyRecycleBin()

    @Query("SELECT * FROM files WHERE name = :name AND isVault = 1 LIMIT 1")
    suspend fun getVaultFileByName(name: String): FileItemEntity?

    // Vault DAO
    @Query("SELECT * FROM vault_items ORDER BY encryptedAtMs DESC")
    fun getAllVaultItems(): Flow<List<VaultItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultItem(item: VaultItemEntity): Long

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteVaultItemById(id: Long)

    // Cloud Sync DAO
    @Query("SELECT * FROM cloud_sync ORDER BY lastSyncedMs DESC")
    fun getCloudSyncItems(): Flow<List<CloudSyncItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCloudSyncItem(item: CloudSyncItemEntity): Long

    // Plugins DAO
    @Query("SELECT * FROM plugins ORDER BY isCore DESC, name ASC")
    fun getAllPlugins(): Flow<List<PluginEntity>>

    @Query("UPDATE plugins SET isEnabled = :enabled WHERE pluginId = :id")
    suspend fun setPluginEnabled(id: String, enabled: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlugins(plugins: List<PluginEntity>)
}
