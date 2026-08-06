import os
import re

ui_dir = "app/src/main/java/com/example/ui/"

kt_files = []
for root, _, files in os.walk(ui_dir):
    for f in files:
        if f.endswith(".kt"):
            kt_files.append(os.path.join(root, f))

for file_path in kt_files:
    with open(file_path, "r") as f:
        content = f.read()
    
    # Fix the mangled import
    content = content.replace("import androidx.compose.ui.res.stringResource.screens", ".screens\n\nimport com.example.R\nimport androidx.compose.ui.res.stringResource")
    
    # Wait, the original was `package com.example.ui.screens`.
    # It matched `package com.example.ui` and became `package com.example.ui\n\nimport com.example.R\nimport androidx.compose.ui.res.stringResource.screens`
    content = content.replace("package com.example.ui\n\nimport com.example.R\nimport androidx.compose.ui.res.stringResource.screens", "package com.example.ui.screens\n\nimport com.example.R\nimport androidx.compose.ui.res.stringResource")
    
    # Also if it's package com.example.ui
    # Maybe we should just use regex to fix imports safely
    
    if "import androidx.compose.ui.res.stringResource" not in content:
        content = re.sub(r'package com\.example\.ui(\.screens)?', r'package com.example.ui\1\n\nimport com.example.R\nimport androidx.compose.ui.res.stringResource', content)
        
    with open(file_path, "w") as f:
        f.write(content)

