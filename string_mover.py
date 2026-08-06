import os
import re

ui_dir = "app/src/main/java/com/example/ui/"
strings_xml_path = "app/src/main/res/values/strings.xml"

# Find all kt files
kt_files = []
for root, _, files in os.walk(ui_dir):
    for f in files:
        if f.endswith(".kt"):
            kt_files.append(os.path.join(root, f))

strings_to_add = {} # mapping string value to resource name
resource_counter = 1

def slugify(text):
    clean = re.sub(r'[^a-zA-Z0-9]', '_', text).strip('_').lower()
    clean = re.sub(r'_+', '_', clean)
    if not clean:
        return "str_misc"
    if len(clean) > 30:
        clean = clean[:30]
    return clean

# First pass: collect unique strings
for file_path in kt_files:
    with open(file_path, "r") as f:
        content = f.read()
    
    # find Text("...")
    matches_text = re.findall(r'Text\("([^"\$]+)"\)', content)
    # find contentDescription = "..."
    matches_desc = re.findall(r'contentDescription\s*=\s*"([^"\$]+)"', content)
    
    for m in matches_text + matches_desc:
        if m not in strings_to_add:
            slug = slugify(m)
            # ensure uniqueness
            base_slug = slug
            idx = 1
            while slug in strings_to_add.values():
                slug = f"{base_slug}_{idx}"
                idx += 1
            strings_to_add[m] = slug

# Update strings.xml
with open(strings_xml_path, "r") as f:
    xml_content = f.read()

new_strings_xml = ""
for val, name in strings_to_add.items():
    new_strings_xml += f'    <string name="{name}">{val}</string>\n'

xml_content = xml_content.replace('</resources>', f'{new_strings_xml}</resources>')
with open(strings_xml_path, "w") as f:
    f.write(xml_content)

# Update Kotlin files
for file_path in kt_files:
    with open(file_path, "r") as f:
        content = f.read()
        
    has_changes = False
    for val, name in strings_to_add.items():
        # Replace Text("val") with Text(stringResource(R.string.name))
        pattern_text = r'Text\("' + re.escape(val) + r'"\)'
        if re.search(pattern_text, content):
            content = re.sub(pattern_text, f'Text(stringResource(R.string.{name}))', content)
            has_changes = True
            
        # Replace contentDescription = "val" with contentDescription = stringResource(R.string.name)
        pattern_desc = r'contentDescription\s*=\s*"' + re.escape(val) + r'"'
        if re.search(pattern_desc, content):
            content = re.sub(pattern_desc, f'contentDescription = stringResource(R.string.{name})', content)
            has_changes = True
            
    if has_changes:
        if "import androidx.compose.ui.res.stringResource" not in content:
            # Need to insert import
            content = content.replace("import androidx.compose.material3.*", "import androidx.compose.material3.*\nimport androidx.compose.ui.res.stringResource\nimport com.example.R")
            if "import com.example.R" not in content:
                content = content.replace("package com.example.ui", "package com.example.ui\n\nimport com.example.R\nimport androidx.compose.ui.res.stringResource")
                content = content.replace("package com.example.ui.screens", "package com.example.ui.screens\n\nimport com.example.R\nimport androidx.compose.ui.res.stringResource")
        with open(file_path, "w") as f:
            f.write(content)

print(strings_to_add)
