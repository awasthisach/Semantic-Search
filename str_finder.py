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

with open(strings_xml_path, "r") as f:
    xml_content = f.read()

# First pass: collect unique strings
for file_path in kt_files:
    with open(file_path, "r") as f:
        content = f.read()
    
    # find Text("...")
    matches_text = re.findall(r'Text\(\s*"([^"\$]+)"', content)
    matches_text2 = re.findall(r'Text\(text\s*=\s*"([^"\$]+)"', content)
    # find contentDescription = "..."
    matches_desc = re.findall(r'contentDescription\s*=\s*"([^"\$]+)"', content)
    
    for m in matches_text + matches_text2 + matches_desc:
        if f'>{m}</string>' in xml_content:
            continue
        if m not in strings_to_add:
            slug = slugify(m)
            # ensure uniqueness
            base_slug = slug
            idx = 1
            while slug in strings_to_add.values() or f'name="{slug}"' in xml_content:
                slug = f"{base_slug}_{idx}"
                idx += 1
            strings_to_add[m] = slug

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
    
    # We will iterate through all lines
    lines = content.split('\n')
    for i, line in enumerate(lines):
        for val, name in strings_to_add.items():
            if f'Text("{val}"' in line:
                lines[i] = line.replace(f'Text("{val}"', f'Text(stringResource(R.string.{name})')
                has_changes = True
            elif f'Text(text = "{val}"' in line:
                lines[i] = line.replace(f'Text(text = "{val}"', f'Text(text = stringResource(R.string.{name})')
                has_changes = True
            elif f'contentDescription = "{val}"' in line:
                lines[i] = line.replace(f'contentDescription = "{val}"', f'contentDescription = stringResource(R.string.{name})')
                has_changes = True

    if has_changes:
        content = '\n'.join(lines)
        with open(file_path, "w") as f:
            f.write(content)

print(strings_to_add)
