import os
import re

ui_dir = "app/src/main/java/com/example/ui/"

for root, _, files in os.walk(ui_dir):
    for f in files:
        if f.endswith(".kt"):
            file_path = os.path.join(root, f)
            with open(file_path, "r") as f_in:
                content = f_in.read()
            
            # Clean up messy package declarations
            # If the file is in ui/screens, its package should be com.example.ui.screens
            # If in ui/theme, com.example.ui.theme
            # If in ui, com.example.ui
            
            rel_path = os.path.relpath(file_path, "app/src/main/java/")
            expected_package = rel_path.replace("/", ".")[:-3] # remove .kt
            expected_package = ".".join(expected_package.split(".")[:-1])
            
            # Remove all package declarations and invalid imports
            content = re.sub(r'^package .*$', '', content, flags=re.MULTILINE)
            content = re.sub(r'^import com\.example\.R\.screens.*$', '', content, flags=re.MULTILINE)
            content = re.sub(r'^import androidx\.compose\.ui\.res\.stringResource\.screens.*$', '', content, flags=re.MULTILINE)
            content = re.sub(r'^import com\.example\.ui\.R.*$', '', content, flags=re.MULTILINE)
            content = re.sub(r'^import com\.example\.R\n', '', content, flags=re.MULTILINE)
            content = re.sub(r'^import androidx\.compose\.ui\.res\.stringResource\n', '', content, flags=re.MULTILINE)
            
            # some stray .screens might be left as a single line
            content = re.sub(r'^\.screens.*$', '', content, flags=re.MULTILINE)
            
            # Add back package and standard imports
            new_content = f"package {expected_package}\n\nimport com.example.R\nimport androidx.compose.ui.res.stringResource\n" + content
            
            # remove blank lines at start
            new_content = re.sub(r'\n{3,}', '\n\n', new_content)
            
            with open(file_path, "w") as f_out:
                f_out.write(new_content)

