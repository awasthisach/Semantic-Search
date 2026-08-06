with open("app/src/main/java/com/example/ui/VVFSmartManagerApp.kt", "r") as f:
    text = f.read()

text = text.replace("val categoryStats by viewModel.categoryStats.collectAsStateWithLifecycle()", "val categoryStats by viewModel.categoryStats.collectAsStateWithLifecycle()\n    val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()")

text = text.replace("0 -> DashboardScreen(\n                    viewModel = viewModel,\n                    categoryStats = categoryStats,", "0 -> DashboardScreen(\n                    viewModel = viewModel,\n                    categoryStats = categoryStats,\n                    recentFiles = recentFiles,")

with open("app/src/main/java/com/example/ui/VVFSmartManagerApp.kt", "w") as f:
    f.write(text)
