import re
import os

strings = {
    "search_hint": "Search by filename, tag, or OCR content...",
    "search_desc": "Search",
    "clear_desc": "Clear",
    "all_files": "All Files",
    "recycle_bin": "Recycle Bin",
    "empty_trash": "Empty Trash",
    "restore_desc": "Restore",
    "delete_forever_desc": "Delete Forever",
    "no_files_desc": "No Files",
    "rename_file": "Rename File",
    "file_name": "File Name",
    "rename": "Rename",
    "cancel": "Cancel",
    "menu_desc": "Menu",
    "encrypt_to_vault": "Encrypt to Vault",
    "add_tag": "Add Tag",
    "move_to_trash": "Move to Trash",
    "tag_name_hint": "Tag Name (e.g. Urgent, Personal)",
    "add": "Add",
    "change_master_pin": "Change Master PIN",
    "current_pin": "Current PIN",
    "new_4_digit_pin": "New 4-Digit PIN",
    "confirm_new_pin": "Confirm New PIN",
    "change": "Change",
    "vault_locked_desc": "Vault Locked",
    "biometric_desc": "Biometric",
    "delete_desc": "Delete",
    "unlocked_desc": "Unlocked",
    "lock_vault": "Lock Vault",
    "vault_empty_desc": "Vault Empty",
    "encrypted_desc": "Encrypted",
    "decrypt": "Decrypt",
    "scanner_desc": "Scanner",
    "pdf_engine_desc": "PDF Engine",
    "threshold_desc": "Threshold",
    "threshold_loose": "70% (Loose)",
    "threshold_exact": "95% (Exact)",
    "auto_select": "Auto-Select",
    "clean": "Clean",
    "ocr_desc": "OCR",
    "ai_desc": "AI",
    "ocr_hint": "e.g., 'electricity bill', 'tax invoice', 'blueprint'",
    "vvf_verified_desc": "VVF Verified",
    "speed_desc": "Speed",
    "audit_desc": "Audit",
    "view_all": "View All",
    "add_custom_tag": "Add Custom Tag",
    "tag_name_dashboard_hint": "Tag Name (e.g. Tax, Work)",
    "trigger_drive_sync": "Trigger Drive Sync Now",
    "vvf_logo_desc": "VVF Logo",
    "home": "Home",
    "files": "Files",
    "vault": "Vault",
    "ai_dupes": "AI Dupes",
    "cloud": "Cloud"
}

# Add to strings.xml
strings_xml_path = "app/src/main/res/values/strings.xml"
try:
    with open(strings_xml_path, "r") as f:
        xml_content = f.read()
except FileNotFoundError:
    xml_content = "<resources>\n    <string name=\"app_name\">My Application</string>\n</resources>"

# append new strings before </resources>
new_strings = ""
for key, value in strings.items():
    if f'name="{key}"' not in xml_content:
        new_strings += f'    <string name="{key}">{value}</string>\n'

if new_strings:
    xml_content = xml_content.replace("</resources>", f"{new_strings}</resources>")
    with open(strings_xml_path, "w") as f:
        f.write(xml_content)


files_to_update = [
    "app/src/main/java/com/example/ui/screens/FileManagerScreen.kt",
    "app/src/main/java/com/example/ui/screens/VaultScreen.kt",
    "app/src/main/java/com/example/ui/screens/AiDuplicatesScreen.kt",
    "app/src/main/java/com/example/ui/screens/DashboardScreen.kt",
    "app/src/main/java/com/example/ui/screens/CloudPluginsScreen.kt",
    "app/src/main/java/com/example/ui/VVFSmartManagerApp.kt"
]

import_string_resource = "import androidx.compose.ui.res.stringResource\nimport com.example.R"

for filepath in files_to_update:
    if not os.path.exists(filepath):
        continue
    with open(filepath, "r") as f:
        content = f.read()
    
    if "import androidx.compose.ui.res.stringResource" not in content:
        content = content.replace("import androidx.compose.runtime.*", f"import androidx.compose.runtime.*\n{import_string_resource}")

    for key, value in strings.items():
        # Text("value") -> Text(stringResource(R.string.key))
        # Text("value", ...) -> Text(stringResource(R.string.key), ...)
        content = content.replace(f'Text("{value}"', f'Text(stringResource(R.string.{key})')
        content = content.replace(f'contentDescription = "{value}"', f'contentDescription = stringResource(R.string.{key})')
    
    # Dashboard specific: Text("Add Tag to ${file.name}")
    if "Add Tag to ${file.name}" in content:
        if 'name="add_tag_to_file"' not in xml_content:
            xml_content = xml_content.replace("</resources>", f'    <string name="add_tag_to_file">Add Tag to %1$s</string>\n</resources>')
            with open(strings_xml_path, "w") as f:
                f.write(xml_content)
        content = content.replace('Text("Add Tag to ${file.name}")', 'Text(stringResource(R.string.add_tag_to_file, file.name))')

    with open(filepath, "w") as f:
        f.write(content)

