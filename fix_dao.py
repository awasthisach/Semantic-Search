with open("app/src/main/java/com/example/data/FileDao.kt", "r") as f:
    text = f.read()

if "data class CategoryStat" not in text:
    text = text.replace("interface FileDao {", "data class CategoryStat(val category: String, val count: Int, val totalSize: Long)\n\n@Dao\ninterface FileDao {")

if "getCategoryStats" not in text:
    text = text.replace("@Query(\"SELECT * FROM files WHERE isVault = 0 AND isRecycleBin = 0 ORDER BY dateModifiedMs DESC\")\n    fun getAllActiveFiles(): Flow<List<FileItemEntity>>", "@Query(\"SELECT * FROM files WHERE isVault = 0 AND isRecycleBin = 0 ORDER BY dateModifiedMs DESC\")\n    fun getAllActiveFiles(): Flow<List<FileItemEntity>>\n\n    @Query(\"SELECT category, COUNT(*) as count, SUM(sizeBytes) as totalSize FROM files WHERE isVault = 0 AND isRecycleBin = 0 GROUP BY category\")\n    fun getCategoryStats(): Flow<List<CategoryStat>>")

with open("app/src/main/java/com/example/data/FileDao.kt", "w") as f:
    f.write(text)
