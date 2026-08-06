import re
with open("app/src/main/java/com/example/ui/VVFSmartManagerApp.kt", "r") as f:
    text = f.read()

text = text.replace("val rawFiles by viewModel.allFiles.collectAsStateWithLifecycle()", "val rawFiles by viewModel.allFiles.collectAsStateWithLifecycle()\n    val categoryStats by viewModel.categoryStats.collectAsStateWithLifecycle()")

text = text.replace("0 -> DashboardScreen(\n                    viewModel = viewModel,\n                    files = rawFiles,", "0 -> DashboardScreen(\n                    viewModel = viewModel,\n                    categoryStats = categoryStats,")

with open("app/src/main/java/com/example/ui/VVFSmartManagerApp.kt", "w") as f:
    f.write(text)
