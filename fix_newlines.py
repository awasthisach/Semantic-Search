import os
import re

for root, _, files in os.walk("app/src/main/java/com/example/ui"):
    for file in files:
        if file.endswith(".kt"):
            path = os.path.join(root, file)
            with open(path, "r") as f:
                content = f.read()
            
            # if imports are missing newlines, like import com.example.Rimport androidx...
            # we can use regex to fix it.
            # actually let's just do:
            content = content.replace("import ", "\nimport ")
            # but wait, package com.example.uiimport com.example.R
            content = content.replace("uiimport", "ui\nimport")
            
            # fix any double newlines
            content = re.sub(r'\n+', '\n', content)
            
            with open(path, "w") as f:
                f.write(content)
