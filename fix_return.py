with open("app/src/main/java/com/example/storage/StorageScanner.kt", "r") as f:
    text = f.read()

text = text.replace("        return discoveredFiles.values.toList()\n    }\n\n    private suspend fun scanDirectoryRecursively(", "        discoveredFiles.values.toList()\n    }\n\n    private suspend fun scanDirectoryRecursively(")

with open("app/src/main/java/com/example/storage/StorageScanner.kt", "w") as f:
    f.write(text)
