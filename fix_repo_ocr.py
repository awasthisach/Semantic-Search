with open("app/src/main/java/com/example/data/SmartManagerRepository.kt", "r") as f:
    text = f.read()

if "val ocrScannedFiles: Flow<List<FileItemEntity>> = dao.getOcrScannedFiles()" not in text:
    text = text.replace("val categoryStats: Flow<List<CategoryStat>> = dao.getCategoryStats()", "val categoryStats: Flow<List<CategoryStat>> = dao.getCategoryStats()\n    val ocrScannedFiles: Flow<List<FileItemEntity>> = dao.getOcrScannedFiles()\n    fun searchSemanticFiles(query: String): Flow<List<FileItemEntity>> = dao.searchSemanticFiles(query)")

with open("app/src/main/java/com/example/data/SmartManagerRepository.kt", "w") as f:
    f.write(text)
