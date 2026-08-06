import re
with open("app/src/main/java/com/example/storage/StorageScanner.kt", "r") as f:
    text = f.read()

text = text.replace("import java.security.MessageDigest", "import java.security.MessageDigest\nimport kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.withContext\nimport kotlinx.coroutines.ensureActive")

text = text.replace("fun computeFileHash(file: File): String {", "suspend fun computeFileHash(file: File): String = withContext(Dispatchers.IO) {")
text = text.replace("if (!file.exists() || !file.canRead()) return \"\"", "if (!file.exists() || !file.canRead()) return@withContext \"\"")
text = text.replace("return digest.digest().joinToString(\"\") { \"%02x\".format(it) }", "digest.digest().joinToString(\"\") { \"%02x\".format(it) }\n    }")
text = text.replace("digest.update(buffer, 0, bytesRead)\n            }", "ensureActive()\n                digest.update(buffer, 0, bytesRead)\n            }")

text = text.replace("fun computeDHash(file: File): String {", "suspend fun computeDHash(file: File): String = withContext(Dispatchers.IO) {")
text = text.replace("if (!file.exists() || !file.canRead() || !isImageFile(file.name)) return \"\"", "if (!file.exists() || !file.canRead() || !isImageFile(file.name)) return@withContext \"\"")
text = text.replace("return try {", "try {")
text = text.replace("val bitmap = decodeSampledBitmapFromFile(file, 64, 64) ?: return \"\"", "val bitmap = decodeSampledBitmapFromFile(file, 64, 64) ?: return@withContext \"\"")
text = text.replace("hash\n        } catch (e: Exception) {", "hash\n        } catch (e: Exception) {") # no return needed if last expr
text = text.replace("\"\"\n        }", "\"\"\n        }\n    }")
text = text.replace("val hash = computeDHashFromBitmap(bitmap)", "ensureActive()\n            val hash = computeDHashFromBitmap(bitmap)")

text = text.replace("fun computeVideoDHash(file: File, timeUs: Long = 1_000_000L): String {", "suspend fun computeVideoDHash(file: File, timeUs: Long = 1_000_000L): String = withContext(Dispatchers.IO) {")
text = text.replace("if (!file.exists() || !file.canRead() || !isVideoFile(file.name)) return \"\"", "if (!file.exists() || !file.canRead() || !isVideoFile(file.name)) return@withContext \"\"")
text = text.replace("val retriever = MediaMetadataRetriever()", "ensureActive()\n        val retriever = MediaMetadataRetriever()")
text = text.replace("return try {\n            retriever.setDataSource(file.absolutePath)", "try {\n            retriever.setDataSource(file.absolutePath)")
text = text.replace("} catch (ignored: Exception) {}\n        }", "} catch (ignored: Exception) {}\n        }\n    }")

text = text.replace("fun computeDocumentFingerprint(file: File): String {", "suspend fun computeDocumentFingerprint(file: File): String = withContext(Dispatchers.IO) {")
text = text.replace("if (!file.exists() || !file.canRead() || !isDocumentFile(file.name)) return \"\"", "if (!file.exists() || !file.canRead() || !isDocumentFile(file.name)) return@withContext \"\"")
text = text.replace("return try {\n            val length = file.length()", "try {\n            ensureActive()\n            val length = file.length()")
text = text.replace("if (length == 0L) return \"\"", "if (length == 0L) return@withContext \"\"")
text = text.replace("digest.digest().joinToString(\"\") { \"%02x\".format(it) }.take(16)\n        } catch (e: Exception) {", "ensureActive()\n            digest.digest().joinToString(\"\") { \"%02x\".format(it) }.take(16)\n        } catch (e: Exception) {")
text = text.replace("Log.w(TAG, \"Document fingerprint calculation failed for ${file.name}: ${e.message}\")\n            \"\"\n        }", "Log.w(TAG, \"Document fingerprint calculation failed for ${file.name}: ${e.message}\")\n            \"\"\n        }\n    }")

text = text.replace("private fun computeFileHashQuietly(file: File): String {", "private suspend fun computeFileHashQuietly(file: File): String {")
text = text.replace("fun computeDHashQuietly(file: File): String {", "suspend fun computeDHashQuietly(file: File): String {")

with open("app/src/main/java/com/example/storage/StorageScanner.kt", "w") as f:
    f.write(text)
