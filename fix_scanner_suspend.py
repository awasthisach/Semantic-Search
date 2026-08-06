import re
with open("app/src/main/java/com/example/storage/StorageScanner.kt", "r") as f:
    text = f.read()

text = text.replace("fun scanDeviceStorage(computeHashes: Boolean = false): List<FileItemEntity> {", "suspend fun scanDeviceStorage(computeHashes: Boolean = false): List<FileItemEntity> = withContext(Dispatchers.IO) {")
text = text.replace("return discoveredFiles.values.toList()", "discoveredFiles.values.toList()\n    }")
text = text.replace("private fun scanDirectoryRecursively(", "private suspend fun scanDirectoryRecursively(")
text = text.replace("private fun scanMediaStore(outMap: MutableMap<String, FileItemEntity>, computeHashes: Boolean) {", "private suspend fun scanMediaStore(outMap: MutableMap<String, FileItemEntity>, computeHashes: Boolean) {")

with open("app/src/main/java/com/example/storage/StorageScanner.kt", "w") as f:
    f.write(text)
