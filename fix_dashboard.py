import re
with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "r") as f:
    text = f.read()

text = text.replace("import com.example.data.FileItemEntity", "import com.example.data.FileItemEntity\nimport com.example.data.CategoryStat")

text = text.replace("fun DashboardScreen(\n    viewModel: MainViewModel,\n    files: List<FileItemEntity>,", "fun DashboardScreen(\n    viewModel: MainViewModel,\n    categoryStats: List<CategoryStat>,")

text = text.replace("val totalSize = remember(files) { files.sumOf { it.sizeBytes } }", "val totalSize = remember(categoryStats) { categoryStats.sumOf { it.totalSize } }")

text = text.replace("""@Composable
fun CategoryRow(
    title: String,
    category: FileCategory,
    files: List<FileItemEntity>,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    val categoryFiles = remember(files, category) { files.filter { it.category == category.name } }
    val count = categoryFiles.size
    val size = remember(categoryFiles) { categoryFiles.sumOf { it.sizeBytes } }""", """@Composable
fun CategoryRow(
    title: String,
    category: FileCategory,
    categoryStats: List<CategoryStat>,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    val stat = categoryStats.find { it.category == category.name }
    val count = stat?.count ?: 0
    val size = stat?.totalSize ?: 0L""")

text = re.sub(r'CategoryRow\("([^"]+)", ([^,]+), files,', r'CategoryRow("\1", \2, categoryStats,', text)

with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w") as f:
    f.write(text)
