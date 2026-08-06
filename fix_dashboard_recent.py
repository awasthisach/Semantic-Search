with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "r") as f:
    text = f.read()

text = text.replace("fun DashboardScreen(\n    viewModel: MainViewModel,\n    categoryStats: List<CategoryStat>,", "fun DashboardScreen(\n    viewModel: MainViewModel,\n    categoryStats: List<CategoryStat>,\n    recentFiles: List<FileItemEntity>,")

text = text.replace("items(files.take(5), key = { it.id }) { file ->", "items(recentFiles.take(5), key = { it.id }) { file ->")

with open("app/src/main/java/com/example/ui/screens/DashboardScreen.kt", "w") as f:
    f.write(text)
