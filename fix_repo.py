with open("app/src/main/java/com/example/data/SmartManagerRepository.kt", "r") as f:
    text = f.read()

if "val categoryStats: Flow<List<CategoryStat>>" not in text:
    text = text.replace("val activeFiles: Flow<List<FileItemEntity>> = dao.getAllActiveFiles()", "val activeFiles: Flow<List<FileItemEntity>> = dao.getAllActiveFiles()\n    val categoryStats: Flow<List<CategoryStat>> = dao.getCategoryStats()")

with open("app/src/main/java/com/example/data/SmartManagerRepository.kt", "w") as f:
    f.write(text)
