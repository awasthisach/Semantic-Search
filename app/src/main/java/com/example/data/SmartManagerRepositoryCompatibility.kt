package com.example.data

import kotlinx.coroutines.flow.Flow

/**
 * Compatibility facade for the current repository API.
 * Keeps existing ViewModel call sites stable while the repository exposes
 * strongly-typed Flow properties for these collections.
 */
fun SmartManagerRepository.getCategoryStats(): Flow<List<CategoryStat>> = categoryStats
fun SmartManagerRepository.getDuplicateGroups(): Flow<List<DuplicateGroup>> = exactDuplicates
fun SmartManagerRepository.getPlugins(): Flow<List<PluginEntity>> = plugins
fun SmartManagerRepository.getCloudSyncItems(): Flow<List<CloudSyncItemEntity>> = cloudSyncItems
fun SmartManagerRepository.getVaultItems(): Flow<List<VaultItemEntity>> = vaultItems
