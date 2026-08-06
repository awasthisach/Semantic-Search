with open("app/src/main/java/com/example/data/FileDao.kt", "r") as f:
    text = f.read()

if "getFileById" not in text:
    text = text.replace("interface FileDao {", "interface FileDao {\n    @Query(\"SELECT * FROM files WHERE id = :id LIMIT 1\")\n    suspend fun getFileById(id: Long): FileItemEntity?\n\n    @Query(\"SELECT * FROM files WHERE name = :name LIMIT 1\")\n    suspend fun getFileByName(name: String): FileItemEntity?")

with open("app/src/main/java/com/example/data/FileDao.kt", "w") as f:
    f.write(text)

with open("app/src/main/java/com/example/data/SmartManagerRepository.kt", "r") as f:
    text = f.read()

if "getFileById" not in text:
    text = text.replace("fun searchSemanticFiles", "suspend fun getFileById(id: Long) = dao.getFileById(id)\n    suspend fun getFileByName(name: String) = dao.getFileByName(name)\n    fun searchSemanticFiles")

with open("app/src/main/java/com/example/data/SmartManagerRepository.kt", "w") as f:
    f.write(text)
