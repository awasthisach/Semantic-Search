with open("app/src/main/java/com/example/data/FileDao.kt", "r") as f:
    text = f.read()

if "getOcrScannedFiles" not in text:
    text = text.replace("interface FileDao {", "interface FileDao {\n    @Query(\"SELECT * FROM files WHERE isVault = 0 AND isRecycleBin = 0 AND ocrText != '' ORDER BY dateModifiedMs DESC LIMIT 100\")\n    fun getOcrScannedFiles(): Flow<List<FileItemEntity>>\n\n    @Query(\"SELECT * FROM files WHERE isVault = 0 AND isRecycleBin = 0 AND (name LIKE '%' || :query || '%' OR ocrText LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%') ORDER BY dateModifiedMs DESC LIMIT 100\")\n    fun searchSemanticFiles(query: String): Flow<List<FileItemEntity>>")

with open("app/src/main/java/com/example/data/FileDao.kt", "w") as f:
    f.write(text)
