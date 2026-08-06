import re
import os

def insert_strings_to_xml(filepath):
    with open(filepath, 'r') as f:
        content = f.read()
    
    new_strings = """
    <string name="recycle_bin_count">Recycle Bin (%1$d files)</string>
    <string name="deleted_files_auto_purge">Deleted Files (Auto-purge after 30 days)</string>
    <string name="recycle_bin_empty">Recycle Bin is empty.</string>
    <string name="active_storage_files">Active Storage Files (%1$d)</string>
    <string name="no_files_found_category">No files found in this category.</string>
    <string name="file_size_date">%1$s • %2$s</string>
    <string name="tags_label">Tags: %1$s</string>
    <string name="ocr_scanned">OCR Scanned</string>

    <string name="secure_encrypted_vault">Secure Encrypted Vault</string>
    <string name="enter_4_digit_master_pin">Enter 4-Digit Master PIN (Default: 1234)</string>
    <string name="encrypted_vault_unlocked">Encrypted Vault Unlocked</string>
    <string name="aes_256_active">AES-256 Android Keystore Cipher Active</string>
    <string name="vault_security_options">Vault Security Options</string>
    <string name="encrypted_files_count">Encrypted Files (%1$d)</string>
    <string name="encrypted_file_details">%1$s • %2$s</string>

    <string name="background_batch_indexing">Background batch indexing: %1$d%% completed</string>
    <string name="document_fingerprint_engine">Document Fingerprint Engine</string>
    <string name="indexed_percent">%1$d%% Indexed</string>
    <string name="indexed_count">Indexed: %1$d</string>
    <string name="pending_count">Pending: %1$d</string>
    <string name="visual_similarity_threshold">Visual Similarity Threshold</string>
    <string name="match_percent">%1$d%% Match</string>
    <string name="adjust_similarity_slider">Adjust similarity slider (70%% loose -&gt; 95%% near-exact visual match)</string>
    <string name="files_selected_deletion">%1$d files selected for deletion</string>
    <string name="level_1_2_and_3_4">Level 1-2 Exact Hash &amp; Level 3-4 Visual AI</string>
    <string name="level_1_2_exact_hash_duplicates">Level 1-2: Exact Hash Duplicates (%1$d sets)</string>
    <string name="no_exact_hash_duplicate">No exact hash duplicate files detected.</string>
    <string name="level_3_4_visual_semantic">Level 3-4: Visual &amp; Semantic AI Duplicates (%1$d sets)</string>
    <string name="no_visual_duplicates_matching">No visual duplicates matching %1$d%% threshold.</string>
    <string name="step_7_video_near_duplicates">Step 7: Video Near-Duplicates (%1$d sets)</string>
    <string name="no_video_keyframe_duplicates">No video keyframe near-duplicates detected.</string>
    <string name="step_6_ai_semantic_matches">Step 6: AI Semantic Vector Matches (%1$d sets)</string>
    <string name="no_ai_semantic_matches">No AI semantic vector matches detected.</string>
    <string name="phase_7_step_1_document_fingerprint">Phase 7 Step 1: Document Fingerprint Matches (%1$d sets)</string>
    <string name="no_document_fingerprint_duplicates">No document fingerprint duplicates detected.</string>
    <string name="score_percent">Score: %1$d%%</string>
    <string name="file_path_size">%1$s • %2$s</string>
    <string name="ml_kit_ocr_engine">ML Kit OCR Text Recognition Engine</string>
    <string name="ml_kit_ocr_desc">Extracts plain text from document scans, photos, invoices &amp; identity cards, indexing them for instant search.</string>
    <string name="ocr_indexed_documents">OCR Indexed Documents (%1$d)</string>
    <string name="extracted_text">Extracted Text: %1$s</string>
    <string name="tflite_ai_search">TFLite AI Semantic Search Engine</string>
    <string name="tflite_ai_search_desc">Natural Language Search across all file metadata, tags, and OCR text using on-device lightweight embeddings.</string>
    <string name="semantic_search_results">Semantic Search Results</string>
    <string name="score_96">Score 96%%</string>

    <string name="vvf_smart_manager_v2_0">VVF Smart Manager v2.0</string>
    <string name="system_health">System Health: 94%% Excellent</string>
    <string name="start_10s">Start &lt;10s</string>
    <string name="storage_used">Storage Used: %1$s</string>
    <string name="storage_free">18.5 GB Free of 128 GB</string>
    <string name="quick_actions">Quick Actions</string>
    <string name="master_roadmap_v2_0">Master Roadmap v2.0 Compliance</string>
    <string name="phases_1_19_audited">Phases 1-19 Architecture &amp; Stack Audited</string>
    <string name="golden_rule_audit_report">Golden Rule Audit Report</string>
    <string name="storage_categories">Storage Categories</string>
    <string name="recent_storage_files">Recent Storage Files</string>
    <string name="file_count">%1$d files</string>
    <string name="file_tags_bullet"> • %1$s</string>

    <string name="google_drive_core">Google Drive (Core Provider)</string>
    <string name="authorized">Authorized</string>
    <string name="google_drive_desc">REST API &amp; Credential Manager OAuth2 integration. Auto-sync queue with resume upload &amp; version history.</string>
    <string name="cloud_sync_queue">Cloud Sync Queue (%1$d)</string>
    <string name="provider_size">Provider: %1$s • %2$s</string>
    <string name="plugin_desc">Master Spec Section 4 Core/Plugin split. Download or toggle optional extensions on demand.</string>
    <string name="registered_extensions">Registered Extensions (%1$d)</string>
    <string name="core_label">CORE</string>
</resources>"""
    content = content.replace("</resources>", new_strings)
    with open(filepath, 'w') as f:
        f.write(content)


def replace_in_file(filepath, replacements):
    with open(filepath, 'r') as f:
        content = f.read()
    
    for old, new in replacements:
        content = content.replace(old, new)
        
    with open(filepath, 'w') as f:
        f.write(content)

# Define replacements per file
file_manager = "app/src/main/java/com/example/ui/screens/FileManagerScreen.kt"
file_manager_reps = [
    ('Text(text = "Recycle Bin (${recycleBinFiles.size} files)"', 'Text(text = stringResource(R.string.recycle_bin_count, recycleBinFiles.size)'),
    ('Text("Deleted Files (Auto-purge after 30 days)"', 'Text(stringResource(R.string.deleted_files_auto_purge)'),
    ('Text("Recycle Bin is empty."', 'Text(stringResource(R.string.recycle_bin_empty)'),
    ('Text(text = "Active Storage Files (${files.size})"', 'Text(text = stringResource(R.string.active_storage_files, files.size)'),
    ('Text("No files found in this category."', 'Text(stringResource(R.string.no_files_found_category)'),
    ('Text(text = "${formatFileSize(file.sizeBytes)} • ${formatDate(file.dateModifiedMs)}"', 'Text(text = stringResource(R.string.file_size_date, formatFileSize(file.sizeBytes), formatDate(file.dateModifiedMs))'),
    ('Text(text = "Tags: ${file.tags}"', 'Text(text = stringResource(R.string.tags_label, file.tags)'),
    ('Text(text = "OCR Scanned"', 'Text(text = stringResource(R.string.ocr_scanned)')
]

vault = "app/src/main/java/com/example/ui/screens/VaultScreen.kt"
vault_reps = [
    ('Text(text = "Secure Encrypted Vault"', 'Text(text = stringResource(R.string.secure_encrypted_vault)'),
    ('Text("Enter 4-Digit Master PIN (Default: 1234)"', 'Text(stringResource(R.string.enter_4_digit_master_pin)'),
    ('Text("Encrypted Vault Unlocked"', 'Text(stringResource(R.string.encrypted_vault_unlocked)'),
    ('Text("AES-256 Android Keystore Cipher Active"', 'Text(stringResource(R.string.aes_256_active)'),
    ('Text("Vault Security Options"', 'Text(stringResource(R.string.vault_security_options)'),
    ('Text(text = "Encrypted Files (${vaultItems.size})"', 'Text(text = stringResource(R.string.encrypted_files_count, vaultItems.size)'),
    ('Text(text = "${item.encryptedName} • ${formatFileSize(item.sizeBytes)}"', 'Text(text = stringResource(R.string.encrypted_file_details, item.encryptedName, formatFileSize(item.sizeBytes))')
]

ai_dupes = "app/src/main/java/com/example/ui/screens/AiDuplicatesScreen.kt"
ai_dupes_reps = [
    ('Text(text = "Background batch indexing: ${(scanProgress * 100).toInt()}% completed"', 'Text(text = stringResource(R.string.background_batch_indexing, (scanProgress * 100).toInt())'),
    ('Text(text = "Document Fingerprint Engine"', 'Text(text = stringResource(R.string.document_fingerprint_engine)'),
    ('Text(text = "${(documentStats.third * 100).toInt()}% Indexed"', 'Text(text = stringResource(R.string.indexed_percent, (documentStats.third * 100).toInt())'),
    ('Text(text = "Indexed: ${documentStats.first}"', 'Text(text = stringResource(R.string.indexed_count, documentStats.first)'),
    ('Text(text = "Pending: ${documentStats.second}"', 'Text(text = stringResource(R.string.pending_count, documentStats.second)'),
    ('Text(text = "Visual Similarity Threshold"', 'Text(text = stringResource(R.string.visual_similarity_threshold)'),
    ('Text(text = "${similarityThreshold.toInt()}% Match"', 'Text(text = stringResource(R.string.match_percent, similarityThreshold.toInt())'),
    ('Text("Adjust similarity slider (70% loose -> 95% near-exact visual match)"', 'Text(stringResource(R.string.adjust_similarity_slider)'),
    ('Text("${selectedDuplicateIds.size} files selected for deletion"', 'Text(stringResource(R.string.files_selected_deletion, selectedDuplicateIds.size)'),
    ('Text(text = "Level 1-2 Exact Hash & Level 3-4 Visual AI"', 'Text(text = stringResource(R.string.level_1_2_and_3_4)'),
    ('Text(text = "Level 1-2: Exact Hash Duplicates (${level1Duplicates.size} sets)"', 'Text(text = stringResource(R.string.level_1_2_exact_hash_duplicates, level1Duplicates.size)'),
    ('Text("No exact hash duplicate files detected."', 'Text(stringResource(R.string.no_exact_hash_duplicate)'),
    ('Text(text = "Level 3-4: Visual & Semantic AI Duplicates (${level3Duplicates.size} sets)"', 'Text(text = stringResource(R.string.level_3_4_visual_semantic, level3Duplicates.size)'),
    ('Text("No visual duplicates matching ${similarityThreshold.toInt()}% threshold."', 'Text(stringResource(R.string.no_visual_duplicates_matching, similarityThreshold.toInt())'),
    ('Text(text = "Step 7: Video Near-Duplicates (${videoDuplicates.size} sets)"', 'Text(text = stringResource(R.string.step_7_video_near_duplicates, videoDuplicates.size)'),
    ('Text("No video keyframe near-duplicates detected."', 'Text(stringResource(R.string.no_video_keyframe_duplicates)'),
    ('Text(text = "Step 6: AI Semantic Vector Matches (${semanticDuplicates.size} sets)"', 'Text(text = stringResource(R.string.step_6_ai_semantic_matches, semanticDuplicates.size)'),
    ('Text("No AI semantic vector matches detected."', 'Text(stringResource(R.string.no_ai_semantic_matches)'),
    ('Text(text = "Phase 7 Step 1: Document Fingerprint Matches (${documentDuplicates.size} sets)"', 'Text(text = stringResource(R.string.phase_7_step_1_document_fingerprint, documentDuplicates.size)'),
    ('Text("No document fingerprint duplicates detected."', 'Text(stringResource(R.string.no_document_fingerprint_duplicates)'),
    ('Text(text = "Score: ${group.similarityScore}%"', 'Text(text = stringResource(R.string.score_percent, group.similarityScore)'),
    ('Text(text = "${file.path} • ${formatFileSize(file.sizeBytes)}"', 'Text(text = stringResource(R.string.file_path_size, file.path, formatFileSize(file.sizeBytes))'),
    ('Text(text = "ML Kit OCR Text Recognition Engine"', 'Text(text = stringResource(R.string.ml_kit_ocr_engine)'),
    ('Text("Extracts plain text from document scans, photos, invoices & identity cards, indexing them for instant search."', 'Text(stringResource(R.string.ml_kit_ocr_desc)'),
    ('Text(text = "OCR Indexed Documents (${ocrScannedFiles.size})"', 'Text(text = stringResource(R.string.ocr_indexed_documents, ocrScannedFiles.size)'),
    ('Text(text = "Extracted Text: ${file.ocrText}"', 'Text(text = stringResource(R.string.extracted_text, file.ocrText)'),
    ('Text(text = "TFLite AI Semantic Search Engine"', 'Text(text = stringResource(R.string.tflite_ai_search)'),
    ('Text("Natural Language Search across all file metadata, tags, and OCR text using on-device lightweight embeddings."', 'Text(stringResource(R.string.tflite_ai_search_desc)'),
    ('Text(text = "Semantic Search Results"', 'Text(text = stringResource(R.string.semantic_search_results)'),
    ('Text(text = "Score 96%"', 'Text(text = stringResource(R.string.score_96)')
]

dashboard = "app/src/main/java/com/example/ui/screens/DashboardScreen.kt"
dashboard_reps = [
    ('Text(text = "VVF Smart Manager v2.0"', 'Text(text = stringResource(R.string.vvf_smart_manager_v2_0)'),
    ('Text(text = "System Health: 94% Excellent"', 'Text(text = stringResource(R.string.system_health)'),
    ('Text(text = "Start <10s"', 'Text(text = stringResource(R.string.start_10s)'),
    ('Text(text = "Storage Used: $formattedTotalSize"', 'Text(text = stringResource(R.string.storage_used, formattedTotalSize)'),
    ('Text(text = "18.5 GB Free of 128 GB"', 'Text(text = stringResource(R.string.storage_free)'),
    ('Text("Quick Actions"', 'Text(stringResource(R.string.quick_actions)'),
    ('Text(text = "Master Roadmap v2.0 Compliance"', 'Text(text = stringResource(R.string.master_roadmap_v2_0)'),
    ('Text("Phases 1-19 Architecture & Stack Audited"', 'Text(stringResource(R.string.phases_1_19_audited)'),
    ('Text("Golden Rule Audit Report"', 'Text(stringResource(R.string.golden_rule_audit_report)'),
    ('Text(text = "Storage Categories"', 'Text(text = stringResource(R.string.storage_categories)'),
    ('Text(text = "Recent Storage Files"', 'Text(text = stringResource(R.string.recent_storage_files)'),
    ('Text(text = "$count files"', 'Text(text = stringResource(R.string.file_count, count)'),
    ('Text(text = " • ${file.tags}"', 'Text(text = stringResource(R.string.file_tags_bullet, file.tags)')
]

cloud_plugins = "app/src/main/java/com/example/ui/screens/CloudPluginsScreen.kt"
cloud_plugins_reps = [
    ('Text(text = "Google Drive (Core Provider)"', 'Text(text = stringResource(R.string.google_drive_core)'),
    ('Text(text = "Authorized"', 'Text(text = stringResource(R.string.authorized)'),
    ('Text("REST API & Credential Manager OAuth2 integration. Auto-sync queue with resume upload & version history."', 'Text(stringResource(R.string.google_drive_desc)'),
    ('Text(text = "Cloud Sync Queue (${syncItems.size})"', 'Text(text = stringResource(R.string.cloud_sync_queue, syncItems.size)'),
    ('Text(text = "Provider: ${item.provider} • ${formatFileSize(item.fileSize)}"', 'Text(text = stringResource(R.string.provider_size, item.provider, formatFileSize(item.fileSize))'),
    ('Text("Master Spec Section 4 Core/Plugin split. Download or toggle optional extensions on demand."', 'Text(stringResource(R.string.plugin_desc)'),
    ('Text(text = "Registered Extensions (${plugins.size})"', 'Text(text = stringResource(R.string.registered_extensions, plugins.size)'),
    ('Text("CORE"', 'Text(stringResource(R.string.core_label)')
]

app_kt = "app/src/main/java/com/example/ui/VVFSmartManagerApp.kt"
app_kt_reps = [
    ('Text("VVF Smart Manager"', 'Text(stringResource(R.string.vvf_smart_manager_v2_0)')
]


insert_strings_to_xml("app/src/main/res/values/strings.xml")
replace_in_file(file_manager, file_manager_reps)
replace_in_file(vault, vault_reps)
replace_in_file(ai_dupes, ai_dupes_reps)
replace_in_file(dashboard, dashboard_reps)
replace_in_file(cloud_plugins, cloud_plugins_reps)
replace_in_file(app_kt, app_kt_reps)

