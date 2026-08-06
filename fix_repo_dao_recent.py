with open("app/src/main/java/com/example/data/FileDao.kt", "r") as f:
    text = f.read()

if "getRecentFiles" not in text:
    text = text.replace("fun getAllActiveFiles(): Flow<List<FileItemEntity>>", "fun getAllActiveFiles(): Flow<List<FileItemEntity>>\n\n    @Query(\"SELECT * FROM files WHERE isVault = 0 AND isRecycleBin = 0 ORDER BY dateModifiedMs DESC LIMIT 10\")\n    fun getRecentFiles(): Flow<List<FileItemEntity>>")
    with open("app/src/main/java/com/example/data/FileDao.kt", "w") as f:
        f.write(text)

with open("app/src/main/java/com/example/data/SmartManagerRepository.kt", "r") as f:
    text = f.read()

if "val recentFiles:" not in text:
    text = text.replace("val activeFiles: Flow<List<FileItemEntity>> = dao.getAllActiveFiles()", "val activeFiles: Flow<List<FileItemEntity>> = dao.getAllActiveFiles()\n    val recentFiles: Flow<List<FileItemEntity>> = dao.getRecentFiles()")
    with open("app/src/main/java/com/example/data/SmartManagerRepository.kt", "w") as f:
        f.write(text)
