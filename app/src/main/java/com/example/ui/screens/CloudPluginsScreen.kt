package com.example.ui.screens
import com.example.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CloudSyncItemEntity
import com.example.data.PluginEntity
import com.example.ui.MainViewModel
import com.example.ui.theme.BhagwaOrange
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.SoftGold
@Composable
fun CloudPluginsScreen(
    viewModel: MainViewModel,
    cloudSyncItems: List<CloudSyncItemEntity>,
    plugins: List<PluginEntity>
) {
    var conflictResolutionMode by rememberSaveable { mutableStateOf("Keep Local") }
    var selectedSection by rememberSaveable { mutableStateOf(0) } // 0: Cloud Sync, 1: Plugin Manager
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionChip("Cloud Providers", 0, selectedSection, SoftGold) { selectedSection = 0 }
            SectionChip("Plugin Manager", 1, selectedSection, SkyCyan) { selectedSection = 1 }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (selectedSection == 0) {
            CloudSyncSection(
                viewModel = viewModel,
                syncItems = cloudSyncItems,
                conflictMode = conflictResolutionMode,
                onConflictModeChange = { conflictResolutionMode = it }
            )
        } else {
            PluginManagerSection(
                viewModel = viewModel,
                plugins = plugins
            )
        }
    }
}
@Composable
fun CloudSyncSection(
    viewModel: MainViewModel,
    syncItems: List<CloudSyncItemEntity>,
    conflictMode: String,
    onConflictModeChange: (String) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Google Drive Core Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = EmeraldGreen)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Google Drive (Core Provider)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = EmeraldGreen.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Authorized",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "REST API & Credential Manager OAuth2 integration. Auto-sync queue with resume upload & version history.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.syncCloudProvider("GOOGLE_DRIVE") },
                        colors = ButtonDefaults.buttonColors(containerColor = BhagwaOrange)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.trigger_drive_sync_now))
                    }
                }
            }
        }
        // Conflict Resolution Settings
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.sync_conflict_resolution), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(R.string.strategy_mode), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = {
                            onConflictModeChange(if (conflictMode == "Keep Local") "Keep Cloud" else "Keep Local")
                        }) {
                            Text(text = conflictMode, color = BhagwaOrange, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        // Multi-Cloud Sync History Queue
        item {
            Text(
                text = "Cloud Sync Queue (${syncItems.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
        items(syncItems, key = { it.id }) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = item.fileName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = "Provider: ${item.provider} • ${formatFileSize(item.fileSize)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (item.status) {
                            "SYNCED" -> EmeraldGreen.copy(alpha = 0.2f)
                            "PENDING", "QUEUED" -> SoftGold.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                        }
                    ) {
                        Text(
                            text = item.status,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (item.status) {
                                "SYNCED" -> EmeraldGreen
                                "PENDING", "QUEUED" -> SoftGold
                                else -> MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun PluginManagerSection(
    viewModel: MainViewModel,
    plugins: List<PluginEntity>
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Extension, contentDescription = null, tint = SkyCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.plugin_architecture_system), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Master Spec Section 4 Core/Plugin split. Download or toggle optional extensions on demand.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Text(
                text = "Registered Extensions (${plugins.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
        items(plugins, key = { it.pluginId }) { plugin ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = plugin.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (plugin.isCore) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = BhagwaOrange.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "CORE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BhagwaOrange,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = plugin.description,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = plugin.isEnabled,
                        onCheckedChange = { viewModel.togglePlugin(plugin.pluginId, plugin.isEnabled) },
                        colors = SwitchDefaults.colors(checkedThumbColor = BhagwaOrange),
                        modifier = Modifier.testTag("plugin_switch_${plugin.pluginId}")
                    )
                }
            }
        }
    }
}
