import os

ui_dir = "app/src/main/java/com/example/ui/"
strings_xml_path = "app/src/main/res/values/strings.xml"

# Update strings.xml
with open(strings_xml_path, "r") as f:
    xml_content = f.read()

strings_to_add = {
    "Start Scan": "start_scan",
    "Rescan": "rescan",
    "Change Master PIN": "change_master_pin_2",
}

new_strings_xml = ""
for val, name in strings_to_add.items():
    if f'>{val}</string>' not in xml_content:
        new_strings_xml += f'    <string name="{name}">{val}</string>\n'

if new_strings_xml:
    xml_content = xml_content.replace('</resources>', f'{new_strings_xml}</resources>')
    with open(strings_xml_path, "w") as f:
        f.write(xml_content)

# Replace in files
file1 = "app/src/main/java/com/example/ui/screens/AiDuplicatesScreen.kt"
with open(file1, "r") as f:
    c = f.read()
c = c.replace('Text(if (isScanning) "Rescan" else "Start Scan"', 'Text(if (isScanning) stringResource(R.string.rescan) else stringResource(R.string.start_scan)')
with open(file1, "w") as f:
    f.write(c)
    
file2 = "app/src/main/java/com/example/ui/screens/VaultScreen.kt"
with open(file2, "r") as f:
    c = f.read()
c = c.replace('Text(text = "Change Master PIN"', 'Text(text = stringResource(R.string.change_master_pin)')
with open(file2, "w") as f:
    f.write(c)

