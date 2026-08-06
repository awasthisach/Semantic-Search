import re

with open("app/src/main/java/com/example/storage/StorageScanner.kt", "r") as f:
    text = f.read()

# Add a closing brace after scanDeviceStorage
text = text.replace("        discoveredFiles.values.toList()\n    }\n\n    private suspend fun scanDirectoryRecursively(", "        return discoveredFiles.values.toList()\n    }\n\n    private suspend fun scanDirectoryRecursively(")
# If it's already "discoveredFiles.values.toList()\n    }", it will be replaced. Wait, let's just make sure.

with open("app/src/main/java/com/example/storage/StorageScanner.kt", "w") as f:
    f.write(text)
